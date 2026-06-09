package com.evecorp.erp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.evecorp.erp.data.local.entity.WalletJournalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WalletJournalDao {

    @Query("SELECT * FROM wallet_journal WHERE corporationId = :corpId ORDER BY date DESC LIMIT :limit")
    fun getRecent(corpId: Long, limit: Int = 50): Flow<List<WalletJournalEntity>>

    @Query("SELECT MAX(id) FROM wallet_journal WHERE corporationId = :corpId")
    suspend fun getMaxId(corpId: Long): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<WalletJournalEntity>)

    @Query("SELECT * FROM wallet_journal WHERE corporationId = :corpId AND date >= :sinceMs ORDER BY date ASC")
    suspend fun getJournalSince(corpId: Long, sinceMs: Long): List<WalletJournalEntity>

    @Query("DELETE FROM wallet_journal WHERE corporationId = :corpId")
    suspend fun deleteAll(corpId: Long)
}
