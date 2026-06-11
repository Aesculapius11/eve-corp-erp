package com.evecorp.erp.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.evecorp.erp.Constants
import com.evecorp.erp.MainActivity
import com.evecorp.erp.auth.TokenManager
import com.evecorp.erp.auth.TokenRefresher
import com.evecorp.erp.data.local.DashboardPreferences
import com.evecorp.erp.data.local.dao.IndustryJobDao
import com.evecorp.erp.data.local.dao.MarketOrderDao
import com.evecorp.erp.data.local.dao.TypeNameCacheDao
import com.evecorp.erp.data.repository.IndustryRepository
import com.evecorp.erp.data.repository.MarketRepository
import com.evecorp.erp.data.repository.WalletRepository
import com.evecorp.erp.notification.NotificationHelper
import com.evecorp.erp.util.AppUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * 前台保活服务
 * - 每 10 分钟主动刷新 token，保持 ESI refresh token 存活
 * - 每 5 分钟检查工业作业/市场订单变化并推送通知
 * - 显示最小化持久通知，防止系统杀死进程
 */
@AndroidEntryPoint
class KeepAliveService : Service() {

    @Inject lateinit var tokenManager: TokenManager
    @Inject lateinit var tokenRefresher: TokenRefresher
    @Inject lateinit var walletRepository: WalletRepository
    @Inject lateinit var industryRepository: IndustryRepository
    @Inject lateinit var marketRepository: MarketRepository
    @Inject lateinit var industryJobDao: IndustryJobDao
    @Inject lateinit var marketOrderDao: MarketOrderDao
    @Inject lateinit var typeNameCacheDao: TypeNameCacheDao
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var dashboardPreferences: DashboardPreferences

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tokenRefreshJob: Job? = null
    private var alertCheckJob: Job? = null
    private var dataSyncJob: Job? = null

    private val prefs by lazy {
        getSharedPreferences("alert_prefs", Context.MODE_PRIVATE)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createPersistentNotification())
        Log.d(TAG, "KeepAliveService started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startTokenRefreshLoop()
        startAlertCheckLoop()
        startDataSyncLoop()
        return START_STICKY // 被杀后自动重启
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        Log.d(TAG, "KeepAliveService destroyed")
        super.onDestroy()
    }

    /**
     * 每 10 分钟主动刷新 token
     */
    private fun startTokenRefreshLoop() {
        tokenRefreshJob?.cancel()
        tokenRefreshJob = serviceScope.launch {
            while (isActive) {
                try {
                    if (tokenManager.isLoggedIn()) {
                        tokenRefresher.refresh()
                        Log.d(TAG, "Token refreshed successfully")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Token refresh failed", e)
                }
                delay(TOKEN_REFRESH_INTERVAL)
            }
        }
    }

    /**
     * 每 N 分钟检查工业/市场变化（用户可配置）
     */
    private fun startAlertCheckLoop() {
        alertCheckJob?.cancel()
        alertCheckJob = serviceScope.launch {
            delay(60_000) // 启动后等 1 分钟再开始检查
            while (isActive) {
                try {
                    if (tokenManager.isLoggedIn()) {
                        checkIndustryAlerts()
                        checkMarketAlerts()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Alert check failed", e)
                }
                delay(dashboardPreferences.getAlertIntervalMinutes() * 60_000L)
            }
        }
    }

    /**
     * 每 N 分钟同步数据（用户可配置）
     */
    private fun startDataSyncLoop() {
        dataSyncJob?.cancel()
        dataSyncJob = serviceScope.launch {
            delay(30_000) // 启动后等 30 秒再开始同步
            while (isActive) {
                try {
                    if (tokenManager.isLoggedIn()) {
                        val corpId = tokenManager.corporationId
                        walletRepository.syncBalance(corpId)
                        walletRepository.syncJournal(corpId)
                        industryRepository.syncCostIndices(listOf(Constants.HAAJINEN_SYSTEM_ID))
                        industryRepository.syncJobs(corpId)
                        marketRepository.syncOrders(corpId)
                        Log.d(TAG, "Background data sync completed")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Background data sync failed", e)
                }
                delay(dashboardPreferences.getSyncIntervalMinutes() * 60_000L)
            }
        }
    }

    /**
     * 检查工业作业提醒
     */
    private suspend fun checkIndustryAlerts() {
        val corpId = tokenManager.corporationId
        val jobs = industryJobDao.getActiveJobs(corpId).first()
        val now = System.currentTimeMillis()

        for (job in jobs) {
            val timeLeft = job.endDate - now
            val productName = job.productTypeId?.let {
                typeNameCacheDao.getName(it) ?: "产品#$it"
            } ?: "未知产品"

            for ((threshold, label) in NotificationHelper.ALERT_THRESHOLDS) {
                val alertKey = "industry_${job.jobId}_$threshold"
                val checkInterval = dashboardPreferences.getAlertIntervalMinutes() * 60_000L

                if (timeLeft in (threshold - checkInterval)..threshold) {
                    if (!prefs.getBoolean(alertKey, false)) {
                        val title = if (threshold == 0L) "工业作业完成" else "工业作业即将完成"
                        val message = "$productName — ${AppUtils.getActivityLabel(job.activityType)} — 还剩$label"

                        notificationHelper.sendIndustryNotification(
                            notificationId = NotificationHelper.INDUSTRY_NOTIFICATION_BASE + job.jobId.toInt(),
                            title = title,
                            message = message
                        )

                        prefs.edit().putBoolean(alertKey, true).apply()
                        Log.d(TAG, "Industry alert sent: $alertKey")
                    }
                }
            }

            // 清理过期的提醒标记
            if (timeLeft < -3_600_000L) {
                for ((threshold, _) in NotificationHelper.ALERT_THRESHOLDS) {
                    prefs.edit().remove("industry_${job.jobId}_$threshold").apply()
                }
            }
        }
    }

    /**
     * 检查市场订单变化
     */
    private suspend fun checkMarketAlerts() {
        val orders = marketOrderDao.getAllActiveOrders().first()
        val currentOrderIds = orders.map { it.orderId.toString() }.toSet()
        val currentHash = orders.joinToString("|") { "${it.orderId}:${it.volumeRemain}:${it.price}:${it.state}" }
            .let { AppUtils.md5(it) }

        val previousHash = prefs.getString(KEY_MARKET_HASH, null)

        if (previousHash != null && previousHash != currentHash) {
            val previousOrders = prefs.getStringSet(KEY_MARKET_ORDER_IDS, emptySet()) ?: emptySet()
            val newOrders = currentOrderIds - previousOrders
            val closedOrders = previousOrders - currentOrderIds
            val changes = mutableListOf<String>()

            if (newOrders.isNotEmpty()) changes.add("${newOrders.size}个新订单")
            if (closedOrders.isNotEmpty()) changes.add("${closedOrders.size}个订单已完成/取消")

            val prevDetails = prefs.getStringSet(KEY_MARKET_DETAILS, emptySet()) ?: emptySet()
            for (order in orders) {
                val detail = "${order.orderId}:${order.volumeRemain}:${order.price}"
                if (!prevDetails.contains(detail) && !newOrders.contains(order.orderId.toString())) {
                    if (order.volumeRemain < order.volumeTotal) {
                        val typeName = typeNameCacheDao.getName(order.typeId) ?: "物品#${order.typeId}"
                        changes.add("$typeName 已售出部分")
                    }
                }
            }

            if (changes.isNotEmpty()) {
                notificationHelper.sendMarketNotification(
                    notificationId = NotificationHelper.MARKET_NOTIFICATION_BASE,
                    title = "市场单变动",
                    message = changes.joinToString("；")
                )
            }
        }

        prefs.edit()
            .putString(KEY_MARKET_HASH, currentHash)
            .putStringSet(KEY_MARKET_ORDER_IDS, currentOrderIds)
            .putStringSet(KEY_MARKET_DETAILS, orders.map { "${it.orderId}:${it.volumeRemain}:${it.price}" }.toSet())
            .apply()
    }


    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "后台同步", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "保持应用后台运行以同步数据和发送提醒"
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun createPersistentNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("EVE Corp ERP")
            .setContentText("后台同步中")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    companion object {
        private const val TAG = "KeepAliveService"
        private const val CHANNEL_ID = "keep_alive"
        private const val NOTIFICATION_ID = 99999

        private const val TOKEN_REFRESH_INTERVAL = 10 * 60 * 1000L  // 10 分钟

        private const val KEY_MARKET_HASH = "market_orders_hash"
        private const val KEY_MARKET_ORDER_IDS = "market_order_ids"
        private const val KEY_MARKET_DETAILS = "market_order_details"

        fun start(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java)
            context.stopService(intent)
        }
    }
}
