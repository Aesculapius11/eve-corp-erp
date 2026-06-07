package com.evecorp.erp.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CorporationDivisionDto(
    @Json(name = "division") val division: Int,
    @Json(name = "name") val name: String
)

@JsonClass(generateAdapter = true)
data class HangarItemDto(
    @Json(name = "item_id") val itemId: Long,
    @Json(name = "type_id") val typeId: Long,
    @Json(name = "quantity") val quantity: Int,
    @Json(name = "location_flag") val locationFlag: String
)

@JsonClass(generateAdapter = true)
data class UniverseNameDto(
    @Json(name = "id") val id: Long,
    @Json(name = "name") val name: String,
    @Json(name = "category") val category: String? = null
)
