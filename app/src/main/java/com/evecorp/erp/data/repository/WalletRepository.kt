package com.evecorp.erp.data.repository

import com.evecorp.erp.data.local.dao.WalletBalanceDao
import com.evecorp.erp.data.local.dao.WalletJournalDao
import com.evecorp.erp.data.local.entity.WalletBalanceEntity
import com.evecorp.erp.data.local.entity.WalletJournalEntity
import com.evecorp.erp.data.remote.api.EveEsiApi
import com.evecorp.erp.data.remote.dto.WalletBalanceDto
import com.evecorp.erp.data.remote.dto.WalletJournalDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WalletRepository @Inject constructor(
    private val walletBalanceDao: WalletBalanceDao,
    private val walletJournalDao: WalletJournalDao,
    private val esiApi: EveEsiApi
) {
    fun getBalance(corpId: Long): Flow<WalletBalanceEntity?> = walletBalanceDao.getBalance(corpId)

    fun getRecentJournal(corpId: Long, limit: Int = 50): Flow<List<WalletJournalEntity>> =
        walletJournalDao.getRecent(corpId, limit)

    suspend fun syncBalance(corpId: Long): Result<Unit> {
        return try {
            val response = esiApi.getWallets(corpId)
            if (response.isSuccessful) {
                val wallets = response.body() ?: emptyList()
                val totalBalance = wallets.sumOf { it.balance }
                walletBalanceDao.upsert(
                    WalletBalanceEntity(
                        corporationId = corpId,
                        balance = totalBalance,
                        lastUpdated = System.currentTimeMillis()
                    )
                )
                Result.success(Unit)
            } else {
                Result.failure(Exception("ESI error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncJournal(corpId: Long, division: Int = 1): Result<Unit> {
        return try {
            val maxId = walletJournalDao.getMaxId(corpId)
            val allEntries = mutableListOf<com.evecorp.erp.data.remote.dto.WalletJournalDto>()
            var page = 1
            var totalPages = 1

            while (page <= totalPages) {
                if (page > 1) delay(150L)
                val response = esiApi.getWalletJournal(corpId, division, page)
                if (response.isSuccessful) {
                    response.body()?.let { allEntries.addAll(it) }
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

            val newEntries = allEntries
                .filter { maxId == null || it.id > maxId }
                .map { it.toEntity(corpId, division) }
            if (newEntries.isNotEmpty()) {
                walletJournalDao.insertAll(newEntries)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

private fun WalletJournalDto.toEntity(corpId: Long, division: Int) = WalletJournalEntity(
    id = id,
    corporationId = corpId,
    division = division,
    date = Instant.parse(date).toEpochMilli(),
    refType = refType,
    amount = amount,
    description = description,
    contextId = contextId,
    contextIdType = contextIdType
)
