package com.evecorp.erp.ui.screens.dashboard

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evecorp.erp.data.local.entity.BalanceSnapshotEntity
import com.evecorp.erp.ui.theme.Sky400
import com.evecorp.erp.ui.theme.Sky500
import com.evecorp.erp.ui.theme.Sky600
import com.evecorp.erp.ui.theme.Sakura300
import com.evecorp.erp.ui.theme.Sakura400
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * 30 天资产变化折线图。
 *
 * 动画效果：
 * - Phase 1 (0-60%): 折线从左到右逐段绘制，带弹性缓动
 * - Phase 2 (40-100%): 渐变填充从底部向上淡入
 * - Phase 3 (80-100%): 数据点逐个弹出
 * - 最新数据点带 Sakura Pink 呼吸光晕
 */
@Composable
fun BalanceChart(
    snapshots: List<BalanceSnapshotEntity>,
    modifier: Modifier = Modifier
) {
    if (snapshots.isEmpty()) {
        Box(modifier = modifier.height(220.dp))
        return
    }

    val lineColor = Sky500
    val gradientStartColor = Sky400.copy(alpha = 0.3f)
    val gradientEndColor = Sky600.copy(alpha = 0.02f)
    val dotColor = Sky600
    val dotCenterColor = MaterialTheme.colorScheme.surface
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val accentDot = Sakura300
    val accentGlow = Sakura400.copy(alpha = 0.3f)

    // ── 动画：折线绘制 ──
    val lineProgress = remember { Animatable(0f) }
    // ── 动画：填充渐现 ──
    val fillProgress = remember { Animatable(0f) }
    // ── 动画：数据点弹出 ──
    val dotProgress = remember { Animatable(0f) }
    // ── 动画：最新点呼吸光晕 ──
    val glowProgress = remember { Animatable(0f) }
    // 使用数据哈希作为动画 key，避免内容不变时重播
    val dataKey = remember(snapshots) { snapshots.map { it.date to it.balance }.hashCode() }

    LaunchedEffect(dataKey) {
        // 重置所有动画
        lineProgress.snapTo(0f)
        fillProgress.snapTo(0f)
        dotProgress.snapTo(0f)

        // Phase 1: 折线绘制（0→1，800ms，弹性缓动）
        lineProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 800,
                easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
            )
        )

        // Phase 2: 填充渐现（与折线后半段重叠）
        fillProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 500,
                easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
            )
        )

        // Phase 3: 数据点弹出
        dotProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 400,
                easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f) // 弹性回弹
            )
        )

        // 持续呼吸光晕
        glowProgress.animateTo(
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        )
    }

    // 数据处理
    val today = LocalDate.now(ZoneOffset.UTC)
    val dateRange = (29 downTo 0).map { today.minusDays(it.toLong()) }
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    val dataByDate = snapshots.associate { it.date to it.balance }
    val points = dateRange.mapIndexed { index, date ->
        val dateStr = date.format(formatter)
        index.toFloat() to (dataByDate[dateStr] ?: 0.0)
    }
    val hasData = dataByDate.isNotEmpty()

    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier.height(220.dp).fillMaxWidth()) {
        val paddingLeft = 72.dp.toPx()
        val paddingRight = 16.dp.toPx()
        val paddingTop = 20.dp.toPx()
        val paddingBottom = 36.dp.toPx()

        val chartWidth = size.width - paddingLeft - paddingRight
        val chartHeight = size.height - paddingTop - paddingBottom

        if (!hasData || chartWidth <= 0) return@Canvas

        val activePoints = points.filter { it.second > 0.0 }
        if (activePoints.isEmpty()) return@Canvas

        val values = activePoints.map { it.second }
        val minVal = values.min() * 0.95
        val maxVal = values.max() * 1.05
        val range = if (maxVal > minVal) maxVal - minVal else 1.0

        fun xFor(index: Int): Float {
            val fraction = index.toFloat() / (dateRange.size - 1).coerceAtLeast(1)
            return paddingLeft + fraction * chartWidth
        }

        fun yFor(value: Double): Float {
            val fraction = ((value - minVal) / range).toFloat().coerceIn(0f, 1f)
            return paddingTop + chartHeight * (1f - fraction)
        }

        // ── 网格线（水平，4 条，虚线） ──
        for (i in 0..3) {
            val y = paddingTop + chartHeight * i / 3f
            drawLine(
                color = gridColor,
                start = Offset(paddingLeft, y),
                end = Offset(size.width - paddingRight, y),
                strokeWidth = 0.8.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
            )
            val labelValue = maxVal - (range * i / 3.0)
            val label = formatBalanceShort(labelValue)
            val result = textMeasurer.measure(
                AnnotatedString(label),
                style = TextStyle(fontSize = 10.sp, color = labelColor)
            )
            drawText(result, topLeft = Offset(4.dp.toPx(), y - result.size.height / 2f))
        }

        // ── X 轴标签（每隔 5 天） ──
        for (i in dateRange.indices step 5) {
            val x = xFor(i)
            val label = dateRange[i].format(DateTimeFormatter.ofPattern("M/d"))
            val result = textMeasurer.measure(
                AnnotatedString(label),
                style = TextStyle(fontSize = 10.sp, color = labelColor, textAlign = TextAlign.Center)
            )
            drawText(result, topLeft = Offset(x - result.size.width / 2f, size.height - paddingBottom + 10.dp.toPx()))
        }

        // ── Phase 1: 折线绘制（从左到右逐段出现） ──
        val totalPoints = activePoints.size
        val visibleCount = (totalPoints * lineProgress.value).toInt().coerceAtLeast(1)
        val partialFraction = (totalPoints * lineProgress.value) - (visibleCount - 1)

        val path = Path()
        val visiblePoints = activePoints.take(visibleCount)

        visiblePoints.forEachIndexed { idx, (indexF, value) ->
            val x = xFor(indexF.toInt())
            val y = yFor(value)
            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        // 最后一段的插值（折线正在绘制的"笔尖"效果）
        if (visibleCount < totalPoints && visibleCount > 0) {
            val lastVisible = visiblePoints.last()
            val nextPoint = activePoints[visibleCount]
            val x1 = xFor(lastVisible.first.toInt())
            val y1 = yFor(lastVisible.second)
            val x2 = xFor(nextPoint.first.toInt())
            val y2 = yFor(nextPoint.second)
            val interpX = x1 + (x2 - x1) * partialFraction.coerceIn(0f, 1f)
            val interpY = y1 + (y2 - y1) * partialFraction.coerceIn(0f, 1f)
            path.lineTo(interpX, interpY)
        }

        // ── Phase 2: 渐变填充（从底部向上淡入） ──
        if (fillProgress.value > 0f) {
            val fillPath = Path().apply {
                addPath(path)
                val lastPt = if (visibleCount < totalPoints && visibleCount > 0) {
                    val lp = visiblePoints.last()
                    val np = activePoints[visibleCount]
                    val frac = partialFraction.coerceIn(0f, 1f)
                    val lx = xFor(lp.first.toInt()) + (xFor(np.first.toInt()) - xFor(lp.first.toInt())) * frac
                    lx
                } else {
                    xFor(visiblePoints.last().first.toInt())
                }
                lineTo(lastPt, paddingTop + chartHeight)
                lineTo(xFor(visiblePoints.first().first.toInt()), paddingTop + chartHeight)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        gradientStartColor.copy(alpha = gradientStartColor.alpha * fillProgress.value),
                        gradientEndColor
                    ),
                    startY = paddingTop,
                    endY = paddingTop + chartHeight
                )
            )
        }

        // ── 折线描边 ──
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(
                width = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // ── "笔尖"光点（正在绘制的位置） ──
        if (lineProgress.value < 1f && visibleCount > 0) {
            val lastVisible = visiblePoints.last()
            val nextPoint = if (visibleCount < totalPoints) activePoints[visibleCount] else null
            if (nextPoint != null) {
                val frac = partialFraction.coerceIn(0f, 1f)
                val x = xFor(lastVisible.first.toInt()) + (xFor(nextPoint.first.toInt()) - xFor(lastVisible.first.toInt())) * frac
                val y = yFor(lastVisible.second) + (yFor(nextPoint.second) - yFor(lastVisible.second)) * frac
                // 光晕
                drawCircle(
                    color = lineColor.copy(alpha = 0.3f),
                    radius = 8.dp.toPx(),
                    center = Offset(x, y)
                )
                drawCircle(
                    color = lineColor,
                    radius = 3.dp.toPx(),
                    center = Offset(x, y)
                )
            }
        }

        // ── Phase 3: 数据点（带弹出效果） ──
        if (dotProgress.value > 0f) {
            val dotCount = (visibleCount * dotProgress.value).toInt().coerceAtLeast(0)
            visiblePoints.take(dotCount).forEachIndexed { idx, (indexF, value) ->
                val x = xFor(indexF.toInt())
                val y = yFor(value)
                val isLast = idx == visiblePoints.lastIndex && visibleCount >= totalPoints

                // 弹出缩放效果
                val scale = if (idx == dotCount - 1) {
                    // 最后一个点有弹出动画
                    val overshoot = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
                    overshoot.transform((dotProgress.value * visibleCount - idx).coerceIn(0f, 1f))
                } else 1f

                if (isLast) {
                    // 最新数据点：呼吸光晕
                    val glowRadius = 8.dp.toPx() + 4.dp.toPx() * glowProgress.value
                    drawCircle(
                        color = accentGlow,
                        radius = glowRadius * scale,
                        center = Offset(x, y)
                    )
                    drawCircle(
                        color = accentDot,
                        radius = 5.dp.toPx() * scale,
                        center = Offset(x, y)
                    )
                    drawCircle(
                        color = dotCenterColor,
                        radius = 2.5.dp.toPx() * scale,
                        center = Offset(x, y)
                    )
                } else {
                    drawCircle(
                        color = dotColor,
                        radius = 3.5.dp.toPx() * scale,
                        center = Offset(x, y)
                    )
                    drawCircle(
                        color = dotCenterColor,
                        radius = 2.dp.toPx() * scale,
                        center = Offset(x, y)
                    )
                }
            }
        }
    }
}

private fun formatBalanceShort(value: Double): String {
    return when {
        value >= 1_000_000_000 -> String.format("%.1fB", value / 1_000_000_000)
        value >= 1_000_000 -> String.format("%.0fM", value / 1_000_000)
        value >= 1_000 -> String.format("%.0fK", value / 1_000)
        else -> String.format("%.0f", value)
    }
}
