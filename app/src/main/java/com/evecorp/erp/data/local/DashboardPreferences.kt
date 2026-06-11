package com.evecorp.erp.data.local

import android.content.Context
import android.content.SharedPreferences
import com.evecorp.erp.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 首页偏好设置持久化（成本指数星系选择等）
 */
@Singleton
class DashboardPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("dashboard_prefs", Context.MODE_PRIVATE)

    private val _syncIntervalFlow = MutableStateFlow(getSyncIntervalMinutes())
    val syncIntervalFlow: StateFlow<Int> = _syncIntervalFlow.asStateFlow()

    private val _alertIntervalFlow = MutableStateFlow(getAlertIntervalMinutes())
    val alertIntervalFlow: StateFlow<Int> = _alertIntervalFlow.asStateFlow()

    fun getCostIndexSystemId(): Long =
        prefs.getLong(KEY_COST_INDEX_SYSTEM_ID, Constants.HAAJINEN_SYSTEM_ID)

    fun getCostIndexSystemName(): String =
        prefs.getString(KEY_COST_INDEX_SYSTEM_NAME, "吉他") ?: "吉他"

    fun setCostIndexSystem(systemId: Long, systemName: String) {
        prefs.edit()
            .putLong(KEY_COST_INDEX_SYSTEM_ID, systemId)
            .putString(KEY_COST_INDEX_SYSTEM_NAME, systemName)
            .apply()
    }

    companion object {
        private const val KEY_COST_INDEX_SYSTEM_ID = "cost_index_system_id"
        private const val KEY_COST_INDEX_SYSTEM_NAME = "cost_index_system_name"
        private const val KEY_SYNC_INTERVAL = "sync_interval_minutes"
        private const val KEY_ALERT_INTERVAL = "alert_interval_minutes"

        const val DEFAULT_SYNC_INTERVAL = 15    // 分钟
        const val DEFAULT_ALERT_INTERVAL = 5    // 分钟
        const val MIN_INTERVAL = 5              // 最小间隔
    }

    fun getSyncIntervalMinutes(): Int {
        val value = prefs.getInt(KEY_SYNC_INTERVAL, DEFAULT_SYNC_INTERVAL)
        return value.coerceAtLeast(MIN_INTERVAL)
    }

    fun setSyncIntervalMinutes(minutes: Int) {
        val coerced = minutes.coerceAtLeast(MIN_INTERVAL)
        prefs.edit().putInt(KEY_SYNC_INTERVAL, coerced).apply()
        _syncIntervalFlow.value = coerced
    }

    fun getAlertIntervalMinutes(): Int {
        val value = prefs.getInt(KEY_ALERT_INTERVAL, DEFAULT_ALERT_INTERVAL)
        return value.coerceAtLeast(MIN_INTERVAL)
    }

    fun setAlertIntervalMinutes(minutes: Int) {
        val coerced = minutes.coerceAtLeast(MIN_INTERVAL)
        prefs.edit().putInt(KEY_ALERT_INTERVAL, coerced).apply()
        _alertIntervalFlow.value = coerced
    }
}
