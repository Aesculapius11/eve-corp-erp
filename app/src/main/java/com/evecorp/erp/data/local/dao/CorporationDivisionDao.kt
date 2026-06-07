package com.evecorp.erp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.evecorp.erp.data.local.entity.CorporationDivisionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CorporationDivisionDao {

    @Query("SELECT * FROM corporation_division ORDER BY divisionKey")
    fun getAll(): Flow<List<CorporationDivisionEntity>>

    @Query("SELECT * FROM corporation_division WHERE isMain = 1 LIMIT 1")
    fun getMain(): Flow<CorporationDivisionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(divisions: List<CorporationDivisionEntity>)

    @Update
    suspend fun update(division: CorporationDivisionEntity)

    @Query("UPDATE corporation_division SET isMain = 0 WHERE isMain = 1")
    suspend fun clearMainFlag()

    @Query("UPDATE corporation_division SET isMain = 1 WHERE divisionId = :divisionId")
    suspend fun setMain(divisionId: Long)

    @Query("DELETE FROM corporation_division")
    suspend fun deleteAll()
}
