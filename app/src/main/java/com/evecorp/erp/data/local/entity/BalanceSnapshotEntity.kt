package com.evecorp.erp.data.local.entity

import androidx.room.Entity

/**
 * 每日钱包余额快照，用于绘制 30 天折线图。
 * 每次 syncBalance 时写入，同一天只保留最新值。
 * 主键为 (corporationId, date) 复合主键，REPLACE 策略自动按天去重。
 */
@Entity(
    tableName = "balance_snapshot",
    primaryKeys = ["corporationId", "date"]
)
data class BalanceSnapshotEntity(
    val corporationId: Long,
    val balance: Double,
    /** yyyy-MM-dd 格式的日期字符串，同一天去重 */
    val date: String,
    val timestamp: Long
)
