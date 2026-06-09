package com.evecorp.erp.data.repository

import android.util.Log
import com.evecorp.erp.Constants
import com.evecorp.erp.data.local.dao.WalletBalanceDao
import com.evecorp.erp.data.local.dao.WalletJournalDao
import com.evecorp.erp.data.local.entity.WalletBalanceEntity
import com.evecorp.erp.data.local.entity.WalletJournalEntity
import com.evecorp.erp.data.remote.api.EveEsiApi
import com.evecorp.erp.data.remote.dto.WalletJournalDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "WalletRepo"

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

    suspend fun syncJournal(corpId: Long): Result<Unit> {
        return try {
            val maxId = walletJournalDao.getMaxId(corpId)
            val allEntries = mutableListOf<WalletJournalDto>()

            // 同步所有 7 个 division
            for (division in 1..7) {
                var page = 1
                var totalPages = 1

                while (page <= totalPages) {
                    if (page > 1) delay(Constants.ESI_PAGE_DELAY_MS)
                    val response = esiApi.getWalletJournal(corpId, division, page)
                    if (response.isSuccessful) {
                        response.body()?.let { allEntries.addAll(it) }
                        totalPages = response.headers()["X-Pages"]?.toIntOrNull() ?: 1
                        page++
                    } else if (response.code() == 429) {
                        val retryAfter = response.headers()["Retry-After"]?.toLongOrNull() ?: 10
                        delay(retryAfter * 1000)
                        continue
                    } else if (response.code() == 404 || response.code() == 403) {
                        // 该 division 不存在或无权限，跳过
                        break
                    } else {
                        return Result.failure(Exception("ESI error: ${response.code()}"))
                    }
                }
                delay(Constants.ESI_PAGE_DELAY_MS)
            }

            val newEntries = allEntries
                .filter { maxId == null || it.id > maxId }
                .mapNotNull { it.toEntityOrNull(corpId) }
            if (newEntries.isNotEmpty()) {
                walletJournalDao.insertAll(newEntries)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

private fun WalletJournalDto.toEntityOrNull(corpId: Long): WalletJournalEntity? {
    val dateMs = date.toEpochMillisOrNull() ?: return null
    return WalletJournalEntity(
        id = id,
        corporationId = corpId,
        division = 1, // TODO: 从 response header 或 URL 推断 division
        date = dateMs,
        refType = refType,
        amount = amount,
        description = description,
        contextId = contextId,
        contextIdType = contextIdType
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
