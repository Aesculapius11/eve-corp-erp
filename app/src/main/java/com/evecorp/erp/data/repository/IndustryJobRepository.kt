package com.evecorp.erp.data.repository

import com.evecorp.erp.data.local.dao.IndustryJobDao
import com.evecorp.erp.data.local.entity.IndustryJobEntity
import com.evecorp.erp.data.remote.api.EveEsiApi
import com.evecorp.erp.data.remote.dto.IndustryJobDto
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IndustryJobRepository @Inject constructor(
    private val industryJobDao: IndustryJobDao,
    private val esiApi: EveEsiApi
) {
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
                val response = esiApi.getIndustryJobs(corpId, page = page)
                if (response.isSuccessful) {
                    response.body()?.let { allJobs.addAll(it) }
                    totalPages = response.headers()["X-Pages"]?.toIntOrNull() ?: 1
                    page++
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
    installDate = Instant.parse(installDate).toEpochMilli(),
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
