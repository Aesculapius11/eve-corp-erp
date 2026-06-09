package com.evecorp.erp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 机库物品实体。
 * 不使用外键约束，避免 syncDivisions.deleteAll() 级联删除所有资产数据。
 * 数据一致性由 Repository 层的同步顺序保证。
 */
@Entity(
    tableName = "hangar_item",
    indices = [Index(value = ["divisionId"])]
)
data class HangarItemEntity(
    @PrimaryKey val itemId: Long,
    val divisionId: Long,
    val typeId: Long,
    val quantity: Int,
    val locationFlag: String
)
