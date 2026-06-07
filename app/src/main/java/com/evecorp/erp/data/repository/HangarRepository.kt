package com.evecorp.erp.data.repository

import com.evecorp.erp.data.local.dao.CorporationDivisionDao
import com.evecorp.erp.data.local.dao.HangarItemDao
import com.evecorp.erp.data.local.dao.TypeNameCacheDao
import com.evecorp.erp.data.local.entity.CorporationDivisionEntity
import com.evecorp.erp.data.local.entity.HangarItemEntity
import com.evecorp.erp.data.remote.api.EveEsiApi
import com.evecorp.erp.data.remote.dto.HangarItemDto
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

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
                val divisions = response.body() ?: emptyList()
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
                val response = esiApi.getAssets(corpId, page = page)
                if (response.isSuccessful) {
                    response.body()?.let { allItems.addAll(it) }
                    totalPages = response.headers()["X-Pages"]?.toIntOrNull() ?: 1
                    page++
                } else {
                    return Result.failure(Exception("ESI error: ${response.code()}"))
                }
            }

            // Clear and re-insert
            hangarItemDao.deleteAll()

            // Group by location_flag to map to divisions
            val divisionMap = mutableMapOf<String, Long>()
            // TODO: Map location_flag to division_id properly

            val entities = allItems.map { item ->
                HangarItemEntity(
                    itemId = item.itemId,
                    divisionId = 1, // TODO: proper mapping
                    typeId = item.typeId,
                    quantity = item.quantity,
                    locationFlag = item.locationFlag
                )
            }
            hangarItemDao.insertAll(entities)

            // Cache type names
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

            // ESI accepts max 100 IDs per request
            missing.chunked(100).forEach { chunk ->
                val response = esiApi.postUniverseNames(chunk)
                if (response.isSuccessful) {
                    val names = response.body() ?: emptyList()
                    typeNameCacheDao.insertAll(
                        names.map {
                            com.evecorp.erp.data.local.entity.TypeNameCacheEntity(
                                typeId = it.id,
                                name = it.name
                            )
                        }
                    )
                }
            }
        } catch (_: Exception) { }
    }

    suspend fun getTypeName(typeId: Long): String =
        typeNameCacheDao.getName(typeId) ?: "Unknown ($typeId)"
}
