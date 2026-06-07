package com.evecorp.erp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.evecorp.erp.data.local.entity.HangarItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HangarItemDao {

    @Query("SELECT * FROM hangar_item WHERE divisionId = :divisionId ORDER BY quantity DESC")
    fun getByDivision(divisionId: Long): Flow<List<HangarItemEntity>>

    @Query("""
        SELECT hi.* FROM hangar_item hi
        INNER JOIN corporation_division cd ON hi.divisionId = cd.divisionId
        WHERE cd.isMain = 1
        ORDER BY hi.quantity DESC
    """)
    fun getMainHangarItems(): Flow<List<HangarItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<HangarItemEntity>)

    @Query("DELETE FROM hangar_item WHERE divisionId = :divisionId")
    suspend fun deleteByDivision(divisionId: Long)

    @Query("DELETE FROM hangar_item")
    suspend fun deleteAll()
}
