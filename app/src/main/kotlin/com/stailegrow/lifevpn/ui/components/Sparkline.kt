package com.stailegrow.lifevpn.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stailegrow.lifevpn.ui.theme.LocalPalette

/** Interpolates through the actual samples without overshooting adjacent values. */
@Composable
fun Sparkline(values: List<Int>, color: Color, modifier: Modifier = Modifier, strokeWidth: Dp = 1.75.dp) {
    val palette = LocalPalette.current
    Canvas(modifier = modifier) {
        val inset = 5.dp.toPx()
        val left = inset
        val right = (size.width - inset).coerceAtLeast(left)
        val top = inset
        val bottom = (size.height - inset).coerceAtLeast(top)
        for (fraction in listOf(0.5f, 1f)) {
            val y = top + (bottom - top) * fraction
            drawLine(palette.textSecondary.copy(alpha = 0.09f), Offset(left, y), Offset(right, y), 1.dp.toPx())
        }
        if (values.isEmpty()) return@Canvas
        val low = values.min().toFloat()
        val high = values.max().toFloat()
        val middle = (low + high) / 2f
        // A 1ms fluctuation should not fill the entire graph height.
        val range = maxOf(high - low, 20f, middle * 0.2f)
        val lower = (middle - range * 0.65f).coerceAtLeast(0f)
        val upper = maxOf(lower + range * 1.3f, high + range * 0.15f)
        val points = values.mapIndexed { index, value ->
            val x = if (values.size == 1) right else left + (right - left) * index / (values.size - 1)
            val y = bottom - ((value - lower) / (upper - lower)).coerceIn(0f, 1f) * (bottom - top)
            Offset(x, y)
        }
        if (points.size > 1) {
            val line = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    val a = points[i - 1]
                    val b = points[i]
                    val centerX = (a.x + b.x) / 2f
                    cubicTo(centerX, a.y, centerX, b.y, b.x, b.y)
                }
            }
            val area = Path().apply {
                addPath(line)
                lineTo(points.last().x, bottom)
                lineTo(points.first().x, bottom)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.14f), color.copy(alpha = 0f)), top, bottom))
            drawPath(line, color, style = Stroke(strokeWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        drawCircle(palette.card, 3.5.dp.toPx(), points.last())
        drawCircle(color, 2.5.dp.toPx(), points.last())
    }
}
