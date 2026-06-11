package com.evecorp.erp.util

import java.security.MessageDigest

/**
 * 通用工具函数
 */
object AppUtils {

    /**
     * 计算字符串的 MD5 哈希值
     */
    fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * 工业活动类型中文标签
     */
    fun getActivityLabel(activityType: String): String = when (activityType) {
        "manufacturing" -> "制造"
        "invention" -> "发明"
        "copying" -> "拷贝"
        "researching_time_efficiency" -> "时间研究"
        "researching_material_efficiency" -> "材料研究"
        else -> activityType
    }
}
