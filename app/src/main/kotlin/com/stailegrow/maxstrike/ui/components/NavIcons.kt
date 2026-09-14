package com.stailegrow.maxstrike.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp

/**
 * Три значка нижней навигации — раньше рисовались тонкими "техническими"
 * обводками (дом из прямых линий, полосатый гараж-гейт вместо шестерёнки),
 * что не вязалось с мягким, безрамочным, заливным языком остального
 * интерфейса 2.0. Теперь все три — сплошные закруглённые заливки без единой
 * обводки, в едином стиле с остальным приложением.
 */
@Composable
fun HomeIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(22.dp)) {
        val w = size.width
        val h = size.height

        // Крыша — треугольник со скруглённой вершиной и скруглёнными
        // нижними углами (через quadraticTo), а не острые прямые линии.
        val roofTip = 0.06f
        val roof = Path().apply {
            moveTo(w * 0.10f, h * 0.50f)
            quadraticTo(w * 0.10f, h * 0.40f, w * 0.18f, h * 0.34f)
            lineTo(w * 0.44f, h * roofTip + h * 0.06f)
            quadraticTo(w * 0.5f, h * roofTip, w * 0.56f, h * roofTip + h * 0.06f)
            lineTo(w * 0.82f, h * 0.34f)
            quadraticTo(w * 0.90f, h * 0.40f, w * 0.90f, h * 0.50f)
            lineTo(w * 0.74f, h * 0.50f)
            lineTo(w * 0.5f, h * 0.30f)
            lineTo(w * 0.26f, h * 0.50f)
            close()
        }
        drawPath(roof, color = color)

        // Стены — единая заливка со скруглёнными нижними углами.
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.22f, h * 0.46f),
            size = androidx.compose.ui.geometry.Size(w * 0.56f, h * 0.44f),
            cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
        )

        // "Дверь"-вырез мягкого тона — маленький прозрачный акцент не
        // рисуем (чтобы не завязываться на цвет фона), вместо этого —
        // маленький кружок-акцент чуть светлее у основания.
        drawCircle(
            color = Color.White.copy(alpha = 0.55f),
            radius = w * 0.045f,
            center = Offset(w * 0.5f, h * 0.78f),
        )
    }
}

@Composable
fun ServersIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(22.dp)) {
        val w = size.width
        val barHeight = size.height * 0.20f
        val rowTops = listOf(0.10f, 0.40f, 0.70f)
        val rowWidths = listOf(0.88f, 0.72f, 0.56f)
        rowTops.forEachIndexed { index, top ->
            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.06f, size.height * top),
                size = androidx.compose.ui.geometry.Size(w * rowWidths[index], barHeight),
                cornerRadius = CornerRadius(barHeight / 2f, barHeight / 2f),
            )
            // Маленькая "точка-статус" у левого края каждой полоски —
            // мягкий акцент вместо голой геометрической полосы.
            drawCircle(
                color = Color.White.copy(alpha = 0.65f),
                radius = barHeight * 0.18f,
                center = Offset(w * 0.06f + barHeight / 2f, size.height * top + barHeight / 2f),
            )
        }
    }
}

@Composable
fun SettingsIcon(color: Color, modifier: Modifier = Modifier) {
    // Современные "слайдеры" вместо угловатой шестерёнки-заглушки — три
    // мягкие дорожки с закруглёнными заливными ползунками на разной высоте,
    // без единой прямой острой линии.
    Canvas(modifier = modifier.size(22.dp)) {
        val w = size.width
        val trackHeight = size.height * 0.06f
        val rows = listOf(0.24f to 0.62f, 0.50f to 0.32f, 0.76f to 0.78f)
        rows.forEach { (rowY, knobX) ->
            val y = size.height * rowY
            drawRoundRect(
                color = color.copy(alpha = 0.35f),
                topLeft = Offset(w * 0.08f, y - trackHeight / 2f),
                size = androidx.compose.ui.geometry.Size(w * 0.84f, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f),
            )
            drawCircle(
                color = color,
                radius = size.height * 0.09f,
                center = Offset(w * knobX, y),
            )
        }
    }
}
