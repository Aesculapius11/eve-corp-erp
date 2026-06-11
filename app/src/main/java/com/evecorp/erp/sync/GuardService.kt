package com.evecorp.erp.sync

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.evecorp.erp.MainActivity
import com.evecorp.erp.auth.TokenManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

/**
 * 守护服务（独立进程 :guard）
 *
 * 国产 App 经典的双进程守护策略：
 * KeepAliveService（主进程） ↔ GuardService（:guard 进程）
 * 两个进程互相监控，如果一方被杀死，另一方立即将其重启。
 *
 * 原理：
 * - 两个服务运行在不同进程，系统无法同时杀死两个进程
 * - 每个进程通过 AlarmManager 定时检查对方是否存活
 * - 如果对方被杀死，通过 Binder 连接断开检测 + AlarmManager 重启
 *
 * Android 8+ 限制：
 * 虽然 Google 在 Android 8 开始限制后台启动，但：
 * 1. 前台服务本身不受后台启动限制（通过 startForegroundService）
 * 2. AlarmManager.setAlarmClock() 在所有 ROM 上都不受限
 * 3. 国产 ROM 对双进程的抵抗力低于 Google 原生
 */
@AndroidEntryPoint
class GuardService : Service() {

    @Inject lateinit var tokenManager: TokenManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var guardJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createPersistentNotification())
        Log.d(TAG, "GuardService started (process: guard)")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startGuardLoop()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        Log.d(TAG, "GuardService destroyed, scheduling restart")
        super.onDestroy()
        // 被杀死后通过 AlarmManager 快速重启
        scheduleRestart()
    }

    /**
     * 守护循环：定期检查 KeepAliveService 是否存活
     * 如果主进程服务被杀死，立即重启它
     */
    private fun startGuardLoop() {
        guardJob?.cancel()
        guardJob = serviceScope.launch {
            // 启动后先等一会儿，让主服务也有机会启动
            delay(INITIAL_DELAY)

            while (isActive) {
                try {
                    if (!tokenManager.isLoggedIn()) {
                        delay(CHECK_INTERVAL)
                        continue
                    }
                    // 检查主进程服务是否存活，如果没有则重启
                    if (!isMainServiceRunning()) {
                        Log.w(TAG, "KeepAliveService is dead! Restarting...")
                        KeepAliveService.start(this@GuardService)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Guard check failed", e)
                }
                delay(CHECK_INTERVAL)
            }
        }
    }

    /**
     * 检查 KeepAliveService 是否正在运行
     */
    private fun isMainServiceRunning(): Boolean {
        return try {
            val manager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
                if (KeepAliveService::class.java.name == service.service.className) {
                    return true
                }
            }
            false
        } catch (e: Exception) {
            // getRunningServices 在 Android 5+ 已废弃，部分 ROM 可能返回空
            // 保守处理：假设存活，避免误重启
            Log.w(TAG, "Failed to check service status", e)
            true
        }
    }

    private fun scheduleRestart() {
        try {
            val intent = Intent(applicationContext, GuardService::class.java)
            val pendingIntent = PendingIntent.getService(
                applicationContext, REQUEST_CODE_RESTART, intent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(
                    System.currentTimeMillis() + RESTART_DELAY,
                    PendingIntent.getActivity(
                        applicationContext, 0,
                        Intent(applicationContext, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE
                    )
                ),
                pendingIntent
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to schedule guard restart", e)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "后台守护", NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "保持应用后台服务稳定运行"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_SECRET
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
            .setContentText("后台服务运行中")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    companion object {
        private const val TAG = "GuardService"
        private const val CHANNEL_ID = "guard_service"
        private const val NOTIFICATION_ID = 88888
        private const val CHECK_INTERVAL = 30_000L          // 每 30 秒检查一次
        private const val INITIAL_DELAY = 15_000L           // 启动后等 15 秒
        private const val RESTART_DELAY = 3_000L            // 被杀后 3 秒重启
        private const val REQUEST_CODE_RESTART = 2001

        fun start(context: Context) {
            val intent = Intent(context, GuardService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, GuardService::class.java)
            context.stopService(intent)
        }
    }
}
