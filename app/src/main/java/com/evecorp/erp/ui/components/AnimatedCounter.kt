package com.evecorp.erp.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import java.text.DecimalFormat

/**
 * 数字增长动画组件
 * - 始终单行显示，字号自适应避免换行跳动
 * - 只在值真正变化时播放动画（避免 Room Flow 重复发射触发）
 * - 首次加载：从 0 动画到目标值
 * - 后续变化：从旧值动画到新值，先快后慢
 * - 修复：滚动离开再回来不会重复播放动画
 */
@Composable
fun AnimatedCounter(
    targetValue: Double,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    fontWeight: FontWeight? = null,
    color: Color = Color.Unspecified
) {
    // 使用 rememberSaveable 跟踪动画完成状态，防止滚动回收后重复播放
    var hasAnimated by rememberSaveable { mutableStateOf(false) }
    var lastCompletedValue by rememberSaveable { mutableStateOf(targetValue) }
    val animatable = remember {
        Animatable(
            if (hasAnimated && lastCompletedValue == targetValue) targetValue.toFloat()
            else 0f
        )
    }

    LaunchedEffect(targetValue) {
        // 如果已经对此数值完成过动画（如滚动回来），直接跳转不播放
        if (hasAnimated && lastCompletedValue == targetValue) {
            animatable.snapTo(targetValue.toFloat())
            return@LaunchedEffect
        }

        val current = animatable.value
        // 从当前值动画到目标值，变化幅度大时长更长
        val diff = kotlin.math.abs(targetValue.toFloat() - current)
        val duration = if (current < 1f) 2000 else (500 + (diff / targetValue.toFloat().coerceAtLeast(1f)) * 1000)
            .toInt().coerceIn(500, 2500)
        animatable.animateTo(
            targetValue = targetValue.toFloat(),
            animationSpec = tween(
                durationMillis = duration,
                easing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
            )
        )
        // 标记动画已完成
        hasAnimated = true
        lastCompletedValue = targetValue
    }

    val df = remember { DecimalFormat("#,##0.00") }
    val displayText = "${df.format(animatable.value.toDouble())} ISK"

    // 根据目标值长度动态计算字号，确保始终单行
    val baseFontSize = style.fontSize.value
    val targetText = "${df.format(targetValue)} ISK"
    val lengthDiff = (targetText.length - 15).coerceAtLeast(0)
    val adjustedFontSize = (baseFontSize - lengthDiff * 1.2f).coerceAtLeast(18f)

    Text(
        text = displayText,
        modifier = modifier.fillMaxWidth(),
        style = style.copy(fontSize = adjustedFontSize.sp),
        fontWeight = fontWeight,
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis
    )
}
