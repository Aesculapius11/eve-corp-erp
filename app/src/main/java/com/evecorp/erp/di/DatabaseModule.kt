package com.evecorp.erp.di

import android.content.Context
import androidx.room.Room
import com.evecorp.erp.data.local.dao.*
import com.evecorp.erp.data.local.database.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "eve_corp_erp.db"
        ).build()
    }

    @Provides fun provideWalletBalanceDao(db: AppDatabase): WalletBalanceDao = db.walletBalanceDao()
    @Provides fun provideWalletJournalDao(db: AppDatabase): WalletJournalDao = db.walletJournalDao()
    @Provides fun provideSystemCostIndexDao(db: AppDatabase): SystemCostIndexDao = db.systemCostIndexDao()
    @Provides fun provideIndustryJobDao(db: AppDatabase): IndustryJobDao = db.industryJobDao()
    @Provides fun provideMarketOrderDao(db: AppDatabase): MarketOrderDao = db.marketOrderDao()
    @Provides fun provideCorporationBillDao(db: AppDatabase): CorporationBillDao = db.corporationBillDao()
    @Provides fun provideCorporationDivisionDao(db: AppDatabase): CorporationDivisionDao = db.corporationDivisionDao()
    @Provides fun provideHangarItemDao(db: AppDatabase): HangarItemDao = db.hangarItemDao()
    @Provides fun provideTypeNameCacheDao(db: AppDatabase): TypeNameCacheDao = db.typeNameCacheDao()
}
