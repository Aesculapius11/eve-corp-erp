package com.evecorp.erp.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
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

/**
 * AlarmManager 触发的后台提醒检查接收器
 * 在 KeepAliveService 被系统杀死后，仍能通过 AlarmManager 唤醒执行提醒检查。
 * 使用 goAsync() 在后台线程执行，避免阻塞主线程导致 ANR。
 */
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

    // BroadcastReceiver.onReceive() 的 context 参数，保存为字段供 check 方法使用
    private var appContext: Context? = null

    override fun onReceive(context: Context, intent: Intent) {
        appContext = context
        val action = intent.action ?: return
        Log.d(TAG, "Received alarm: $action")

        if (!tokenManager.isLoggedIn()) {
            scheduleNextCheck(context)
            return
        }

        val corpId = tokenManager.corporationId

        // 使用 goAsync() 在后台线程执行，避免阻塞主线程导致 ANR
        val pendingResult = goAsync()
        Thread {
            try {
                runBlocking {
                    when (action) {
                        ACTION_CHECK_ALERTS -> {
                            try {
                                checkIndustryAlerts(corpId)
                                checkMarketAlerts()
                                Log.d(TAG, "Alert check completed via AlarmManager")
                            } catch (e: Exception) {
                                Log.w(TAG, "Alert check failed", e)
                            }
                        }

                        ACTION_SYNC_DATA -> {
                            try {
                                walletRepository.syncBalance(corpId)
                                walletRepository.syncJournal(corpId)
                                industryRepository.syncCostIndices(listOf(com.evecorp.erp.Constants.HAAJINEN_SYSTEM_ID))
                                industryRepository.syncJobs(corpId)
                                marketRepository.syncOrders(corpId)
                                Log.d(TAG, "Data sync completed via AlarmManager")
                            } catch (e: Exception) {
                                Log.w(TAG, "Data sync failed", e)
                            }
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }.start()

        // 调度下一次检查（在主线程快速完成）
        when (action) {
            ACTION_CHECK_ALERTS -> scheduleNextCheck(context)
            ACTION_SYNC_DATA -> scheduleNextSync(context)
        }
    }

    private fun requireContext(): Context = appContext ?: error("Context not available")

    private suspend fun checkIndustryAlerts(corpId: Long) {
        val ctx = requireContext()
        val prefs = ctx.getSharedPreferences("alert_prefs", Context.MODE_PRIVATE)
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

                if (timeLeft in (threshold - checkWindow)..threshold) {
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
                        Log.d(TAG, "Industry alert sent via AlarmManager: $alertKey")
                    }
                }
            }

            if (timeLeft < -3_600_000L) {
                for ((threshold, _) in NotificationHelper.ALERT_THRESHOLDS) {
                    prefs.edit().remove("industry_${job.jobId}_$threshold").commit()
                }
            }
        }
    }

    private suspend fun checkMarketAlerts() {
        val ctx = requireContext()
        val prefs = ctx.getSharedPreferences("alert_prefs", Context.MODE_PRIVATE)
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
            .commit()
    }

    companion object {
        private const val TAG = "AlertCheckReceiver"
        const val ACTION_CHECK_ALERTS = "com.evecorp.erp.CHECK_ALERTS"
        const val ACTION_SYNC_DATA = "com.evecorp.erp.SYNC_DATA"

        private const val KEY_MARKET_HASH = "market_orders_hash"
        private const val KEY_MARKET_ORDER_IDS = "market_order_ids"
        private const val KEY_MARKET_DETAILS = "market_order_details"

        /**
         * 使用 AlarmManager 调度下一次提醒检查
         *
         * 使用 setAlarmClock() 而非 setAndAllowWhileIdle()：
         * setAlarmClock() 是 AlarmManager 中优先级最高的类型，
         * 国产 ROM（MIUI、HarmonyOS、ColorOS、Flyme 等）对此类型
         * 几乎不做任何延迟或拦截，确保提醒准时触达。
         */
        fun scheduleNextCheck(context: Context) {
            val interval = DashboardPreferences.DEFAULT_ALERT_INTERVAL * 60_000L
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AlertCheckReceiver::class.java).apply {
                action = ACTION_CHECK_ALERTS
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, REQUEST_CODE_ALERTS, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(
                    System.currentTimeMillis() + interval,
                    // 提供一个 "闹钟" 级别的描述，让用户知道此应用有定时任务
                    PendingIntent.getActivity(
                        context, 0,
                        Intent(context, com.evecorp.erp.MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE
                    )
                ),
                pendingIntent
            )
            Log.d(TAG, "Next alert check scheduled in ${interval / 60_000} min (AlarmClock)")
        }

        /**
         * 使用 AlarmManager 调度下一次数据同步
         */
        fun scheduleNextSync(context: Context) {
            val interval = DashboardPreferences.DEFAULT_SYNC_INTERVAL * 60_000L
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AlertCheckReceiver::class.java).apply {
                action = ACTION_SYNC_DATA
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, REQUEST_CODE_SYNC, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(
                    System.currentTimeMillis() + interval,
                    PendingIntent.getActivity(
                        context, 0,
                        Intent(context, com.evecorp.erp.MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE
                    )
                ),
                pendingIntent
            )
            Log.d(TAG, "Next data sync scheduled in ${interval / 60_000} min (AlarmClock)")
        }

        /** 取消所有调度的提醒 */
        fun cancelAll(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val alertIntent = Intent(context, AlertCheckReceiver::class.java).apply { action = ACTION_CHECK_ALERTS }
            val syncIntent = Intent(context, AlertCheckReceiver::class.java).apply { action = ACTION_SYNC_DATA }
            alarmManager.cancel(PendingIntent.getBroadcast(
                context, REQUEST_CODE_ALERTS, alertIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))
            alarmManager.cancel(PendingIntent.getBroadcast(
                context, REQUEST_CODE_SYNC, syncIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))
            Log.d(TAG, "All scheduled alarms cancelled")
        }

        private const val REQUEST_CODE_ALERTS = 1001
        private const val REQUEST_CODE_SYNC = 1002
    }
}
