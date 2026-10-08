package cn.yausen.notaledger.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One slice of the ring chart. */
data class ChartSlice(
    val label: String,
    val value: Double,
    val color: Color,
)

/**
 * Ring chart of spending by category. The centre shows the total, which is the
 * number a person actually wants to read off it.
 */
@Composable
fun DonutChart(
    slices: List<ChartSlice>,
    centerLabel: String,
    centerValue: String,
    modifier: Modifier = Modifier,
    ringSize: Int = 168,
    thickness: Float = 26f,
) {
    val total = slices.sumOf { it.value }.takeIf { it > 0.0 } ?: 0.0
    val animated by animateFloatAsState(
        targetValue = if (total > 0.0) 1f else 0f,
        animationSpec = tween(durationMillis = 550),
        label = "donut",
    )
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant

    Box(modifier = modifier.size(ringSize.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(ringSize.dp)) {
            val strokeWidth = thickness
            val inset = strokeWidth / 2f
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(inset, inset)

            if (total <= 0.0) {
                drawArc(
                    color = emptyColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth),
                )
                return@Canvas
            }

            var startAngle = -90f
            slices.forEach { slice ->
                val sweep = (slice.value / total * 360.0).toFloat() * animated
                if (sweep > 0f) {
                    drawArc(
                        color = slice.color,
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth),
                    )
                }
                startAngle += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = centerLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = centerValue,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** One bar of the trend chart. */
data class BarEntry(
    val label: String,
    val value: Double,
    val color: Color? = null,
    val highlighted: Boolean = false,
)

/**
 * Simple column chart. Values are relative to the largest bar, so the shape of
 * the month is readable without axis furniture.
 */
@Composable
fun BarChart(
    entries: List<BarEntry>,
    modifier: Modifier = Modifier,
    barHeight: Int = 120,
    showValues: Boolean = false,
    valueFormatter: (Double) -> String = { it.toInt().toString() },
) {
    if (entries.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(barHeight.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "暂无数据",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val max = entries.maxOf { it.value }.takeIf { it > 0.0 } ?: 1.0
    val accent = MaterialTheme.colorScheme.primary
    val onMuted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            entries.forEach { entry ->
                val ratio = (entry.value / max).toFloat()
                val animated by animateFloatAsState(
                    targetValue = ratio,
                    animationSpec = tween(durationMillis = 450),
                    label = "bar-${entry.label}",
                )
                val drawnHeight = ((barHeight - 16) * animated).coerceAtLeast(2f)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    if (showValues && entry.value > 0) {
                        Text(
                            text = valueFormatter(entry.value),
                            fontSize = 9.sp,
                            color = onMuted,
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(2.dp))
                    }
                    Canvas(
                        modifier = Modifier
                            .widthIn(max = 40.dp)
                            .fillMaxWidth()
                            .height(drawnHeight.dp),
                    ) {
                        val barColor = entry.color
                            ?: if (entry.highlighted) accent else accent.copy(alpha = 0.55f)
                        drawRoundRect(
                            color = if (entry.value > 0) barColor else Color.Transparent,
                            cornerRadius = CornerRadius(
                                x = size.width / 2.5f,
                                y = size.width / 2.5f,
                            ),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            entries.forEach { entry ->
                Text(
                    text = entry.label,
                    fontSize = 9.sp,
                    color = if (entry.highlighted) accent else onMuted,
                    fontWeight = if (entry.highlighted) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 1.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Legend row: the color dot matching a ring slice, the category, its amount and
 * its share of the total.
 */
@Composable
fun LegendRow(
    label: String,
    value: String,
    share: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(modifier = Modifier.size(10.dp)) {
            drawCircle(color = color)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "${(share * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
