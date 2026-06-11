package com.evecorp.erp.ui

import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

/** 格式化 ISK 金额：千分位 + 两位小数，如 1,234,567,890.12 */
fun formatIskFull(amount: Double): String {
    val df = DecimalFormat("#,##0.00")
    return "${df.format(amount)} ISK"
}

/** 格式化 ISK 金额：简写（B/M/K），用于小卡片 */
fun formatIsk(amount: Double): String {
    return when {
        amount >= 1_000_000_000 -> "${String.format("%.2f", amount / 1_000_000_000)}B ISK"
        amount >= 1_000_000 -> "${String.format("%.1f", amount / 1_000_000)}M ISK"
        amount >= 1_000 -> "${String.format("%.0f", amount / 1_000)}K ISK"
        else -> "${String.format("%.2f", amount)} ISK"
    }
}

fun formatNumber(n: Int): String {
    return when {
        n >= 1_000_000 -> "${String.format("%.1f", n / 1_000_000.0)}M"
        n >= 1_000 -> "${String.format("%.0f", n / 1_000.0)}K"
        else -> n.toString()
    }
}

fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

fun formatTimeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3600_000 -> "${diff / 60_000} 分钟前"
        diff < 86400_000 -> "${diff / 3600_000} 小时前"
        else -> "${diff / 86400_000} 天前"
    }
}
