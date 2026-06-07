package com.evecorp.erp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.evecorp.erp.data.local.entity.SystemCostIndexEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SystemCostIndexDao {

    @Query("SELECT * FROM system_cost_index ORDER BY systemName")
    fun getAll(): Flow<List<SystemCostIndexEntity>>

    @Query("SELECT * FROM system_cost_index WHERE systemId = :systemId")
    fun getBySystemId(systemId: Long): Flow<SystemCostIndexEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<SystemCostIndexEntity>)

    @Query("DELETE FROM system_cost_index")
    suspend fun deleteAll()
}
