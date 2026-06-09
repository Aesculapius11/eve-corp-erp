package com.evecorp.erp.data.repository

import android.util.Log
import com.evecorp.erp.Constants
import com.evecorp.erp.data.local.dao.BalanceSnapshotDao
import com.evecorp.erp.data.local.dao.WalletBalanceDao
import com.evecorp.erp.data.local.dao.WalletJournalDao
import com.evecorp.erp.data.local.entity.BalanceSnapshotEntity
import com.evecorp.erp.data.local.entity.WalletBalanceEntity
import com.evecorp.erp.data.local.entity.WalletJournalEntity
import com.evecorp.erp.data.remote.api.EveEsiApi
import com.evecorp.erp.data.remote.dto.WalletJournalDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "WalletRepo"

@Singleton
class WalletRepository @Inject constructor(
    private val walletBalanceDao: WalletBalanceDao,
    private val walletJournalDao: WalletJournalDao,
    private val balanceSnapshotDao: BalanceSnapshotDao,
    private val esiApi: EveEsiApi
) {
    fun getBalance(corpId: Long): Flow<WalletBalanceEntity?> = walletBalanceDao.getBalance(corpId)

    fun getRecentJournal(corpId: Long, limit: Int = 50): Flow<List<WalletJournalEntity>> =
        walletJournalDao.getRecent(corpId, limit)

    /** 获取最近 30 天的余额快照（按日期升序） */
    fun getBalanceHistory(corpId: Long): Flow<List<BalanceSnapshotEntity>> =
        balanceSnapshotDao.getHistory(corpId)

    suspend fun syncBalance(corpId: Long): Result<Unit> {
        return try {
            val response = esiApi.getWallets(corpId)
            if (response.isSuccessful) {
                val wallets = response.body() ?: emptyList()
                val totalBalance = wallets.sumOf { it.balance }
                val now = System.currentTimeMillis()

                walletBalanceDao.upsert(
                    WalletBalanceEntity(
                        corporationId = corpId,
                        balance = totalBalance,
                        lastUpdated = now
                    )
                )

                // 存每日快照
                val today = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE)
                balanceSnapshotDao.upsert(
                    BalanceSnapshotEntity(
                        corporationId = corpId,
                        balance = totalBalance,
                        date = today,
                        timestamp = now
                    )
                )

                // 清理 30 天前的旧快照
                val cutoff = LocalDate.now(ZoneOffset.UTC).minusDays(30)
                    .format(DateTimeFormatter.ISO_LOCAL_DATE)
                balanceSnapshotDao.deleteOlderThan(corpId, cutoff)

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
        division = 1,
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
