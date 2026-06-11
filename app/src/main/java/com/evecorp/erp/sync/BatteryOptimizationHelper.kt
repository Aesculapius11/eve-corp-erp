package com.evecorp.erp.sync

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * 电池优化白名单引导辅助类
 *
 * ─────────────────────────────────────────────────────────────
 *  国产 ROM 必杀后台 —— 唯一解是用户手动加白名单
 * ─────────────────────────────────────────────────────────────
 *
 * 即使代码层做了双进程守护 + AlarmManager + 系统事件唤醒，
 * 国产 ROM（MIUI / HarmonyOS / ColorOS / Flyme 等）仍然会
 * 在锁屏后 5-15 分钟内杀死第三方应用的后台服务。
 *
 * 唯一的解决方案：引导用户将应用加入系统的「电池优化白名单」
 * 和「自启动管理白名单」。
 *
 * ─────────────────────────────────────────────────────────────
 *  各 ROM 白名单路径
 * ─────────────────────────────────────────────────────────────
 *
 *  小米 MIUI:
 *    设置 → 应用设置 → 应用管理 → EVE Corp ERP → 省电策略 → 无限制
 *    设置 → 应用设置 → 权限管理 → 自启动 → 开启
 *
 *  华为 HarmonyOS:
 *    设置 → 应用 → 应用启动管理 → EVE Corp ERP → 关闭「自动管理」→ 全部允许
 *
 *  OPPO ColorOS:
 *    设置 → 电池 → 耗电管理 → EVE Corp ERP → 允许完全后台行为
 *    设置 → 应用管理 → 自启动管理 → 开启
 *
 *  VIVO FuntouchOS:
 *    设置 → 电池 → 后台耗电管理 → EVE Corp ERP → 允许高耗电
 *    设置 → 更多设置 → 自启动 → 开启
 *
 *  魅族 Flyme:
 *    设置 → 应用管理 → 已安装 → EVE Corp ERP → 权限管理 → 自启动 → 开启
 *
 * ─────────────────────────────────────────────────────────────
 *  使用方式
 * ─────────────────────────────────────────────────────────────
 *
 *  在设置页面中添加一个「后台保活设置」入口，点击后：
 *  1. 检查当前是否已被优化
 *  2. 如果未被加入白名单，弹出引导对话框
 *  3. 点击「去设置」跳转到对应系统设置页面
 */
object BatteryOptimizationHelper {
    private const val TAG = "BatteryOptimization"

    /**
     * 检查应用是否已被加入电池优化的白名单
     *
     * @return true = 已在白名单（无需引导），false = 需要引导用户
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check battery optimization", e)
            false
        }
    }

    /**
     * 跳转到系统电池优化设置页面，引导用户手动加入白名单
     *
     * 不同 Android 版本跳转不同页面：
     * - Android 6-8: 直接跳转电池优化白名单列表
     * - Android 9+: 部分 ROM 需要跳转应用详情页
     * - 国产 ROM: 通常需要跳转应用详情页
     */
    fun openBatteryOptimizationSettings(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open battery optimization settings", e)
            // 降级：尝试打开应用详情页
            openAppDetailsSettings(context)
        }
    }

    /**
     * 打开系统自带的应用详情页（最通用的方式）
     */
    fun openAppDetailsSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open app details", e)
        }
    }

    /**
     * 跳转到国产 ROM 的自启动管理页面
     * 各厂商的自启动管理 Activity 不同，此方法尝试依次匹配
     */
    fun openAutoStartSettings(context: Context) {
        val manufacturer = Build.MANUFACTURER.lowercase()
        Log.d(TAG, "Opening auto-start settings for $manufacturer")

        try {
            when {
                manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> {
                    // 小米自启动管理
                    val intent = Intent().apply {
                        action = "miui.intent.action.OP_AUTO_START"
                        addCategory(Intent.CATEGORY_DEFAULT)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    return
                }

                manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                    // 华为启动管理
                    val intent = Intent().apply {
                        action = "android.settings.APPLICATION_DETAILS_SETTINGS"
                        data = Uri.parse("package:${context.packageName}")
                        putExtra("package_name", context.packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    return
                }

                manufacturer.contains("oppo") || manufacturer.contains("oneplus") -> {
                    // OPPO 自启动
                    val intent = Intent().apply {
                        action = "oppo.intent.action.OPPO_MANAGER_APP_SETTINGS"
                        putExtra("packageName", context.packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    return
                }

                manufacturer.contains("vivo") -> {
                    // VIVO 自启动
                    val intent = context.packageManager.getLaunchIntentForPackage("com.iqoo.secure")
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        return
                    }
                }

                manufacturer.contains("meizu") -> {
                    // 魅族自启管理
                    val intent = Intent("com.meizu.safe.security.SHOW_APPSEC").apply {
                        putExtra("packageName", context.packageName)
                        addCategory(Intent.CATEGORY_DEFAULT)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    return
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open auto-start for $manufacturer", e)
        }

        // 降级：打开通用应用详情
        openAppDetailsSettings(context)
    }

    /**
     * 获取国产 ROM 名称（用于显示引导文字）
     */
    fun getRomName(): String {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
            manufacturer.contains("huawei") -> "华为（HarmonyOS）"
            manufacturer.contains("honor") -> "荣耀（MagicOS）"
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> "小米（MIUI/HyperOS）"
            manufacturer.contains("oppo") -> "OPPO（ColorOS）"
            manufacturer.contains("oneplus") -> "一加（ColorOS）"
            manufacturer.contains("vivo") -> "VIVO（FuntouchOS）"
            manufacturer.contains("iqoo") -> "iQOO（FuntouchOS）"
            manufacturer.contains("meizu") -> "魅族（Flyme）"
            manufacturer.contains("realme") -> "Realme（realme UI）"
            manufacturer.contains("samsung") -> "三星（One UI）"
            else -> "Android (${Build.MANUFACTURER})"
        }
    }

    /**
     * 获取当前 ROM 的白名单引导文字
     */
    fun getWhitelistGuide(): String {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") ->
                """
                ① 打开「设置」→「应用设置」→「应用管理」
                ② 找到「EVE Corp ERP」
                ③ 点击「省电策略」→ 选择「无限制」
                ④ 返回 →「自启动」→ 开启
                """.trimIndent()

            manufacturer.contains("huawei") || manufacturer.contains("honor") ->
                """
                ① 打开「设置」→「应用」→「应用启动管理」
                ② 找到「EVE Corp ERP」
                ③ 关闭「自动管理」
                ④ 在弹出的窗口中，全部三项允许：自启动、关联启动、后台活动
                """.trimIndent()

            manufacturer.contains("oppo") || manufacturer.contains("oneplus") ->
                """
                ① 打开「设置」→「电池」→「耗电管理」
                ② 找到「EVE Corp ERP」
                ③ 开启「允许完全后台行为」
                ④ 返回「设置」→「应用管理」→「自启动管理」→ 开启
                """.trimIndent()

            manufacturer.contains("vivo") || manufacturer.contains("iqoo") ->
                """
                ① 打开「设置」→「电池」→「后台耗电管理」
                ② 找到「EVE Corp ERP」→ 选择「允许高耗电」
                ③ 返回「设置」→「更多设置」→「自启动」→ 开启
                """.trimIndent()

            manufacturer.contains("meizu") ->
                """
                ① 打开「设置」→「应用管理」→「已安装应用」
                ② 找到「EVE Corp ERP」
                ③ 「权限管理」→「自启动」→ 开启
                """.trimIndent()

            else ->
                """
                ① 打开「设置」→「应用」→「应用管理」
                ② 找到「EVE Corp ERP」
                ③ 「电池」→ 选择「无限制」
                """.trimIndent()
        }
    }
}
