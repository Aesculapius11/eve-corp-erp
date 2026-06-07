package com.evecorp.erp.data.repository

import com.evecorp.erp.data.local.dao.MarketOrderDao
import com.evecorp.erp.data.local.entity.MarketOrderEntity
import com.evecorp.erp.data.remote.api.EveEsiApi
import com.evecorp.erp.data.remote.dto.MarketOrderDto
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MarketRepository @Inject constructor(
    private val marketOrderDao: MarketOrderDao,
    private val esiApi: EveEsiApi
) {
    fun getActiveSellOrders(corpId: Long): Flow<List<MarketOrderEntity>> =
        marketOrderDao.getActiveSellOrders(corpId)

    fun getActiveBuyOrders(corpId: Long): Flow<List<MarketOrderEntity>> =
        marketOrderDao.getActiveBuyOrders(corpId)

    suspend fun syncOrders(corpId: Long): Result<Unit> {
        return try {
            val allOrders = mutableListOf<MarketOrderDto>()
            var page = 1
            var totalPages = 1

            while (page <= totalPages) {
                val response = esiApi.getMarketOrders(corpId, page = page)
                if (response.isSuccessful) {
                    response.body()?.let { allOrders.addAll(it) }
                    totalPages = response.headers()["X-Pages"]?.toIntOrNull() ?: 1
                    page++
                } else {
                    return Result.failure(Exception("ESI error: ${response.code()}"))
                }
            }

            marketOrderDao.deleteAll(corpId)
            marketOrderDao.insertAll(allOrders.map { it.toEntity(corpId) })
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

private fun MarketOrderDto.toEntity(corpId: Long) = MarketOrderEntity(
    orderId = orderId,
    corporationId = corpId,
    typeId = typeId,
    locationId = locationId,
    isBuyOrder = isBuyOrder,
    price = price,
    volumeTotal = volumeTotal,
    volumeRemain = volumeRemain,
    issued = Instant.parse(issued).toEpochMilli(),
    duration = duration,
    state = state,
    minVolume = minVolume,
    range = range,
    issuedBy = issuedBy
)
