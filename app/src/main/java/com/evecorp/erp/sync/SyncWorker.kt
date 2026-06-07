package com.evecorp.erp.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.evecorp.erp.Constants
import com.evecorp.erp.auth.TokenManager
import com.evecorp.erp.data.repository.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.TimeUnit

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val tokenManager: TokenManager,
    private val walletRepository: WalletRepository,
    private val industryRepository: IndustryRepository,
    private val marketRepository: MarketRepository,
    private val corporationBillRepository: CorporationBillRepository,
    private val hangarRepository: HangarRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (!tokenManager.isLoggedIn()) return Result.failure()

        val corpId = tokenManager.corporationId

        return try {
            coroutineScope {
                // 并行同步各模块
                val jobs = listOf(
                    async { walletRepository.syncBalance(corpId) },
                    async { walletRepository.syncJournal(corpId) },
                    async { industryRepository.syncCostIndices(listOf(Constants.HAAJINEN_SYSTEM_ID)) },
                    async { industryRepository.syncJobs(corpId) },
                    async { marketRepository.syncOrders(corpId) },
                    async { corporationBillRepository.syncBills(corpId) }
                )
                jobs.awaitAll()

                // Hangar syncs less frequently (ESI cache = 1 hour)
                val lastHangarSync = inputData.getLong(KEY_LAST_HANGAR_SYNC, 0)
                val now = System.currentTimeMillis()
                if (now - lastHangarSync > 3600_000) {
                    hangarRepository.syncDivisions(corpId)
                    hangarRepository.syncAssets(corpId)
                }
            }

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry()
            else Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "eve_corp_sync"

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
