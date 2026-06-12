package com.evecorp.erp.data.local

import android.content.Context
import android.content.SharedPreferences
import com.evecorp.erp.Constants
import com.evecorp.erp.sync.RefreshDomain
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

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

    private val _dashboardLastRefreshAtFlow =
        MutableStateFlow(getLastRefreshAt(RefreshDomain.DASHBOARD))
    val dashboardLastRefreshAtFlow: StateFlow<Long> = _dashboardLastRefreshAtFlow.asStateFlow()

    fun getCostIndexSystemId(): Long =
        prefs.getLong(KEY_COST_INDEX_SYSTEM_ID, Constants.HAAJINEN_SYSTEM_ID)

    fun getCostIndexSystemName(): String =
        prefs.getString(KEY_COST_INDEX_SYSTEM_NAME, "Jita") ?: "Jita"

    fun setCostIndexSystem(systemId: Long, systemName: String) {
        prefs.edit()
            .putLong(KEY_COST_INDEX_SYSTEM_ID, systemId)
            .putString(KEY_COST_INDEX_SYSTEM_NAME, systemName)
            .apply()
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

    fun getLastRefreshAt(domain: RefreshDomain): Long =
        prefs.getLong(lastRefreshKey(domain), 0L)

    fun setLastRefreshAt(domain: RefreshDomain, timestamp: Long) {
        prefs.edit().putLong(lastRefreshKey(domain), timestamp).apply()
        if (domain == RefreshDomain.DASHBOARD) {
            _dashboardLastRefreshAtFlow.value = timestamp
        }
    }

    private fun lastRefreshKey(domain: RefreshDomain): String = when (domain) {
        RefreshDomain.DASHBOARD -> KEY_DASHBOARD_LAST_REFRESH_AT
        RefreshDomain.INDUSTRY -> KEY_INDUSTRY_LAST_REFRESH_AT
        RefreshDomain.MARKET -> KEY_MARKET_LAST_REFRESH_AT
    }

    companion object {
        private const val KEY_COST_INDEX_SYSTEM_ID = "cost_index_system_id"
        private const val KEY_COST_INDEX_SYSTEM_NAME = "cost_index_system_name"
        private const val KEY_SYNC_INTERVAL = "sync_interval_minutes"
        private const val KEY_ALERT_INTERVAL = "alert_interval_minutes"
        private const val KEY_DASHBOARD_LAST_REFRESH_AT = "dashboard_last_refresh_at"
        private const val KEY_INDUSTRY_LAST_REFRESH_AT = "industry_last_refresh_at"
        private const val KEY_MARKET_LAST_REFRESH_AT = "market_last_refresh_at"

        const val DEFAULT_SYNC_INTERVAL = 15
        const val DEFAULT_ALERT_INTERVAL = 5
        const val MIN_INTERVAL = 5
    }
}
