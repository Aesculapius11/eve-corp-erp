package com.evecorp.erp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "system_cost_index")
data class SystemCostIndexEntity(
    @PrimaryKey val systemId: Long,
    val systemName: String,
    val manufacturing: Double,
    val researchingTimeEfficiency: Double,
    val researchingMaterialEfficiency: Double,
    val copying: Double,
    val invention: Double,
    val lastUpdated: Long
)
