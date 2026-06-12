package com.evecorp.erp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.evecorp.erp.data.local.entity.IndustryJobEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IndustryJobDao {

    @Query("SELECT * FROM industry_job WHERE corporationId = :corpId AND status = 'active' ORDER BY endDate ASC")
    fun getActiveJobs(corpId: Long): Flow<List<IndustryJobEntity>>

    @Query("SELECT * FROM industry_job WHERE corporationId = :corpId AND status = 'active' AND activityType = :activity ORDER BY endDate ASC")
    fun getActiveJobsByActivity(corpId: Long, activity: String): Flow<List<IndustryJobEntity>>

    /** 活跃作业 + 近期（30天内）已完成的作业，避免列出所有历史数据 */
    @Query("""
        SELECT * FROM industry_job 
        WHERE corporationId = :corpId 
        AND (status = 'active' OR endDate > :recentSince) 
        ORDER BY endDate DESC
    """)
    fun getRecentJobs(corpId: Long, recentSince: Long): Flow<List<IndustryJobEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(jobs: List<IndustryJobEntity>)

    @Query("DELETE FROM industry_job WHERE corporationId = :corpId")
    suspend fun deleteAll(corpId: Long)
}
