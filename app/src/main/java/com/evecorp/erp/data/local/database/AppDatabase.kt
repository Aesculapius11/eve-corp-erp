package com.evecorp.erp.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.evecorp.erp.data.local.dao.*
import com.evecorp.erp.data.local.entity.*

@Database(
    entities = [
        WalletBalanceEntity::class,
        WalletJournalEntity::class,
        SystemCostIndexEntity::class,
        IndustryJobEntity::class,
        MarketOrderEntity::class,
        CorporationBillEntity::class,
        CorporationDivisionEntity::class,
        HangarItemEntity::class,
        TypeNameCacheEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun walletBalanceDao(): WalletBalanceDao
    abstract fun walletJournalDao(): WalletJournalDao
    abstract fun systemCostIndexDao(): SystemCostIndexDao
    abstract fun industryJobDao(): IndustryJobDao
    abstract fun marketOrderDao(): MarketOrderDao
    abstract fun corporationBillDao(): CorporationBillDao
    abstract fun corporationDivisionDao(): CorporationDivisionDao
    abstract fun hangarItemDao(): HangarItemDao
    abstract fun typeNameCacheDao(): TypeNameCacheDao
}
