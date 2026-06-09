package com.evecorp.erp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 每日钱包余额快照，用于绘制 30 天折线图。
 * 每次 syncBalance 时写入，同一天只保留最新值。
 */
@Entity(
    tableName = "balance_snapshot",
    indices = [Index(value = ["corporationId", "date"])]
)
data class BalanceSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val corporationId: Long,
    val balance: Double,
    /** yyyy-MM-dd 格式的日期字符串，同一天去重 */
    val date: String,
    val timestamp: Long
)
