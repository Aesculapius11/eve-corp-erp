package com.evecorp.erp.ui.screens.dashboard

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 30 天资产变化折线图，带动画渐现效果。
 * 使用 Sky Blue 渐变填充，Sakura Pink 数据点高亮。
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
    val gradientStartColor = Sky400.copy(alpha = 0.25f)
    val gradientEndColor = Sky600.copy(alpha = 0.02f)
    val dotColor = Sky600
    val dotCenterColor = MaterialTheme.colorScheme.surface
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val accentDot = Sakura300

    // 动画：折线渐现
    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(snapshots) {
        animProgress.snapTo(0f)
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1000, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f))
        )
    }

    // 数据处理
    val today = LocalDate.now()
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

        // 只取有数据的点
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

        // 网格线（水平，4 条）
        for (i in 0..3) {
            val y = paddingTop + chartHeight * i / 3f
            drawLine(
                color = gridColor,
                start = Offset(paddingLeft, y),
                end = Offset(size.width - paddingRight, y),
                strokeWidth = 0.8.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
            )
            // Y 轴标签
            val labelValue = maxVal - (range * i / 3.0)
            val label = formatBalanceShort(labelValue)
            val result = textMeasurer.measure(
                AnnotatedString(label),
                style = TextStyle(fontSize = 10.sp, color = labelColor)
            )
            drawText(result, topLeft = Offset(4.dp.toPx(), y - result.size.height / 2f))
        }

        // X 轴标签（每隔 5 天）
        for (i in dateRange.indices step 5) {
            val x = xFor(i)
            val label = dateRange[i].format(DateTimeFormatter.ofPattern("M/d"))
            val result = textMeasurer.measure(
                AnnotatedString(label),
                style = TextStyle(fontSize = 10.sp, color = labelColor, textAlign = TextAlign.Center)
            )
            drawText(result, topLeft = Offset(x - result.size.width / 2f, size.height - paddingBottom + 10.dp.toPx()))
        }

        // 折线路径
        val path = Path()
        val visibleCount = (activePoints.size * animProgress.value).toInt().coerceAtLeast(1)
        val visiblePoints = activePoints.take(visibleCount)

        visiblePoints.forEachIndexed { idx, (indexF, value) ->
            val x = xFor(indexF.toInt())
            val y = yFor(value)
            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        // 渐变填充
        val fillPath = Path().apply {
            addPath(path)
            val lastPoint = visiblePoints.last()
            lineTo(xFor(lastPoint.first.toInt()), paddingTop + chartHeight)
            lineTo(xFor(visiblePoints.first().first.toInt()), paddingTop + chartHeight)
            close()
        }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(gradientStartColor, gradientEndColor),
                startY = paddingTop,
                endY = paddingTop + chartHeight
            )
        )

        // 折线描边
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(
                width = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // 数据点
        visiblePoints.forEachIndexed { idx, (indexF, value) ->
            val x = xFor(indexF.toInt())
            val y = yFor(value)
            val isLast = idx == visiblePoints.lastIndex
            // 外圈
            drawCircle(
                color = if (isLast) accentDot else dotColor,
                radius = if (isLast) 5.dp.toPx() else 3.5.dp.toPx(),
                center = Offset(x, y)
            )
            // 内圈
            drawCircle(
                color = dotCenterColor,
                radius = if (isLast) 2.5.dp.toPx() else 2.dp.toPx(),
                center = Offset(x, y)
            )
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
