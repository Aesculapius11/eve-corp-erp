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
    var isFirstLoad by remember { mutableStateOf(true) }

    LaunchedEffect(targetValue) {
        if (isFirstLoad) {
            // 首次加载：直接跳到目标值，不播放动画
            animatable.snapTo(targetValue.toFloat())
            isFirstLoad = false
        } else {
            // 后续更新：从当前值动画到新值
            animatable.animateTo(
                targetValue = targetValue.toFloat(),
                animationSpec = tween(
                    durationMillis = 800,
                    easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
                )
            )
        }
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
