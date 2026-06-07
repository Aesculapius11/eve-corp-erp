package com.evecorp.erp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "hangar_item",
    foreignKeys = [
        ForeignKey(
            entity = CorporationDivisionEntity::class,
            parentColumns = ["divisionId"],
            childColumns = ["divisionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["divisionId"])]
)
data class HangarItemEntity(
    @PrimaryKey val itemId: Long,
    val divisionId: Long,
    val typeId: Long,
    val quantity: Int,
    val locationFlag: String
)
