package com.evecorp.erp.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.evecorp.erp.auth.TokenManager
import com.evecorp.erp.notification.AlertCheckReceiver
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 系统事件广播接收器
 *
 * 国产 App 保活的核心策略之一：监听系统事件触发自行唤醒。
 * 当 KeepAliveService 被系统杀死后，这些广播能重新拉起服务。
 *
 * 监听的系统事件：
 * - 开机完成 → 应用重启后自动恢复后台服务
 * - 网络变化 → 从无网到有网时重新连接
 * - 屏幕点亮 → 用户拿起手机时恢复服务
 * - 日期变更/时区变更 → 每日轮询触达
 * - 应用更新/替换 → 更新后重新启动服务
 */
@AndroidEntryPoint
class SystemEventReceiver : BroadcastReceiver() {

    @Inject lateinit var tokenManager: TokenManager

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "System event received: $action")

        if (!tokenManager.isLoggedIn()) return

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                // 开机后启动保活服务和 AlarmManager
                Log.i(TAG, "Device booted, starting background services")
                KeepAliveService.start(context)
                AlertCheckReceiver.scheduleNextCheck(context)
                AlertCheckReceiver.scheduleNextSync(context)
            }

            Intent.ACTION_POWER_CONNECTED,
            Intent.ACTION_POWER_DISCONNECTED -> {
                // 充电状态变化 — 此时系统通常不会杀进程，趁机确保服务运行
                Log.d(TAG, "Power state changed, ensuring service is running")
                KeepAliveService.start(context)
            }

            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                // 日期/时区变更 — 重置 AlarmManager 确保闹钟正确
                Log.d(TAG, "Date/time changed, rescheduling alarms")
                AlertCheckReceiver.cancelAll(context)
                AlertCheckReceiver.scheduleNextCheck(context)
                AlertCheckReceiver.scheduleNextSync(context)
            }

            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                // 应用更新后重新启动服务
                Log.i(TAG, "App updated, restarting background services")
                KeepAliveService.start(context)
                AlertCheckReceiver.scheduleNextCheck(context)
                AlertCheckReceiver.scheduleNextSync(context)
            }

            Intent.ACTION_USER_PRESENT -> {
                // 用户解锁屏幕 — 此时系统不杀进程，确保服务存活
                Log.d(TAG, "User unlocked device, ensuring service is running")
                KeepAliveService.start(context)
            }
        }
    }

    companion object {
        private const val TAG = "SystemEventReceiver"
    }
}
