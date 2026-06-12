package com.evecorp.erp.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.evecorp.erp.Constants
import com.evecorp.erp.auth.TokenManager
import com.evecorp.erp.data.local.DashboardPreferences
import com.evecorp.erp.data.local.dao.IndustryJobDao
import com.evecorp.erp.data.local.dao.MarketOrderDao
import com.evecorp.erp.data.local.dao.TypeNameCacheDao
import com.evecorp.erp.data.repository.IndustryRepository
import com.evecorp.erp.data.repository.MarketRepository
import com.evecorp.erp.data.repository.WalletRepository
import com.evecorp.erp.util.AppUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class AlertCheckReceiver : BroadcastReceiver() {

    @Inject lateinit var tokenManager: TokenManager
    @Inject lateinit var industryJobDao: IndustryJobDao
    @Inject lateinit var marketOrderDao: MarketOrderDao
    @Inject lateinit var typeNameCacheDao: TypeNameCacheDao
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var dashboardPreferences: DashboardPreferences
    @Inject lateinit var walletRepository: WalletRepository
    @Inject lateinit var industryRepository: IndustryRepository
    @Inject lateinit var marketRepository: MarketRepository

    private var appContext: Context? = null

    override fun onReceive(context: Context, intent: Intent) {
        appContext = context.applicationContext
        val action = intent.action ?: return
        Log.d(TAG, "Received alarm: $action")

        if (!tokenManager.isLoggedIn()) {
            when (action) {
                ACTION_CHECK_ALERTS -> scheduleNextCheck(context)
                ACTION_SYNC_DATA -> scheduleNextSync(context)
            }
            return
        }

        val corpId = tokenManager.corporationId
        val pendingResult = goAsync()
        Thread {
            try {
                runBlocking {
                    when (action) {
                        ACTION_CHECK_ALERTS -> {
                            checkIndustryAlerts(corpId)
                            checkMarketAlerts(corpId)
                            Log.d(TAG, "Alert check completed via AlarmManager")
                        }

                        ACTION_SYNC_DATA -> {
                            walletRepository.syncBalance(corpId)
                            walletRepository.syncJournal(corpId)
                            industryRepository.syncCostIndices(listOf(Constants.HAAJINEN_SYSTEM_ID))
                            industryRepository.syncJobs(corpId)
                            marketRepository.syncOrders(corpId)
                            checkIndustryAlerts(corpId)
                            checkMarketAlerts(corpId)
                            Log.d(TAG, "Data sync completed via AlarmManager")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Alarm action failed: $action", e)
            } finally {
                pendingResult.finish()
            }
        }.start()

        when (action) {
            ACTION_CHECK_ALERTS -> scheduleNextCheck(context)
            ACTION_SYNC_DATA -> scheduleNextSync(context)
        }
    }

    private fun requireContext(): Context = appContext ?: error("Context not available")

    private suspend fun checkIndustryAlerts(corpId: Long) {
        val prefs = requireContext().getSharedPreferences(ALERT_PREFS_NAME, Context.MODE_PRIVATE)
        val jobs = industryJobDao.getActiveJobs(corpId).first()
        val now = System.currentTimeMillis()
        val checkWindow = dashboardPreferences.getAlertIntervalMinutes() * 60_000L

        for (job in jobs) {
            val timeLeft = job.endDate - now
            val productName = job.productTypeId?.let {
                typeNameCacheDao.getName(it) ?: "产品#$it"
            } ?: "未知产品"

            for ((threshold, label) in NotificationHelper.ALERT_THRESHOLDS) {
                val alertKey = "industry_${job.jobId}_$threshold"
                if (timeLeft in (threshold - checkWindow)..threshold && !prefs.getBoolean(alertKey, false)) {
                    val title = if (threshold == 0L) "工业作业完成" else "工业作业即将完成"
                    val message = "$productName - ${AppUtils.getActivityLabel(job.activityType)} - 还剩$label"

                    notificationHelper.sendIndustryNotification(
                        notificationId = NotificationHelper.safeNotificationId(
                            NotificationHelper.INDUSTRY_NOTIFICATION_BASE,
                            job.jobId
                        ),
                        title = title,
                        message = message
                    )

                    prefs.edit().putBoolean(alertKey, true).commit()
                    Log.d(TAG, "Industry alert sent: $alertKey")
                }
            }

            if (timeLeft < -3_600_000L) {
                for ((threshold, _) in NotificationHelper.ALERT_THRESHOLDS) {
                    prefs.edit().remove("industry_${job.jobId}_$threshold").commit()
                }
            }
        }
    }

    private suspend fun checkMarketAlerts(corpId: Long) {
        val prefs = requireContext().getSharedPreferences(ALERT_PREFS_NAME, Context.MODE_PRIVATE)
        val orders = marketOrderDao.getActiveOrders(corpId).first()
        val currentOrderIds = orders.map { it.orderId.toString() }.toSet()
        val currentHash = orders.joinToString("|") {
            "${it.orderId}:${it.volumeRemain}:${it.price}:${it.state}"
        }.let(AppUtils::md5)

        val previousHash = prefs.getString(KEY_MARKET_HASH, null)
        if (previousHash != null && previousHash != currentHash) {
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

            val prevDetails = prefs.getStringSet(KEY_MARKET_DETAILS, emptySet()) ?: emptySet()
            for (order in orders) {
                val detail = "${order.orderId}:${order.volumeRemain}:${order.price}"
                if (!prevDetails.contains(detail) &&
                    !newOrders.contains(order.orderId.toString()) &&
                    order.volumeRemain < order.volumeTotal
                ) {
                    val typeName = typeNameCacheDao.getName(order.typeId) ?: "物品#${order.typeId}"
                    changes.add("$typeName 已成交部分")
                }
            }

            if (changes.isNotEmpty()) {
                notificationHelper.sendMarketNotification(
                    notificationId = NotificationHelper.MARKET_NOTIFICATION_BASE,
                    title = "市场订单变动",
                    message = changes.joinToString("；")
                )
            }
        }

        prefs.edit()
            .putString(KEY_MARKET_HASH, currentHash)
            .putStringSet(KEY_MARKET_ORDER_IDS, currentOrderIds)
            .putStringSet(KEY_MARKET_DETAILS, orders.map {
                "${it.orderId}:${it.volumeRemain}:${it.price}"
            }.toSet())
            .commit()
    }

    companion object {
        private const val TAG = "AlertCheckReceiver"
        const val ACTION_CHECK_ALERTS = "com.evecorp.erp.CHECK_ALERTS"
        const val ACTION_SYNC_DATA = "com.evecorp.erp.SYNC_DATA"

        private const val ALERT_PREFS_NAME = "alert_prefs"
        private const val DASHBOARD_PREFS_NAME = "dashboard_prefs"
        private const val KEY_MARKET_HASH = "market_orders_hash"
        private const val KEY_MARKET_ORDER_IDS = "market_order_ids"
        private const val KEY_MARKET_DETAILS = "market_order_details"
        private const val KEY_SYNC_INTERVAL = "sync_interval_minutes"
        private const val KEY_ALERT_INTERVAL = "alert_interval_minutes"
        private const val REQUEST_CODE_ALERTS = 1001
        private const val REQUEST_CODE_SYNC = 1002
        private const val MIN_INTERVAL = 5

        fun scheduleNextCheck(context: Context) {
            val interval = getIntervalMillis(
                context = context,
                key = KEY_ALERT_INTERVAL,
                defaultMinutes = DashboardPreferences.DEFAULT_ALERT_INTERVAL
            )
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_ALERTS,
                Intent(context, AlertCheckReceiver::class.java).apply {
                    action = ACTION_CHECK_ALERTS
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            scheduleExactAlarm(
                alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager,
                triggerAtMillis = System.currentTimeMillis() + interval,
                pendingIntent = pendingIntent
            )
            Log.d(TAG, "Next alert check scheduled in ${interval / 60_000} min")
        }

        fun scheduleNextSync(context: Context) {
            val interval = getIntervalMillis(
                context = context,
                key = KEY_SYNC_INTERVAL,
                defaultMinutes = DashboardPreferences.DEFAULT_SYNC_INTERVAL
            )
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_SYNC,
                Intent(context, AlertCheckReceiver::class.java).apply {
                    action = ACTION_SYNC_DATA
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            scheduleExactAlarm(
                alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager,
                triggerAtMillis = System.currentTimeMillis() + interval,
                pendingIntent = pendingIntent
            )
            Log.d(TAG, "Next data sync scheduled in ${interval / 60_000} min")
        }

        fun cancelAll(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val alertIntent = Intent(context, AlertCheckReceiver::class.java).apply {
                action = ACTION_CHECK_ALERTS
            }
            val syncIntent = Intent(context, AlertCheckReceiver::class.java).apply {
                action = ACTION_SYNC_DATA
            }
            alarmManager.cancel(
                PendingIntent.getBroadcast(
                    context,
                    REQUEST_CODE_ALERTS,
                    alertIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            alarmManager.cancel(
                PendingIntent.getBroadcast(
                    context,
                    REQUEST_CODE_SYNC,
                    syncIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            Log.d(TAG, "All scheduled alarms cancelled")
        }

        private fun getIntervalMillis(context: Context, key: String, defaultMinutes: Int): Long {
            val prefs = context.getSharedPreferences(DASHBOARD_PREFS_NAME, Context.MODE_PRIVATE)
            val minutes = prefs.getInt(key, defaultMinutes).coerceAtLeast(MIN_INTERVAL)
            return minutes * 60_000L
        }

        private fun scheduleExactAlarm(
            alarmManager: AlarmManager,
            triggerAtMillis: Long,
            pendingIntent: PendingIntent
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        }
    }
}
