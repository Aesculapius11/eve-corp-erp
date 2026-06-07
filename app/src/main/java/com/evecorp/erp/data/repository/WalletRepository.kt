package com.evecorp.erp.data.repository

import com.evecorp.erp.data.local.dao.WalletBalanceDao
import com.evecorp.erp.data.local.dao.WalletJournalDao
import com.evecorp.erp.data.local.entity.WalletBalanceEntity
import com.evecorp.erp.data.local.entity.WalletJournalEntity
import com.evecorp.erp.data.remote.api.EveEsiApi
import com.evecorp.erp.data.remote.dto.WalletBalanceDto
import com.evecorp.erp.data.remote.dto.WalletJournalDto
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
            val response = esiApi.getWalletJournal(corpId, division)
            if (response.isSuccessful) {
                val entries = response.body() ?: emptyList()
                val newEntries = entries
                    .filter { maxId == null || it.id > maxId }
                    .map { it.toEntity(corpId, division) }
                if (newEntries.isNotEmpty()) {
                    walletJournalDao.insertAll(newEntries)
                }
                Result.success(Unit)
            } else {
                Result.failure(Exception("ESI error: ${response.code()}"))
            }
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
