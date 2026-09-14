package com.stailegrow.maxstrike.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em

/**
 * Роли шрифтов — Life VPN 2.0. Раньше здесь были насыщенные плотные
 * трекинги под техно-шрифты (см. историю в git); теперь — системная
 * гарнитура (AppFonts.kt) и мягкий, спокойный трекинг, под нежный/
 * воздушный стиль. Имена функций не менялись, чтобы не трогать все
 * места использования HudType.* по всему приложению.
 */
object HudType {
    fun hero(size: TextUnit): TextStyle = TextStyle(
        fontSize = size,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.em,
        fontFamily = ChakraPetch,
    )

    fun heading(size: TextUnit): TextStyle = TextStyle(
        fontSize = size,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.em,
        fontFamily = ChakraPetch,
    )

    fun label(size: TextUnit): TextStyle = TextStyle(
        fontSize = size,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.01.em,
        fontFamily = ChakraPetch,
    )

    fun body(size: TextUnit): TextStyle = TextStyle(
        fontSize = size,
        fontWeight = FontWeight.Normal,
        fontFamily = ChakraPetch,
    )

    /** Цифры, адреса, логи — всё, что должно стоять в колонку. */
    fun code(size: TextUnit): TextStyle = TextStyle(
        fontSize = size,
        fontWeight = FontWeight.Normal,
        fontFamily = ShareTechMono,
    )

    /** Раньше — широкая "техно"-разрядка для подписей вроде "SECURE
     *  TUNNEL". В 2.0 подпись убрана из TopBar, функция оставлена (может
     *  использоваться где-то ещё) с мягким, а не киберпанк-трекингом. */
    fun wide(size: TextUnit): TextStyle = TextStyle(
        fontSize = size,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.08.em,
        fontFamily = Syncopate,
    )

    /** Вордмарк "Life VPN" в шапке (AppTopBar). */
    fun wordmark(size: TextUnit): TextStyle = TextStyle(
        fontSize = size,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.em,
        fontFamily = SairaStencilOne,
    )
}
