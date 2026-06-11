package com.evecorp.erp.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.evecorp.erp.Constants
import com.evecorp.erp.auth.TokenManager
import com.evecorp.erp.auth.TokenRefresher
import com.evecorp.erp.data.local.dao.TypeNameCacheDao
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
    private val tokenRefresher: TokenRefresher,
    private val walletRepository: WalletRepository,
    private val industryRepository: IndustryRepository,
    private val marketRepository: MarketRepository,
    private val typeNameCacheDao: TypeNameCacheDao
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (!tokenManager.isLoggedIn()) return Result.failure()

        val corpId = tokenManager.corporationId

        // 主动刷新 token，保持 refresh token 存活（ESI refresh token 约 4 小时过期）
        try {
            tokenRefresher.refreshBlocking()
            Log.d(TAG, "Token proactive refresh completed")
        } catch (e: Exception) {
            Log.w(TAG, "Token proactive refresh failed", e)
        }

        // 一次性清理旧的英文物品名缓存，强制重新拉取中文名
        val syncPrefs = applicationContext.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
        if (!syncPrefs.getBoolean(KEY_CACHE_CLEARED_V2, false)) {
            runCatching { typeNameCacheDao.deleteAll() }
            syncPrefs.edit().putBoolean(KEY_CACHE_CLEARED_V2, true).apply()
        }

        return try {
            coroutineScope {
                // 并行同步各模块，单个失败不影响其余
                val jobs = listOf(
                    async { runCatching { walletRepository.syncBalance(corpId) } },
                    async { runCatching { walletRepository.syncJournal(corpId) } },
                    async { runCatching { industryRepository.syncCostIndices(listOf(Constants.HAAJINEN_SYSTEM_ID)) } },
                    async { runCatching { industryRepository.syncJobs(corpId) } },
                    async { runCatching { marketRepository.syncOrders(corpId) } }
                )
                jobs.awaitAll() // 每个都 catch 了，不会互相取消
            }

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry()
            else Result.failure()
        }
    }

    companion object {
        private const val TAG = "SyncWorker"
        const val WORK_NAME = "eve_corp_sync"
        const val KEY_CACHE_CLEARED_V2 = "cache_cleared_v2_zh"

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
