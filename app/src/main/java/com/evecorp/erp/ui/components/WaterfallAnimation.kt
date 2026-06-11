package com.evecorp.erp.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 瀑布效果动画包装器
 * 列表项依次从下方滑入并渐现，形成瀑布效果
 */
@Composable
fun WaterfallItem(
    index: Int,
    trigger: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val animDelay = (index * 30).coerceAtMost(300)

    val alpha = remember { Animatable(0f) }
    val offsetY = remember { Animatable(20f) }
    val scale = remember { Animatable(0.97f) }

    LaunchedEffect(trigger) {
        alpha.snapTo(0f)
        offsetY.snapTo(20f)
        scale.snapTo(0.97f)
        delay(animDelay.toLong())
        launch {
            alpha.animateTo(1f, tween(200, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)))
        }
        launch {
            offsetY.animateTo(0f, tween(250, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)))
        }
        launch {
            scale.animateTo(1f, tween(200, easing = CubicBezierEasing(0.34f, 1.2f, 0.64f, 1f)))
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
 * 瀑布效果触发器
 * 每次调用 triggerWaterfall() 递增计数器，触发所有 WaterfallItem 重新播放动画
 */
@Composable
fun rememberWaterfallTrigger(): MutableState<Int> {
    return remember { mutableStateOf(0) }
}

fun MutableState<Int>.triggerWaterfall() {
    value++
}
