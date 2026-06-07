package com.evecorp.erp.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.evecorp.erp.auth.TokenManager
import com.evecorp.erp.data.repository.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val tokenManager: TokenManager,
    private val walletRepository: WalletRepository,
    private val industryRepository: IndustryRepository,
    private val industryJobRepository: IndustryJobRepository,
    private val marketRepository: MarketRepository,
    private val corporationBillRepository: CorporationBillRepository,
    private val hangarRepository: HangarRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (!tokenManager.isLoggedIn()) return Result.failure()

        val corpId = tokenManager.corporationId

        return try {
            // Sync in parallel where possible
            walletRepository.syncBalance(corpId)
            walletRepository.syncJournal(corpId)
            industryRepository.syncCostIndices(listOf(HAAJINEN_SYSTEM_ID))
            industryJobRepository.syncJobs(corpId)
            marketRepository.syncOrders(corpId)
            corporationBillRepository.syncBills(corpId)

            // Hangar syncs less frequently (ESI cache = 1 hour)
            val lastHangarSync = inputData.getLong(KEY_LAST_HANGAR_SYNC, 0)
            val now = System.currentTimeMillis()
            if (now - lastHangarSync > 3600_000) {
                hangarRepository.syncDivisions(corpId)
                hangarRepository.syncAssets(corpId)
            }

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry()
            else Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "eve_corp_sync"
        const val HAAJINEN_SYSTEM_ID = 30001424L
        const val KEY_LAST_HANGAR_SYNC = "last_hangar_sync"

        fun createPeriodicRequest(): PeriodicWorkRequest {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            return PeriodicWorkRequestBuilder<SyncWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30, TimeUnit.SECONDS
                )
                .build()
        }

        fun createOneTimeRequest(): OneTimeWorkRequest {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            return OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .build()
        }
    }
}
