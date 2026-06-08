package com.evecorp.erp.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class IndustrySystemDto(
    @Json(name = "solar_system_id") val solarSystemId: Long,
    @Json(name = "cost_indices") val costIndices: List<CostIndexDto>
)

@JsonClass(generateAdapter = true)
data class CostIndexDto(
    @Json(name = "activity") val activity: String,
    @Json(name = "cost_index") val costIndex: Double
)

@JsonClass(generateAdapter = true)
data class IndustryJobDto(
    @Json(name = "job_id") val jobId: Long,
    @Json(name = "activity_id") val activityId: Int,
    @Json(name = "blueprint_id") val blueprintId: Long,
    @Json(name = "blueprint_type_id") val blueprintTypeId: Long,
    @Json(name = "product_type_id") val productTypeId: Long? = null,
    @Json(name = "runs") val runs: Int,
    @Json(name = "successful_runs") val successfulRuns: Int? = null,
    @Json(name = "licensed_runs") val licensedRuns: Int? = null,
    @Json(name = "status") val status: String,
    @Json(name = "start_date") val startDate: String,
    @Json(name = "end_date") val endDate: String,
    @Json(name = "install_date") val installDate: String? = null,
    @Json(name = "facility_id") val facilityId: Long,
    @Json(name = "station_id") val stationId: Long? = null,
    @Json(name = "location_id") val locationId: Long,
    @Json(name = "cost") val cost: Double? = null,
    @Json(name = "installer_id") val installerId: Long
)
