package com.evecorp.erp.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CharacterRolesDto(
    @Json(name = "roles") val roles: List<String> = emptyList(),
    @Json(name = "roles_at_base") val rolesAtBase: List<String> = emptyList(),
    @Json(name = "roles_at_hq") val rolesAtHq: List<String> = emptyList(),
    @Json(name = "roles_at_other") val rolesAtOther: List<String> = emptyList()
)
