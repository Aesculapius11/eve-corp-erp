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
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 30 天钱包余额折线图，带动画渐现效果。
 */
@Composable
fun BalanceChart(
    snapshots: List<BalanceSnapshotEntity>,
    modifier: Modifier = Modifier
) {
    if (snapshots.isEmpty()) {
        Box(modifier = modifier.height(200.dp))
        return
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant

    // 动画：折线渐现
    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(snapshots) {
        animProgress.snapTo(0f)
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
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

    Canvas(modifier = modifier.height(200.dp).fillMaxWidth()) {
        val paddingLeft = 80.dp.toPx()
        val paddingRight = 16.dp.toPx()
        val paddingTop = 16.dp.toPx()
        val paddingBottom = 32.dp.toPx()

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

        // 坐标映射
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
                color = outlineVariant.copy(alpha = 0.5f),
                start = Offset(paddingLeft, y),
                end = Offset(size.width - paddingRight, y),
                strokeWidth = 1.dp.toPx()
            )
            // Y 轴标签
            val labelValue = maxVal - (range * i / 3.0)
            val label = formatBalanceShort(labelValue)
            val result = textMeasurer.measure(
                AnnotatedString(label),
                style = TextStyle(fontSize = 10.sp, color = onSurfaceVariant)
            )
            drawText(result, topLeft = Offset(4.dp.toPx(), y - result.size.height / 2f))
        }

        // X 轴标签（每隔 5 天）
        for (i in dateRange.indices step 5) {
            val x = xFor(i)
            val label = dateRange[i].format(DateTimeFormatter.ofPattern("M/d"))
            val result = textMeasurer.measure(
                AnnotatedString(label),
                style = TextStyle(fontSize = 10.sp, color = onSurfaceVariant, textAlign = TextAlign.Center)
            )
            drawText(result, topLeft = Offset(x - result.size.width / 2f, size.height - paddingBottom + 8.dp.toPx()))
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
                colors = listOf(primaryColor.copy(alpha = 0.3f), Color.Transparent),
                startY = paddingTop,
                endY = paddingTop + chartHeight
            )
        )

        // 折线描边
        drawPath(
            path = path,
            color = primaryColor,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // 数据点
        visiblePoints.forEach { (indexF, value) ->
            val x = xFor(indexF.toInt())
            val y = yFor(value)
            drawCircle(color = primaryColor, radius = 3.5.dp.toPx(), center = Offset(x, y))
            drawCircle(color = surfaceVariant, radius = 2.dp.toPx(), center = Offset(x, y))
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
