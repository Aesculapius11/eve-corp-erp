package com.evecorp.erp.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 瀑布效果动画包装器
 * 列表项依次从下方滑入并渐现，形成瀑布效果
 */
@Composable
fun WaterfallItem(
    index: Int,
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val animDelay = (index * 60).coerceAtMost(600) // 每项延迟 60ms，最大 600ms

    val alpha = remember { Animatable(0f) }
    val offsetY = remember { Animatable(40f) }
    val scale = remember { Animatable(0.95f) }

    LaunchedEffect(visible) {
        if (visible) {
            kotlinx.coroutines.delay(animDelay.toLong())
            launch {
                alpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 400,
                        easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
                    )
                )
            }
            launch {
                offsetY.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = 450,
                        easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
                    )
                )
            }
            launch {
                scale.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 400,
                        easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
                    )
                )
            }
        } else {
            alpha.snapTo(0f)
            offsetY.snapTo(40f)
            scale.snapTo(0.95f)
        }
    }

    Box(
        modifier = modifier.graphicsLayer {
            this.alpha = alpha.value
            translationY = offsetY.value
            scaleX = scale.value
            scaleY = scale.value
        }
    ) {
        content()
    }
}

/**
 * 瀑布效果状态管理
 * 用于触发列表的瀑布动画
 */
@Composable
fun rememberWaterfallState(): MutableState<Boolean> {
    return remember { mutableStateOf(false) }
}

/**
 * 触发瀑布动画刷新
 */
fun MutableState<Boolean>.triggerWaterfall() {
    value = false
    // 短暂延迟后触发，让组件先重置
    value = true
}
