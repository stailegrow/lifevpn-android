package com.stailegrow.maxstrike.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Прямоугольник со скруглёнными углами — базовая форма всего интерфейса
 * Life VPN 2.0. Раньше здесь были срезанные (HUD) углы — версия 2.0 это
 * чисто косметика поверх той же логики (см. PLAN.md), поэтому класс и имена
 * функций (CutRectShape/cutRect/cutRectAll) намеренно не переименованы:
 * так все ~10 мест использования (HudCard, ServerRow, ConnectSlab, TopBar,
 * Cards, AddServerDialog, QrScannerView, NoticeBanner...) продолжают
 * работать без единой правки, просто визуально стали мягче.
 */
enum class CutCorner { TOP_LEADING, TOP_TRAILING, BOTTOM_TRAILING, BOTTOM_LEADING }

class CutRectShape(
    private val cut: Dp,
    private val corners: Set<CutCorner> = CutCorner.values().toSet(),
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val radiusPx = with(density) { cut.toPx() }.coerceAtMost(minOf(size.width, size.height) / 2).coerceAtLeast(0f)
        return Outline.Generic(buildRoundedPath(size, radiusPx, corners))
    }

    // См. комментарий в исходной версии файла: equals/hashCode нужны, чтобы
    // Compose не считал каждый новый CutRectShape(cut, corners) "другим"
    // объектом при рекомпозиции — иначе clip/border/redraw гоняются заново
    // на каждой карточке при скролле, хотя ничего не изменилось.
    override fun equals(other: Any?): Boolean =
        other is CutRectShape && other.cut == cut && other.corners == corners

    override fun hashCode(): Int = cut.hashCode() * 31 + corners.hashCode()
}

private fun buildRoundedPath(size: Size, radiusPx: Float, corners: Set<CutCorner>): Path {
    val tl = if (CutCorner.TOP_LEADING in corners) radiusPx else 0f
    val tr = if (CutCorner.TOP_TRAILING in corners) radiusPx else 0f
    val br = if (CutCorner.BOTTOM_TRAILING in corners) radiusPx else 0f
    val bl = if (CutCorner.BOTTOM_LEADING in corners) radiusPx else 0f
    val w = size.width
    val h = size.height

    return Path().apply {
        moveTo(tl, 0f)
        lineTo(w - tr, 0f)
        if (tr > 0f) {
            arcTo(Rect(w - 2 * tr, 0f, w, 2 * tr), -90f, 90f, false)
        } else {
            lineTo(w, 0f)
        }
        lineTo(w, h - br)
        if (br > 0f) {
            arcTo(Rect(w - 2 * br, h - 2 * br, w, h), 0f, 90f, false)
        } else {
            lineTo(w, h)
        }
        lineTo(bl, h)
        if (bl > 0f) {
            arcTo(Rect(0f, h - 2 * bl, 2 * bl, h), 90f, 90f, false)
        } else {
            lineTo(0f, h)
        }
        lineTo(0f, tl)
        if (tl > 0f) {
            arcTo(Rect(0f, 0f, 2 * tl, 2 * tl), 180f, 90f, false)
        } else {
            lineTo(0f, 0f)
        }
        close()
    }
}

/**
 * cutRect()/cutRectAll() — те же сигнатуры, что и в 1.0, кешируют форму
 * через remember(cut, corners), теперь скруглённую, а не срезанную.
 * По умолчанию скругляем ВСЕ углы (мягкая карточка) — раньше по умолчанию
 * срезались только два угла по диагонали, но для нежного/воздушного стиля
 * равномерное скругление смотрится органичнее для мест, не задавших corners
 * явно.
 */
@Composable
fun cutRect(
    cut: Dp,
    corners: Set<CutCorner> = CutCorner.values().toSet(),
): CutRectShape = remember(cut, corners) { CutRectShape(cut, corners) }

@Composable
fun cutRectAll(cut: Dp): CutRectShape {
    val allCorners = remember { CutCorner.values().toSet() }
    return remember(cut, allCorners) { CutRectShape(cut, allCorners) }
}

/**
 * Раньше — декоративные HUD-уголки (см. историю в git). В Life VPN 2.0 их
 * не рисуем совсем: no-op с той же сигнатурой, чтобы вызывающие места
 * (HudCard и т.д.) не трогать. Параметры оставлены для совместимости
 * сигнатуры и намеренно не используются.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun CornerTicks(
    color: Color,
    modifier: Modifier = Modifier,
    length: Dp = 8.dp,
    strokeWidth: Dp = 1.2.dp,
    inset: Dp = 1.dp,
) {
    // Нежный/воздушный стиль 2.0 — без техно-декора по углам.
}

/**
 * Раньше — диагональная HUD-штриховка (см. историю в git). В Life VPN 2.0
 * не рисуем: no-op с той же сигнатурой ради совместимости вызывающих мест.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun Hatch(
    color: Color,
    modifier: Modifier = Modifier,
    spacing: Dp = 5.dp,
    strokeWidth: Dp = 1.dp,
) {
    // Нежный/воздушный стиль 2.0 — без штриховки.
}
