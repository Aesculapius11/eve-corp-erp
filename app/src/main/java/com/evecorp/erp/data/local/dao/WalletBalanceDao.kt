package com.evecorp.erp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.evecorp.erp.data.local.entity.WalletBalanceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WalletBalanceDao {

    @Query("SELECT * FROM wallet_balance WHERE corporationId = :corpId")
    fun getBalance(corpId: Long): Flow<WalletBalanceEntity?>

    @Query("SELECT * FROM wallet_balance WHERE corporationId = :corpId LIMIT 1")
    suspend fun getBalanceSync(corpId: Long): WalletBalanceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(balance: WalletBalanceEntity)
}
