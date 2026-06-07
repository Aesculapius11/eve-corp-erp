package com.evecorp.erp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "wallet_journal",
    indices = [Index(value = ["corporationId", "date"])]
)
data class WalletJournalEntity(
    @PrimaryKey val id: Long,
    val corporationId: Long,
    val division: Int = 1,
    val date: Long,
    val refType: String,
    val amount: Double,
    val description: String? = null,
    val contextId: Long? = null,
    val contextIdType: String? = null
)
