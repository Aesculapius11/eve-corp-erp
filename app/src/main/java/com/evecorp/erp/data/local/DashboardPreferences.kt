package com.evecorp.erp.data.local

import android.content.Context
import android.content.SharedPreferences
import com.evecorp.erp.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
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
    }
}
