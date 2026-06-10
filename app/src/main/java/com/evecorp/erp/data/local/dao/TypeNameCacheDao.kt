package com.evecorp.erp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.evecorp.erp.data.local.entity.TypeNameCacheEntity

@Dao
interface TypeNameCacheDao {

    @Query("SELECT name FROM type_name_cache WHERE typeId = :typeId")
    suspend fun getName(typeId: Long): String?

    @Query("SELECT * FROM type_name_cache WHERE typeId IN (:typeIds)")
    suspend fun getByTypeIds(typeIds: List<Long>): List<TypeNameCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<TypeNameCacheEntity>)

    @Query("DELETE FROM type_name_cache")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM type_name_cache")
    suspend fun count(): Int
}
