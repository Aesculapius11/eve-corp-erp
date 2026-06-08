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

@JsonClass(generateAdapter = true)
data class CorporationInfoDto(
    @Json(name = "corporation_id") val corporationId: Long,
    @Json(name = "name") val name: String,
    @Json(name = "ticker") val ticker: String,
    @Json(name = "ceo_id") val ceoId: Long? = null,
    @Json(name = "alliance_id") val allianceId: Long? = null,
    @Json(name = "member_count") val memberCount: Int = 0,
    @Json(name = "description") val description: String? = null
)

@JsonClass(generateAdapter = true)
data class AllianceInfoDto(
    @Json(name = "alliance_id") val allianceId: Long,
    @Json(name = "name") val name: String,
    @Json(name = "ticker") val ticker: String
)

@JsonClass(generateAdapter = true)
data class CharacterInfoDto(
    @Json(name = "character_id") val characterId: Long,
    @Json(name = "name") val name: String,
    @Json(name = "corporation_id") val corporationId: Long,
    @Json(name = "alliance_id") val allianceId: Long? = null
)
