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

        // Schedule periodic sync
        val workManager = WorkManager.getInstance(context)
        workManager.enqueueUniquePeriodicWork(
            SyncWorker.WORK_NAME,
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            SyncWorker.createPeriodicRequest()
        )

        return workManager
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
