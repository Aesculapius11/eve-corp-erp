package com.evecorp.erp.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import java.text.DecimalFormat

/**
 * 数字增长动画组件
 * - 始终单行显示，字号自适应避免换行跳动
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
            val startValue = animatable.value * 0.9f
            animatable.snapTo(startValue)
            val diff = kotlin.math.abs(targetValue.toFloat() - startValue)
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
    val displayText = "${df.format(animatable.value.toDouble())} ISK"

    // 根据目标值长度动态计算字号，确保始终单行
    val baseFontSize = style.fontSize.value
    val targetText = "${df.format(targetValue)} ISK"
    // 估算：每增加约 5 个字符，字号缩小 2sp
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
