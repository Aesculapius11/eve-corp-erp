package com.evecorp.erp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "market_order",
    indices = [Index(value = ["corporationId", "state"])]
)
data class MarketOrderEntity(
    @PrimaryKey val orderId: Long,
    val corporationId: Long,
    val typeId: Long,
    val locationId: Long,
    val isBuyOrder: Boolean,
    val price: Double,
    val volumeTotal: Int,
    val volumeRemain: Int,
    val issued: Long,
    val duration: Int,
    val state: String,
    val minVolume: Int = 1,
    val range: String? = null,
    val issuedBy: Long? = null
)
