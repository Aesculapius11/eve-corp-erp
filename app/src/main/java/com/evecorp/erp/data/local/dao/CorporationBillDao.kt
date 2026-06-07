package com.evecorp.erp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.evecorp.erp.data.local.entity.CorporationBillEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CorporationBillDao {

    @Query("SELECT * FROM corporation_bill WHERE corporationId = :corpId ORDER BY dueDate ASC")
    fun getAll(corpId: Long): Flow<List<CorporationBillEntity>>

    @Query("SELECT * FROM corporation_bill WHERE corporationId = :corpId AND paid = 0 ORDER BY dueDate ASC")
    fun getUnpaid(corpId: Long): Flow<List<CorporationBillEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(bills: List<CorporationBillEntity>)

    @Query("DELETE FROM corporation_bill WHERE corporationId = :corpId")
    suspend fun deleteAll(corpId: Long)
}
