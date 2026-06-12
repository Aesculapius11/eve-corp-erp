package com.evecorp.erp.notification

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.evecorp.erp.auth.TokenManager
import com.evecorp.erp.data.local.dao.IndustryJobDao
import com.evecorp.erp.data.local.dao.MarketOrderDao
import com.evecorp.erp.data.local.dao.TypeNameCacheDao
import com.evecorp.erp.util.AppUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * 提醒 Worker
 * 每 5 分钟检查一次：
 * 1. 工业作业完成/即将完成提醒
 * 2. 市场订单变化提醒
 */
@HiltWorker
class AlertWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val tokenManager: TokenManager,
    private val industryJobDao: IndustryJobDao,
    private val marketOrderDao: MarketOrderDao,
    private val typeNameCacheDao: TypeNameCacheDao,
    private val notificationHelper: NotificationHelper
) : CoroutineWorker(appContext, workerParams) {

    private val prefs: SharedPreferences by lazy {
        applicationContext.getSharedPreferences("alert_prefs", Context.MODE_PRIVATE)
    }

    override suspend fun doWork(): Result {
        if (!tokenManager.isLoggedIn()) return Result.failure()

        val corpId = tokenManager.corporationId

        return try {
            checkIndustryAlerts(corpId)
            checkMarketAlerts(corpId)
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "AlertWorker failed", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    /**
     * 检查工业作业提醒
     */
    private suspend fun checkIndustryAlerts(corpId: Long) {
        val jobs = industryJobDao.getActiveJobs(corpId).first()
        val now = System.currentTimeMillis()

        for (job in jobs) {
            val timeLeft = job.endDate - now
            val productName = job.productTypeId?.let {
                typeNameCacheDao.getName(it) ?: "产品#$it"
            } ?: "未知产品"

            // 检查是否已经发送过对应时间点的通知
            for ((threshold, label) in NotificationHelper.ALERT_THRESHOLDS) {
                val alertKey = "industry_${job.jobId}_$threshold"

                if (timeLeft in (threshold - CHECK_INTERVAL_MS)..threshold) {
                    // 在这个时间窗口内，且还没发过通知
                    if (!prefs.getBoolean(alertKey, false)) {
                        val title = if (threshold == 0L) "工业作业完成" else "工业作业即将完成"
                        val message = "$productName — ${AppUtils.getActivityLabel(job.activityType)} — 还剩$label"

                        notificationHelper.sendIndustryNotification(
                            notificationId = NotificationHelper.safeNotificationId(
                                NotificationHelper.INDUSTRY_NOTIFICATION_BASE, job.jobId
                            ),
                            title = title,
                            message = message
                        )

                        prefs.edit().putBoolean(alertKey, true).commit()
                        Log.d(TAG, "Industry alert sent: $alertKey")
                    }
                }
            }

            // 清理过期的提醒标记（作业完成后 1 小时）
            if (timeLeft < -3_600_000L) {
                cleanIndustryAlertKeys(job.jobId)
            }
        }
    }

    /**
     * 检查市场订单变化
     */
    private suspend fun checkMarketAlerts(corpId: Long) {
        val orders = marketOrderDao.getActiveOrders(corpId).first()
        val currentOrderIds = orders.map { it.orderId.toString() }.toSet()

        // 计算当前订单状态的哈希
        val currentHash = orders.joinToString("|") { "${it.orderId}:${it.volumeRemain}:${it.price}:${it.state}" }
            .let { AppUtils.md5(it) }

        val previousHash = prefs.getString(KEY_MARKET_HASH, null)

        if (previousHash != null && previousHash != currentHash) {
            // 检测到变化，分析具体变化
            val previousOrders = prefs.getStringSet(KEY_MARKET_ORDER_IDS, emptySet()) ?: emptySet()

            val newOrders = currentOrderIds - previousOrders
            val closedOrders = previousOrders - currentOrderIds

            val changes = mutableListOf<String>()

            if (newOrders.isNotEmpty()) {
                changes.add("${newOrders.size}个新订单")
            }
            if (closedOrders.isNotEmpty()) {
                changes.add("${closedOrders.size}个订单已完成/取消")
            }

            // 检查价格和数量变化
            val prevDetails = prefs.getStringSet(KEY_MARKET_ORDER_DETAILS, emptySet()) ?: emptySet()
            for (order in orders) {
                val detail = "${order.orderId}:${order.volumeRemain}:${order.price}"
                if (!prevDetails.contains(detail) && !newOrders.contains(order.orderId.toString())) {
                    val typeName = typeNameCacheDao.getName(order.typeId) ?: "物品#${order.typeId}"
                    if (order.volumeRemain < order.volumeTotal) {
                        changes.add("$typeName 已售出部分")
                    }
                }
            }

            if (changes.isNotEmpty()) {
                val message = changes.joinToString("；")
                notificationHelper.sendMarketNotification(
                    notificationId = NotificationHelper.MARKET_NOTIFICATION_BASE,
                    title = "市场单变动",
                    message = message
                )
                Log.d(TAG, "Market alert sent: $message")
            }
        }

        // 保存当前状态 — commit() 确保立即同步，避免竞态
        prefs.edit()
            .putString(KEY_MARKET_HASH, currentHash)
            .putStringSet(KEY_MARKET_ORDER_IDS, currentOrderIds)
            .putStringSet(KEY_MARKET_ORDER_DETAILS, orders.map { "${it.orderId}:${it.volumeRemain}:${it.price}" }.toSet())
            .commit()
    }

    private fun cleanIndustryAlertKeys(jobId: Long) {
        val editor = prefs.edit()
        for ((threshold, _) in NotificationHelper.ALERT_THRESHOLDS) {
            editor.remove("industry_${jobId}_$threshold")
        }
        editor.commit()
    }

    companion object {
        private const val TAG = "AlertWorker"
        const val WORK_NAME = "eve_corp_alerts"
        const val CHECK_INTERVAL_MS = 300_000L // 5 分钟

        private const val KEY_MARKET_HASH = "market_orders_hash"
        private const val KEY_MARKET_ORDER_IDS = "market_order_ids"
        private const val KEY_MARKET_ORDER_DETAILS = "market_order_details"

        fun createPeriodicRequest(): PeriodicWorkRequest {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            return PeriodicWorkRequestBuilder<AlertWorker>(
                5, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30, TimeUnit.SECONDS
                )
                .build()
        }
    }
}
