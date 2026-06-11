package com.evecorp.erp.ui.components

import androidx.compose.animation.core.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import java.text.DecimalFormat

/**
 * 数字翻页动画组件
 * 金额从旧值平滑过渡到新值
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

    LaunchedEffect(targetValue) {
        val currentValue = animatable.value
        // 从0开始时拉长动画时间，让数字滚动更明显
        val duration = if (currentValue < 1f) 1500 else 800
        animatable.animateTo(
            targetValue = targetValue.toFloat(),
            animationSpec = tween(
                durationMillis = duration,
                easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
            )
        )
    }

    val df = remember { DecimalFormat("#,##0.00") }
    Text(
        text = "${df.format(animatable.value)} ISK",
        modifier = modifier,
        style = style,
        fontWeight = fontWeight,
        color = color
    )
}
