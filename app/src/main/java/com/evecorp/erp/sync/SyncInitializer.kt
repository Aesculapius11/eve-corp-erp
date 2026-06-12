package com.evecorp.erp.sync

import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.startup.Initializer
import androidx.work.Configuration
import androidx.work.WorkManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SyncWorkerEntryPoint {
    fun workerFactory(): HiltWorkerFactory
}

class SyncInitializer : Initializer<WorkManager> {
    override fun create(context: Context): WorkManager {
        val entryPoint = EntryPointAccessors.fromApplication(
            context, SyncWorkerEntryPoint::class.java
        )
        val config = Configuration.Builder()
            .setWorkerFactory(entryPoint.workerFactory())
            .build()
        WorkManager.initialize(context, config)

        // Schedule periodic sync (数据同步继续由 WorkManager 负责)
        val workManager = WorkManager.getInstance(context)
        workManager.enqueueUniquePeriodicWork(
            SyncWorker.WORK_NAME,
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            SyncWorker.createPeriodicRequest()
        )

        // AlertWorker 已移除 — KeepAliveService + AlarmManager 覆盖提醒检查
        // 三套并行机制存在竞态条件和重复通知风险

        return workManager
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
