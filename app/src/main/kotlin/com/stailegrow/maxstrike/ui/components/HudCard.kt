package com.stailegrow.maxstrike.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.stailegrow.maxstrike.ui.theme.HudType
import com.stailegrow.maxstrike.ui.theme.LocalPalette
import com.stailegrow.maxstrike.ui.theme.cutRect

/**
 * HudCard — мягкая панель без рамки: скруглённый прямоугольник (cutRect,
 * теперь всегда со скруглёнными углами — см. HudShapes.kt) с полупрозрачной
 * заливкой, без обводки и без декоративных уголков/штриховки версии 1.
 * Заголовок — просто аккуратная надпись капсом, без префикса "// " и без
 * разделительной линии, тем же языком, что подпись "Серверы" в референсном
 * макете Life VPN.
 *
 * Карточка сама никогда не задаёт себе фиксированную ширину — снаружи
 * всегда fillMaxWidth (или weight в Row), высота считается по контенту.
 */
@Composable
fun HudCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    innerPadding: PaddingValues = PaddingValues(16.dp),
    // Диалоги (например, добавление сервера/подписки) сидят поверх
    // анимированного живого фона и системного затемнения диалога сразу —
    // обычная полупрозрачная заливка карточки в этом случае выглядела
    // "грязной"/просвечивающей и почти нечитаемой. solid=true даёт
    // непрозрачную заливку тем же цветом — для модальных окон.
    solid: Boolean = false,
    content: @Composable () -> Unit,
) {
    val palette = LocalPalette.current
    val shape = cutRect(20.dp)
    val backgroundColor = if (solid) palette.card.copy(alpha = 1f) else palette.card

    Column(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .fillMaxWidth(),
    ) {
        if (title != null) {
            Text(
                text = title.uppercase(),
                style = HudType.label(11.sp).copy(letterSpacing = 0.06.em),
                color = palette.textSecondary,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp, start = 16.dp, end = 16.dp, bottom = 4.dp),
            )
        }
        Column(modifier = Modifier.fillMaxWidth().padding(innerPadding)) {
            content()
        }
    }
}
