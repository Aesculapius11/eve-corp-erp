package com.evecorp.erp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "corporation_division")
data class CorporationDivisionEntity(
    @PrimaryKey(autoGenerate = true) val divisionId: Long = 0,
    val name: String,
    val divisionKey: Int,
    val isMain: Boolean = false
)
