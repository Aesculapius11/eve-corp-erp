package com.evecorp.erp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "corporation_bill")
data class CorporationBillEntity(
    @PrimaryKey val billId: Long,
    val corporationId: Long,
    val billType: String,
    val amount: Double,
    val dueDate: Long,
    val issuerId: Long,
    val paid: Boolean,
    val lastUpdated: Long
)
