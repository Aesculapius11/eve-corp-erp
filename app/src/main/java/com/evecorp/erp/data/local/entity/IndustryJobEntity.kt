package com.evecorp.erp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "industry_job",
    indices = [Index(value = ["corporationId", "status"])]
)
data class IndustryJobEntity(
    @PrimaryKey val jobId: Long,
    val corporationId: Long,
    val activityType: String,
    val blueprintId: Long,
    val blueprintTypeId: Long,
    val productTypeId: Long? = null,
    val runs: Int,
    val successfulRuns: Int? = null,
    val licensedRuns: Int? = null,
    val status: String,
    val startDate: Long,
    val endDate: Long,
    val installDate: Long,
    val facilityId: Long,
    val stationId: Long? = null,
    val locationId: Long,
    val cost: Double? = null,
    val installerId: Long
)
