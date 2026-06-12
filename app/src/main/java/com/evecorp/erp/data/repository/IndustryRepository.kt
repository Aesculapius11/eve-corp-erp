package com.evecorp.erp.data.repository

import android.util.Log
import com.evecorp.erp.Constants
import com.evecorp.erp.data.local.dao.IndustryJobDao
import com.evecorp.erp.data.local.dao.SystemCostIndexDao
import com.evecorp.erp.data.local.dao.TypeNameCacheDao
import com.evecorp.erp.data.local.entity.IndustryJobEntity
import com.evecorp.erp.data.local.entity.SystemCostIndexEntity
import com.evecorp.erp.data.local.entity.TypeNameCacheEntity
import com.evecorp.erp.data.remote.api.EveEsiApi
import com.evecorp.erp.data.remote.dto.IndustryJobDto
import com.evecorp.erp.data.remote.dto.IndustrySystemDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "IndustryRepo"
private const val RECENT_WINDOW_MS = 30L * 24 * 60 * 60 * 1000 // 30 天

@Singleton
class IndustryRepository @Inject constructor(
    private val systemCostIndexDao: SystemCostIndexDao,
    private val industryJobDao: IndustryJobDao,
    private val typeNameCacheDao: TypeNameCacheDao,
    private val esiApi: EveEsiApi
) {
    fun getAllCostIndices(): Flow<List<SystemCostIndexEntity>> = systemCostIndexDao.getAll()

    fun getCostIndex(systemId: Long): Flow<SystemCostIndexEntity?> =
        systemCostIndexDao.getBySystemId(systemId)

    /** 按名称搜索星系，返回 solar_system 类型的结果 */
    suspend fun searchSystems(query: String): Result<List<Pair<Long, String>>> {
        return try {
            val response = esiApi.searchUniverseIds(listOf(query))
            if (response.isSuccessful) {
                val results = response.body()
                    ?.filter { it.category == "solar_system" }
                    ?.map { it.id to it.name }
                    ?: emptyList()
                Result.success(results)
            } else {
                Result.failure(Exception("ESI error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncCostIndices(interestingSystemIds: List<Long> = emptyList()): Result<Unit> {
        return try {
            val response = esiApi.getIndustrySystems()
            if (response.isSuccessful) {
                val systems = response.body() ?: emptyList()
                val filtered = if (interestingSystemIds.isNotEmpty()) {
                    systems.filter { it.solarSystemId in interestingSystemIds }
                } else systems

                val entities = filtered.map { it.toEntity() }
                systemCostIndexDao.deleteAll()
                systemCostIndexDao.insertAll(entities)
                Result.success(Unit)
            } else {
                Result.failure(Exception("ESI error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getActiveJobs(corpId: Long): Flow<List<IndustryJobEntity>> =
        industryJobDao.getActiveJobs(corpId)

    /** 活跃作业 + 近期已完成作业（30天内） */
    fun getRecentJobs(corpId: Long): Flow<List<IndustryJobEntity>> =
        industryJobDao.getRecentJobs(corpId, System.currentTimeMillis() - RECENT_WINDOW_MS)

    fun getActiveJobsByActivity(corpId: Long, activity: String): Flow<List<IndustryJobEntity>> =
        industryJobDao.getActiveJobsByActivity(corpId, activity)

    /** 获取物品名称，未缓存返回 "Unknown (typeId)" */
    suspend fun getTypeName(typeId: Long): String {
        return typeNameCacheDao.getName(typeId) ?: "Unknown ($typeId)"
    }

    /** 批量解析并缓存物品名称 */
    suspend fun syncTypeNames(typeIds: List<Long>) {
        val missing = typeIds.filter { typeNameCacheDao.getName(it) == null }
        if (missing.isEmpty()) return
        try {
            // 分 chunk 查询，每批 100 个
            for (chunk in missing.chunked(100)) {
                val response = esiApi.postUniverseNames(chunk)
                if (response.isSuccessful) {
                    val entries = response.body()?.map {
                        TypeNameCacheEntity(typeId = it.id, name = it.name)
                    } ?: emptyList()
                    if (entries.isNotEmpty()) {
                        typeNameCacheDao.insertAll(entries)
                    }
                }
                delay(Constants.ESI_PAGE_DELAY_MS)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync type names", e)
        }
    }

    /** 批量解析并缓存角色名称 */
    suspend fun syncCharacterNames(characterIds: List<Long>) {
        try {
            val missing = characterIds.filter { typeNameCacheDao.getName(it) == null }
            if (missing.isEmpty()) return

            missing.chunked(100).forEach { chunk ->
                val response = esiApi.postUniverseNames(chunk)
                if (response.isSuccessful) {
                    val entries = response.body()?.map {
                        TypeNameCacheEntity(typeId = it.id, name = it.name)
                    } ?: emptyList()
                    if (entries.isNotEmpty()) {
                        typeNameCacheDao.insertAll(entries)
                    }
                }
                delay(Constants.ESI_PAGE_DELAY_MS)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync character names", e)
        }
    }

    suspend fun syncJobs(corpId: Long): Result<Unit> {
        return try {
            val allJobs = mutableListOf<IndustryJobDto>()
            var page = 1
            var totalPages = 1

            while (page <= totalPages) {
                if (page > 1) delay(Constants.ESI_PAGE_DELAY_MS)
                val response = esiApi.getIndustryJobs(corpId, page = page)
                if (response.isSuccessful) {
                    response.body()?.let { allJobs.addAll(it) }
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

            industryJobDao.deleteAll(corpId)
            industryJobDao.insertAll(allJobs.mapNotNull { it.toEntityOrNull(corpId) })

            // 解析蓝图和产品名称
            val typeIds = allJobs.mapNotNull { dto ->
                listOfNotNull(dto.blueprintTypeId, dto.productTypeId)
            }.flatten().distinct()
            syncTypeNames(typeIds)

            // 解析安装者角色名称
            val installerIds = allJobs.map { it.installerId }.distinct()
            syncCharacterNames(installerIds)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

private fun IndustrySystemDto.toEntity(): SystemCostIndexEntity {
    val indices = costIndices.associate { it.activity to it.costIndex }
    return SystemCostIndexEntity(
        systemId = solarSystemId,
        systemName = "",
        manufacturing = indices["manufacturing"] ?: 0.0,
        researchingTimeEfficiency = indices["researching_time_efficiency"] ?: 0.0,
        researchingMaterialEfficiency = indices["researching_material_efficiency"] ?: 0.0,
        copying = indices["copying"] ?: 0.0,
        invention = indices["invention"] ?: 0.0,
        lastUpdated = System.currentTimeMillis()
    )
}

private fun IndustryJobDto.toEntityOrNull(corpId: Long): IndustryJobEntity? {
    val startMs = startDate.toEpochMillisOrNull() ?: return null
    val endMs = endDate.toEpochMillisOrNull() ?: return null
    val installMs = installDate?.toEpochMillisOrNull() ?: startMs
    return IndustryJobEntity(
        jobId = jobId,
        corporationId = corpId,
        activityType = mapActivityId(activityId),
        blueprintId = blueprintId,
        blueprintTypeId = blueprintTypeId,
        productTypeId = productTypeId,
        runs = runs,
        successfulRuns = successfulRuns,
        licensedRuns = licensedRuns,
        status = status,
        startDate = startMs,
        endDate = endMs,
        installDate = installMs,
        facilityId = facilityId,
        stationId = stationId,
        locationId = locationId,
        cost = cost,
        installerId = installerId
    )
}

private fun String.toEpochMillisOrNull(): Long? {
    return try {
        Instant.parse(this).toEpochMilli()
    } catch (e: Exception) {
        Log.w(TAG, "Failed to parse date: $this", e)
        null
    }
}

private fun mapActivityId(id: Int): String = when (id) {
    1 -> "manufacturing"
    3 -> "researching_time_efficiency"
    4 -> "researching_material_efficiency"
    5 -> "copying"
    8 -> "invention"
    else -> {
        Log.w(TAG, "Unknown activity ID: $id")
        "unknown"
    }
}
