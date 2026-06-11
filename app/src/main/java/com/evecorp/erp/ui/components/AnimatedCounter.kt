package com.evecorp.erp.ui.components

import androidx.compose.animation.core.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import java.text.DecimalFormat
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * 数字增长动画组件
 * - 首次加载：从 0 开始增长
 * - 后续更新：从旧值的 90% 开始往新值增长
 * - 速度先快后慢（FastOutSlowIn）
 */
@Composable
fun AnimatedCounter(
    targetValue: Double,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    fontWeight: FontWeight? = null,
    color: Color = Color.Unspecified
) {
    val animatable = remember { Animatable(0f) }
    var isFirstLoad by remember { mutableStateOf(true) }

    LaunchedEffect(targetValue) {
        if (isFirstLoad) {
            // 首次：从 0 增长到目标值
            isFirstLoad = false
            animatable.snapTo(0f)
            animatable.animateTo(
                targetValue = targetValue.toFloat(),
                animationSpec = tween(
                    durationMillis = 1200,
                    easing = FastOutSlowInEasing
                )
            )
        } else {
            // 后续：从旧值的 90% 开始增长
            val startValue = animatable.value * 0.9f
            animatable.snapTo(startValue)
            // 根据变化幅度调整动画时长
            val diff = abs(targetValue.toFloat() - startValue)
            val duration = (400 + (diff / targetValue.toFloat()) * 800)
                .toInt().coerceIn(400, 1200)
            animatable.animateTo(
                targetValue = targetValue.toFloat(),
                animationSpec = tween(
                    durationMillis = duration,
                    easing = FastOutSlowInEasing
                )
            )
        }
    }

    val df = remember { DecimalFormat("#,##0.00") }
    val displayValue = animatable.value.toDouble().roundToLong() + 
        (animatable.value - animatable.value.toLong()) // 保留小数部分
    Text(
        text = "${df.format(animatable.value.toDouble())} ISK",
        modifier = modifier,
        style = style,
        fontWeight = fontWeight,
        color = color
    )
}
