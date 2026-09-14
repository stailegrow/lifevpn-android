package com.stailegrow.maxstrike.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.stailegrow.maxstrike.ui.theme.LocalPalette
import com.stailegrow.maxstrike.ui.theme.Palette
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * Живой фон Life VPN 2.0 — несколько мягких пятен, которые не просто
 * дрейфуют, а ещё и переливаются цветом (каждое плавно перетекает между
 * всеми акцентными оттенками темы). Раньше движение было почти незаметным
 * (амплитуда — фиксированные ~30dp, период 22-30 секунд), а на тёмных
 * темах (Ночь/AMOLED) пятна были собственных, почти чёрных оттенков и
 * терялись на чёрном фоне — фон казался статичным. Теперь амплитуда
 * считается от размера экрана (всегда заметна на любом устройстве), период
 * заметно короче, а цвет каждого пятна подмешивает акцентные цвета темы —
 * переливание видно на любой палитре, включая AMOLED.
 *
 * Тумблер "Живой фон" (SettingsStore.liveBackground) управляет параметром
 * animated: выключен — пятна остаются на месте в первом кадре, без
 * движения и без переливания цвета.
 */
@Composable
fun AppBackground(animated: Boolean, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val density = LocalDensity.current
    var elapsedMs by remember { mutableStateOf(0L) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    if (animated) {
        LaunchedEffect(Unit) {
            val startMs = System.currentTimeMillis()
            while (true) {
                elapsedMs = System.currentTimeMillis() - startMs
                delay(33)
            }
        }
    }

    val vignetteBrush = remember(canvasSize, density, palette.background) {
        buildVignetteBrush(canvasSize, density, palette.background)
    }

    Canvas(modifier = modifier.onSizeChanged { canvasSize = it }) {
        drawSoftBackground(
            palette = palette,
            timeSec = elapsedMs / 1000f,
            animated = animated,
            vignetteBrush = vignetteBrush,
        )
    }
}

/** Виньетка не зависит от времени — считается один раз на размер экрана и
 *  палитру. Задача не "прижать" фон рамкой, а чуть притушить самые края,
 *  чтобы карточки в центре читались лучше даже при более ярком переливании. */
private fun buildVignetteBrush(canvasSize: IntSize, density: Density, backgroundColor: Color): Brush {
    if (canvasSize.width <= 0 || canvasSize.height <= 0) {
        return Brush.radialGradient(listOf(Color.Transparent, Color.Transparent))
    }
    return with(density) {
        val vignetteRadius = 720.dp.toPx()
        val innerStop = (240.dp.toPx() / vignetteRadius).coerceIn(0f, 1f)
        Brush.radialGradient(
            0f to Color.Transparent,
            innerStop to Color.Transparent,
            1f to backgroundColor.copy(alpha = 0.55f),
            center = Offset(canvasSize.width / 2f, canvasSize.height / 2f),
            radius = vignetteRadius,
        )
    }
}

private class DriftingBlob(
    // Точка отдыха в долях ширины/высоты экрана (0..1) — раскладка
    // одинакова на любом размере экрана.
    val restX: Float,
    val restY: Float,
    val radiusFraction: Float,
    val driftFraction: Float,
    val periodSec: Float,
    val phase: Float,
    val colorCyclePeriodSec: Float,
    val colorPhase: Float,
)

// Четыре пятна, разнесённые по экрану и по скорости/фазе — вместе создают
// ощущение живого, переливающегося фона, а не механического повторения
// одной и той же траектории.
private val blobs = listOf(
    DriftingBlob(restX = 0.16f, restY = 0.14f, radiusFraction = 0.60f, driftFraction = 0.16f, periodSec = 11f, phase = 0f, colorCyclePeriodSec = 14f, colorPhase = 0f),
    DriftingBlob(restX = 0.88f, restY = 0.28f, radiusFraction = 0.50f, driftFraction = 0.14f, periodSec = 13f, phase = 2.1f, colorCyclePeriodSec = 17f, colorPhase = 3.5f),
    DriftingBlob(restX = 0.24f, restY = 0.80f, radiusFraction = 0.66f, driftFraction = 0.15f, periodSec = 15f, phase = 4.2f, colorCyclePeriodSec = 20f, colorPhase = 1.7f),
    DriftingBlob(restX = 0.82f, restY = 0.86f, radiusFraction = 0.44f, driftFraction = 0.13f, periodSec = 9f, phase = 5.6f, colorCyclePeriodSec = 12f, colorPhase = 5.0f),
)

private fun DrawScope.drawSoftBackground(
    palette: Palette,
    timeSec: Float,
    animated: Boolean,
    vignetteBrush: Brush,
) {
    // На тёмных темах (Ночь/AMOLED) собственные "тёмные" оттенки пятен
    // почти сливаются с фоном — подмешиваем акцентные цвета темы, чтобы
    // переливание было заметно и на чёрном экране.
    val paletteColors = listOf(palette.blob1, palette.blob2, palette.blob3)
    val cycleColors = if (palette.isDark) {
        paletteColors.map { lerp(it, palette.accentStart, 0.55f) } + lerp(palette.blob1, palette.accentEnd, 0.55f)
    } else {
        paletteColors + lerp(palette.blob2, palette.accentStart, 0.35f)
    }
    val alpha = if (palette.isDark) 0.5f else 0.68f
    val minDim = min(size.width, size.height)

    blobs.forEach { blob ->
        val t = if (!animated) 0f else (timeSec / blob.periodSec + blob.phase) * (2f * Math.PI.toFloat())
        val driftPx = blob.driftFraction * minDim
        val offsetX = sin(t) * driftPx
        val offsetY = sin(t * 0.8f + 1.3f) * driftPx

        val centerX = blob.restX * size.width + offsetX
        val centerY = blob.restY * size.height + offsetY

        // Дыхание радиуса — пятно чуть "пульсирует" в размере, а не только
        // ездит по экрану.
        val pulse = if (!animated) 0f else sin(t * 1.3f)
        val radiusPx = blob.radiusFraction * minDim * (1f + pulse * 0.08f)

        // Переливание цвета — каждое пятно плавно перетекает между всеми
        // оттенками палитры по кругу, со своим периодом и фазой.
        val colorT = if (!animated) 0f else (timeSec / blob.colorCyclePeriodSec + blob.colorPhase) % 1f
        val segment = colorT * cycleColors.size
        val i0 = segment.toInt().coerceIn(0, cycleColors.size - 1)
        val i1 = (i0 + 1) % cycleColors.size
        val frac = segment - i0
        val color = lerp(cycleColors[i0], cycleColors[i1], frac)

        drawCircle(
            brush = Brush.radialGradient(
                0f to color.copy(alpha = alpha),
                0.7f to color.copy(alpha = alpha * 0.5f),
                1f to color.copy(alpha = 0f),
                center = Offset(centerX, centerY),
                radius = radiusPx,
            ),
            radius = radiusPx,
            center = Offset(centerX, centerY),
        )
    }

    // Мягкая виньетка поверх пятен — держит края чуть тише, чтобы карточки
    // в центре читались лучше.
    drawRect(brush = vignetteBrush)
}
