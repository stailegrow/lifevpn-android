package com.stailegrow.maxstrike.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stailegrow.maxstrike.ui.theme.HudType
import com.stailegrow.maxstrike.ui.theme.LocalPalette
import com.stailegrow.maxstrike.ui.theme.Palette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Верхняя панель, общая для всех трёх вкладок. Life VPN 2.0: никаких рамок
 * и вставок в духе техно-плашек — вордмарк это просто маленький фирменный
 * знак + название, ровно как в согласованном эскизе (Main.dc.html):
 * "●  Life VPN", без рамки и без фона под текстом. Статус-пилюля и кнопка
 * "+" — мягкие полупрозрачные кружки без обводки, тот же язык, что и
 * круглые иконки в шапке эскиза.
 */
@Composable
fun AppTopBar(connected: Boolean, onAddClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp),
    ) {
        AppMark(palette = palette, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(9.dp))
        Text(text = "Life VPN", style = HudType.wordmark(18.sp), color = palette.textPrimary)

        Spacer(modifier = Modifier.weight(1f))

        SecureBadge(connected = connected)
        Spacer(modifier = Modifier.width(8.dp))
        AddButton(onClick = onAddClick)
    }
}

@Composable
private fun SecureBadge(connected: Boolean) {
    val palette = LocalPalette.current
    val shape = RoundedCornerShape(50)
    val dotColor = if (connected) palette.good else palette.textSecondary

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(shape)
            .background(palette.card)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(dotColor))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (connected) "Secure" else "Offline",
            style = HudType.label(11.sp),
            color = palette.textSecondary,
        )
    }
}

@Composable
private fun AddButton(onClick: () -> Unit) {
    val palette = LocalPalette.current

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(palette.card)
            .clickable(onClick = onClick),
    ) {
        Text(text = "+", color = palette.accent, style = HudType.hero(18.sp))
    }
}

/**
 * Фирменный знак в шапке — та же "капля с разрывом", что и в иконке
 * приложения и на экране запуска, только в миниатюре и без анимации
 * (раньше здесь была условная скруглённая форма без выреза, просто
 * залитая градиентом — не узнавалась как знак бренда). Круглый вырез
 * прорезан по-настоящему (CompositingStrategy.Offscreen + BlendMode.Clear),
 * поэтому сквозь него виден фон приложения позади, как и на иконке.
 */
@Composable
private fun AppMark(palette: Palette, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val blobRadius = size.minDimension / 2f * 0.92f
            val blobPath = buildMarkPath(center, blobRadius)
            drawPath(
                blobPath,
                brush = Brush.linearGradient(listOf(palette.accentStart, palette.accentEnd)),
            )

            val holeRadius = blobRadius * 0.16f
            val holeDist = blobRadius * 0.55f
            val holeAngleRad = Math.toRadians(-55.0)
            val holeCenter = Offset(
                center.x + holeDist * cos(holeAngleRad).toFloat(),
                center.y + holeDist * sin(holeAngleRad).toFloat(),
            )
            drawCircle(
                color = Color.Black,
                radius = holeRadius,
                center = holeCenter,
                blendMode = BlendMode.Clear,
            )
        }
    }
}

private fun buildMarkPath(center: Offset, baseRadius: Float): Path {
    val points = 20
    val wobble = 0.8f
    val phase = 0.6f
    val pts = ArrayList<Offset>(points)
    for (i in 0 until points) {
        val theta = (i.toFloat() / points) * (2f * PI.toFloat())
        val r = baseRadius * (
            1f +
                wobble * 0.11f * sin(3f * theta + phase) +
                wobble * 0.06f * sin(5f * theta - phase * 2f)
            )
        pts.add(Offset(center.x + r * cos(theta), center.y + r * sin(theta)))
    }

    val path = Path()
    val first = pts[0]
    val last = pts[points - 1]
    path.moveTo((last.x + first.x) / 2f, (last.y + first.y) / 2f)
    for (i in 0 until points) {
        val cur = pts[i]
        val next = pts[(i + 1) % points]
        val midX = (cur.x + next.x) / 2f
        val midY = (cur.y + next.y) / 2f
        path.quadraticTo(cur.x, cur.y, midX, midY)
    }
    path.close()
    return path
}
