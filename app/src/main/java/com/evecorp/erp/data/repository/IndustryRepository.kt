package com.evecorp.erp.data.repository

import com.evecorp.erp.data.local.dao.SystemCostIndexDao
import com.evecorp.erp.data.local.entity.SystemCostIndexEntity
import com.evecorp.erp.data.remote.api.EveEsiApi
import com.evecorp.erp.data.remote.dto.IndustrySystemDto
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IndustryRepository @Inject constructor(
    private val systemCostIndexDao: SystemCostIndexDao,
    private val esiApi: EveEsiApi
) {
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
}

private fun IndustrySystemDto.toEntity(): SystemCostIndexEntity {
    val indices = costIndices.associate { it.activity to it.costIndex }
    return SystemCostIndexEntity(
        systemId = solarSystemId,
        systemName = "", // Will be resolved via universe API
        manufacturing = indices["manufacturing"] ?: 0.0,
        researchingTimeEfficiency = indices["researching_time_efficiency"] ?: 0.0,
        researchingMaterialEfficiency = indices["researching_material_efficiency"] ?: 0.0,
        copying = indices["copying"] ?: 0.0,
        invention = indices["invention"] ?: 0.0,
        lastUpdated = System.currentTimeMillis()
    )
}
