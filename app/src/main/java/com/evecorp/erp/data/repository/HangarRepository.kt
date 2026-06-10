package com.evecorp.erp.data.repository

import android.util.Log
import com.evecorp.erp.Constants
import com.evecorp.erp.data.local.dao.CorporationDivisionDao
import com.evecorp.erp.data.local.dao.HangarItemDao
import com.evecorp.erp.data.local.dao.TypeNameCacheDao
import com.evecorp.erp.data.local.entity.CorporationDivisionEntity
import com.evecorp.erp.data.local.entity.HangarItemEntity
import com.evecorp.erp.data.local.entity.TypeNameCacheEntity
import com.evecorp.erp.data.remote.api.EveEsiApi
import com.evecorp.erp.data.remote.dto.HangarItemDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "HangarRepo"

@Singleton
class HangarRepository @Inject constructor(
    private val corporationDivisionDao: CorporationDivisionDao,
    private val hangarItemDao: HangarItemDao,
    private val typeNameCacheDao: TypeNameCacheDao,
    private val esiApi: EveEsiApi
) {
    fun getAllDivisions(): Flow<List<CorporationDivisionEntity>> =
        corporationDivisionDao.getAll()

    fun getMainDivision(): Flow<CorporationDivisionEntity?> =
        corporationDivisionDao.getMain()

    fun getItemsByDivision(divisionId: Long): Flow<List<HangarItemEntity>> =
        hangarItemDao.getByDivision(divisionId)

    fun getMainHangarItems(): Flow<List<HangarItemEntity>> =
        hangarItemDao.getMainHangarItems()

    suspend fun setMainDivision(divisionId: Long) {
        corporationDivisionDao.clearMainFlag()
        corporationDivisionDao.setMain(divisionId)
    }

    suspend fun syncDivisions(corpId: Long): Result<Unit> {
        return try {
            val response = esiApi.getDivisions(corpId)
            if (response.isSuccessful) {
                val body = response.body()
                // ESI 返回 { "hangar": [...], "wallet": [...] }，只取 hangar
                val divisions = body?.hangar ?: emptyList()
                corporationDivisionDao.deleteAll()
                corporationDivisionDao.insertAll(
                    divisions.map {
                        CorporationDivisionEntity(
                            name = it.name.ifEmpty { "Division ${it.division}" },
                            divisionKey = it.division,
                            isMain = it.division == 1
                        )
                    }
                )
                Result.success(Unit)
            } else {
                Result.failure(Exception("ESI error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncAssets(corpId: Long): Result<Unit> {
        return try {
            val allItems = mutableListOf<HangarItemDto>()
            var page = 1
            var totalPages = 1

            while (page <= totalPages) {
                if (page > 1) delay(Constants.ESI_PAGE_DELAY_MS)
                val response = esiApi.getAssets(corpId, page = page)
                if (response.isSuccessful) {
                    response.body()?.let { allItems.addAll(it) }
                    totalPages = response.headers()["X-Pages"]?.toIntOrNull() ?: 1
                    page++
                } else if (response.code() == 429) {
                    val retryAfter = response.headers()["Retry-After"]?.toLongOrNull() ?: 10
                    delay(retryAfter * 1000)
                    continue
                } else {
                    return Result.failure(Exception("ESI error: ${response.code()}"))
                }
            }

            hangarItemDao.deleteAll()

            val divisions = corporationDivisionDao.getAll().first()
            if (divisions.isEmpty()) {
                Log.w(TAG, "No divisions found, skipping asset insert")
                return Result.success(Unit)
            }

            val divisionByFlag = divisions.associate { "CorpSAG${it.divisionKey}" to it.divisionId }
            val defaultDivisionId = divisions.firstOrNull { it.isMain }?.divisionId
                ?: divisions.first().divisionId

            val entities = allItems.mapNotNull { item ->
                val divId = divisionByFlag[item.locationFlag]
                    ?: divisionByFlag["CorpSAG${mapFlagToDivision(item.locationFlag)}"]
                    ?: defaultDivisionId
                HangarItemEntity(
                    itemId = item.itemId,
                    divisionId = divId,
                    typeId = item.typeId,
                    quantity = item.quantity,
                    locationFlag = item.locationFlag
                )
            }
            hangarItemDao.insertAll(entities)

            val typeIds = allItems.map { it.typeId }.distinct()
            syncTypeNames(typeIds)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncTypeNames(typeIds: List<Long>) {
        try {
            val missing = typeIds.filter { typeNameCacheDao.getName(it) == null }
            if (missing.isEmpty()) return

            missing.chunked(100).forEach { chunk ->
                val response = esiApi.postUniverseNames(chunk)
                if (response.isSuccessful) {
                    val names = response.body() ?: emptyList()
                    typeNameCacheDao.insertAll(
                        names.map { TypeNameCacheEntity(typeId = it.id, name = it.name) }
                    )
                } else {
                    Log.w(TAG, "syncTypeNames failed: ${response.code()}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "syncTypeNames error", e)
        }
    }

    suspend fun getTypeName(typeId: Long): String =
        typeNameCacheDao.getName(typeId) ?: "Unknown ($typeId)"
}

private fun mapFlagToDivision(flag: String): Int {
    val match = Regex("""CorpSAG(\d+)""").find(flag)
    return match?.groupValues?.get(1)?.toIntOrNull() ?: 1
}
