package com.evecorp.erp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "type_name_cache")
data class TypeNameCacheEntity(
    @PrimaryKey val typeId: Long,
    val name: String,
    val groupName: String? = null
)
