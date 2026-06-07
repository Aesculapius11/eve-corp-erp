package com.evecorp.erp.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CorporationBillDto(
    @Json(name = "bill_id") val billId: Long,
    @Json(name = "bill_type") val billType: String,
    @Json(name = "amount") val amount: Double,
    @Json(name = "due_date") val dueDate: String,
    @Json(name = "issuer_id") val issuerId: Long,
    @Json(name = "paid") val paid: Boolean
)
