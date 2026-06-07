package com.evecorp.erp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wallet_balance")
data class WalletBalanceEntity(
    @PrimaryKey val corporationId: Long,
    val balance: Double,
    val lastUpdated: Long
)
