package com.evecorp.erp.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.evecorp.erp.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannels()
    }

    private fun createChannels() {
        val industryChannel = NotificationChannel(
            CHANNEL_INDUSTRY, "工业作业提醒", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "工业作业完成或即将完成时提醒"
            enableVibration(true)
            setShowBadge(true)
        }

        val marketChannel = NotificationChannel(
            CHANNEL_MARKET, "市场订单提醒", NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "市场订单状态变化时提醒"
            setShowBadge(true)
        }

        notificationManager.createNotificationChannel(industryChannel)
        notificationManager.createNotificationChannel(marketChannel)
    }

    fun sendIndustryNotification(notificationId: Int, title: String, message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_INDUSTRY)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    fun sendMarketNotification(notificationId: Int, title: String, message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_MARKET)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    companion object {
        const val CHANNEL_INDUSTRY = "industry_alerts"
        const val CHANNEL_MARKET = "market_alerts"
        const val INDUSTRY_NOTIFICATION_BASE = 10000
        const val MARKET_NOTIFICATION_BASE = 20000
        const val ALERT_CHECK_WINDOW = 300_000L // 5分钟检查窗口
        val ALERT_THRESHOLDS = listOf(
            3_600_000L to "1小时",
            1_800_000L to "30分钟",
            300_000L to "5分钟",
            0L to "已完成"
        )
    }
}
