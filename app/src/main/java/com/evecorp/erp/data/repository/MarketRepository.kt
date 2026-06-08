package com.evecorp.erp.data.repository

import com.evecorp.erp.data.local.dao.MarketOrderDao
import com.evecorp.erp.data.local.entity.MarketOrderEntity
import com.evecorp.erp.data.remote.api.EveEsiApi
import com.evecorp.erp.data.remote.dto.MarketOrderDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MarketRepository @Inject constructor(
    private val marketOrderDao: MarketOrderDao,
    private val typeNameCacheDao: com.evecorp.erp.data.local.dao.TypeNameCacheDao,
    private val esiApi: EveEsiApi
) {
    fun getActiveSellOrders(corpId: Long): Flow<List<MarketOrderEntity>> =
        marketOrderDao.getActiveSellOrders(corpId)

    fun getActiveBuyOrders(corpId: Long): Flow<List<MarketOrderEntity>> =
        marketOrderDao.getActiveBuyOrders(corpId)

    fun getAllActiveOrders(): Flow<List<MarketOrderEntity>> =
        marketOrderDao.getAllActiveOrders()

    suspend fun getTypeName(typeId: Long): String =
        typeNameCacheDao.getName(typeId) ?: "Unknown ($typeId)"

    suspend fun syncTypeNames(orders: List<MarketOrderEntity>) {
        val typeIds = orders.map { it.typeId }.distinct()
        val missing = typeIds.filter { typeNameCacheDao.getName(it) == null }
        if (missing.isNotEmpty()) {
            try {
                val resp = esiApi.postUniverseNames(missing)
                if (resp.isSuccessful) {
                    resp.body()?.let { names ->
                        typeNameCacheDao.insertAll(names.map {
                            com.evecorp.erp.data.local.entity.TypeNameCacheEntity(
                                typeId = it.id,
                                name = it.name
                            )
                        })
                    }
                }
            } catch (_: Exception) {}
        }
    }

    suspend fun syncOrders(corpId: Long): Result<Unit> {
        return try {
            val allOrders = mutableListOf<MarketOrderDto>()
            var page = 1
            var totalPages = 1

            while (page <= totalPages) {
                if (page > 1) delay(150L)
                val response = esiApi.getMarketOrders(corpId, page = page)
                if (response.isSuccessful) {
                    response.body()?.let { allOrders.addAll(it) }
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

            marketOrderDao.deleteAll(corpId)
            marketOrderDao.insertAll(allOrders.map { it.toEntity(corpId) })
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncCharacterOrders(characterId: Long): Result<Unit> {
        return try {
            val response = esiApi.getCharacterOrders(characterId)
            if (response.isSuccessful) {
                val orders = response.body() ?: emptyList()
                // 个人订单 corporationId 设为 0 区分
                marketOrderDao.insertAll(orders.map { it.toEntity(0) })
                Result.success(Unit)
            } else {
                Result.failure(Exception("ESI error: ${response.code()}"))
            }
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
