package com.evecorp.erp.sync

enum class RefreshDomain {
    DASHBOARD,
    INDUSTRY,
    MARKET
}

object EsiRefreshPolicy {
    const val MIN_REFRESH_INTERVAL_MINUTES = 5
    const val MIN_REFRESH_INTERVAL_MILLIS = MIN_REFRESH_INTERVAL_MINUTES * 60_000L
    const val EXCESSIVE_TAP_THRESHOLD = 3

    fun manualRemainingMillis(
        lastRefreshAt: Long,
        now: Long = System.currentTimeMillis()
    ): Long {
        if (lastRefreshAt <= 0L) return 0L
        return (lastRefreshAt + MIN_REFRESH_INTERVAL_MILLIS - now).coerceAtLeast(0L)
    }

    fun automaticRemainingMillis(
        lastRefreshAt: Long,
        configuredIntervalMinutes: Int,
        now: Long = System.currentTimeMillis()
    ): Long {
        if (lastRefreshAt <= 0L) return 0L
        val intervalMillis =
            maxOf(configuredIntervalMinutes, MIN_REFRESH_INTERVAL_MINUTES) * 60_000L
        return (lastRefreshAt + intervalMillis - now).coerceAtLeast(0L)
    }

    fun formatRemaining(remainingMillis: Long): String {
        if (remainingMillis <= 0L) return "0秒"
        val totalSeconds = (remainingMillis + 999L) / 1000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return if (minutes > 0L) "${minutes}分${seconds}秒" else "${seconds}秒"
    }
}
