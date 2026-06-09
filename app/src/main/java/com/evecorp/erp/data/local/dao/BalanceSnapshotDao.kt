package com.evecorp.erp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.evecorp.erp.data.local.entity.BalanceSnapshotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BalanceSnapshotDao {

    /** 获取最近 N 天的余额快照，按日期升序 */
    @Query("""
        SELECT * FROM balance_snapshot
        WHERE corporationId = :corpId
        ORDER BY date ASC
    """)
    fun getHistory(corpId: Long): Flow<List<BalanceSnapshotEntity>>

    /** 同一天的快照用 REPLACE 策略覆盖 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snapshot: BalanceSnapshotEntity)

    /** 删除超过 30 天的旧快照 */
    @Query("DELETE FROM balance_snapshot WHERE corporationId = :corpId AND date < :cutoffDate")
    suspend fun deleteOlderThan(corpId: Long, cutoffDate: String)
}
