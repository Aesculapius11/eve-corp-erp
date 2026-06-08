package com.evecorp.erp.data.repository

import com.evecorp.erp.data.local.dao.IndustryJobDao
import com.evecorp.erp.data.local.dao.SystemCostIndexDao
import com.evecorp.erp.data.local.entity.IndustryJobEntity
import com.evecorp.erp.data.local.entity.SystemCostIndexEntity
import com.evecorp.erp.data.remote.api.EveEsiApi
import com.evecorp.erp.data.remote.dto.IndustryJobDto
import com.evecorp.erp.data.remote.dto.IndustrySystemDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IndustryRepository @Inject constructor(
    private val systemCostIndexDao: SystemCostIndexDao,
    private val industryJobDao: IndustryJobDao,
    private val esiApi: EveEsiApi
) {
    // --- Cost Indices ---

    fun getAllCostIndices(): Flow<List<SystemCostIndexEntity>> = systemCostIndexDao.getAll()

    fun getCostIndex(systemId: Long): Flow<SystemCostIndexEntity?> =
        systemCostIndexDao.getBySystemId(systemId)

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

    // --- Industry Jobs ---

    fun getActiveJobs(corpId: Long): Flow<List<IndustryJobEntity>> =
        industryJobDao.getActiveJobs(corpId)

    fun getActiveJobsByActivity(corpId: Long, activity: String): Flow<List<IndustryJobEntity>> =
        industryJobDao.getActiveJobsByActivity(corpId, activity)

    suspend fun syncJobs(corpId: Long): Result<Unit> {
        return try {
            val allJobs = mutableListOf<IndustryJobDto>()
            var page = 1
            var totalPages = 1

            while (page <= totalPages) {
                if (page > 1) delay(PAGE_DELAY_MS)
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
            industryJobDao.insertAll(allJobs.map { it.toEntity(corpId) })
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

private const val PAGE_DELAY_MS = 150L

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

private fun IndustryJobDto.toEntity(corpId: Long) = IndustryJobEntity(
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
    startDate = Instant.parse(startDate).toEpochMilli(),
    endDate = Instant.parse(endDate).toEpochMilli(),
    installDate = installDate?.let { Instant.parse(it).toEpochMilli() } ?: Instant.parse(startDate).toEpochMilli(),
    facilityId = facilityId,
    stationId = stationId,
    locationId = locationId,
    cost = cost,
    installerId = installerId
)

private fun mapActivityId(id: Int): String = when (id) {
    1 -> "manufacturing"
    3 -> "researching_time_efficiency"
    4 -> "researching_material_efficiency"
    5 -> "copying"
    8 -> "invention"
    else -> "unknown"
}
