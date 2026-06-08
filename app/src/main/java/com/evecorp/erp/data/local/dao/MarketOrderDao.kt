package com.evecorp.erp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.evecorp.erp.data.local.entity.MarketOrderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MarketOrderDao {

    @Query("SELECT * FROM market_order WHERE corporationId = :corpId AND state = 'active' AND isBuyOrder = 0 ORDER BY issued DESC")
    fun getActiveSellOrders(corpId: Long): Flow<List<MarketOrderEntity>>

    @Query("SELECT * FROM market_order WHERE corporationId = :corpId AND state = 'active' AND isBuyOrder = 1 ORDER BY issued DESC")
    fun getActiveBuyOrders(corpId: Long): Flow<List<MarketOrderEntity>>

    @Query("SELECT * FROM market_order WHERE corporationId = :corpId AND state = 'active' ORDER BY issued DESC")
    fun getActiveOrders(corpId: Long): Flow<List<MarketOrderEntity>>

    @Query("SELECT * FROM market_order WHERE state = 'active' ORDER BY issued DESC")
    fun getAllActiveOrders(): Flow<List<MarketOrderEntity>>

    @Query("SELECT * FROM market_order WHERE corporationId = :corpId ORDER BY issued DESC")
    fun getAllOrders(corpId: Long): Flow<List<MarketOrderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(orders: List<MarketOrderEntity>)

    @Query("DELETE FROM market_order WHERE corporationId = :corpId")
    suspend fun deleteAll(corpId: Long)
}
