package com.evecorp.erp.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class WalletBalanceDto(
    @Json(name = "balance") val balance: Double,
    @Json(name = "division") val division: Int
)

@JsonClass(generateAdapter = true)
data class WalletJournalDto(
    @Json(name = "id") val id: Long,
    @Json(name = "date") val date: String,
    @Json(name = "ref_type") val refType: String,
    @Json(name = "amount") val amount: Double,
    @Json(name = "description") val description: String? = null,
    @Json(name = "context_id") val contextId: Long? = null,
    @Json(name = "context_id_type") val contextIdType: String? = null
)
