package com.evecorp.erp.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MarketOrderDto(
    @Json(name = "order_id") val orderId: Long,
    @Json(name = "type_id") val typeId: Long,
    @Json(name = "location_id") val locationId: Long,
    @Json(name = "is_buy_order") val isBuyOrder: Boolean = false,
    @Json(name = "price") val price: Double,
    @Json(name = "volume_total") val volumeTotal: Int,
    @Json(name = "volume_remain") val volumeRemain: Int,
    @Json(name = "issued") val issued: String,
    @Json(name = "duration") val duration: Int,
    @Json(name = "state") val state: String = "active",
    @Json(name = "min_volume") val minVolume: Int = 1,
    @Json(name = "range") val range: String? = null,
    @Json(name = "issued_by") val issuedBy: Long? = null
)
