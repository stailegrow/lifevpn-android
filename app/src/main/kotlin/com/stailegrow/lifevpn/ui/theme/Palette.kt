package com.stailegrow.lifevpn.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Палитра приложения — Life VPN 2.0. Та же роль-ориентированная модель,
 * что и в 1.0 (см. Theme.kt / LocalPalette), только сами цвета теперь
 * те же десять тем, что в macOS-версии: пять тёмных (Life night, основная
 * Life в цветах Life Panel, Аметист, Лагуна, Янтарь) и пять светлых. Экраны по-прежнему не знают конкретных
 * цветов, только роли — смена темы ничего не требует от разметки.
 */
data class Palette(
    val id: String,
    val nameRU: String,
    val nameEN: String,

    val background: Color,
    val card: Color,
    val cardBorder: Color,

    val accentStart: Color,
    val accentEnd: Color,
    val idleRing: Color,

    val textPrimary: Color,
    val textSecondary: Color,

    val good: Color,
    val warn: Color,
    val bad: Color,

    // Пятна живого фона (см. AppBackground.kt) — своя тройка цветов на тему.
    val blob1: Color,
    val blob2: Color,
    val blob3: Color,

    /** Тёмная тема (Ночь/AMOLED) — влияет на выбор базовой Material-схемы
     *  в Theme.kt (darkColorScheme vs lightColorScheme) для ролей, которые
     *  палитра явно не переопределяет. */
    val isDark: Boolean = false,
) {
    val name: String get() = nameRU
    val accent: Color get() = accentStart

    companion object {
        /**
         * По умолчанию — нейтральная светлая тема, не «для мальчиков» и не
         * «для девочек». Цвета приглушённые (низкая насыщенность), а не
         * яркие/неоновые — просили именно "нежные", а не повторение старой
         * HUD-гаммы.
         */
        val sky = Palette(
            id = "sky", nameRU = "Небо", nameEN = "Sky",
            background = Color(0xFFF2F7FD), card = Color(0xB3FFFFFF), cardBorder = Color(0x80FFFFFF),
            accentStart = Color(0xFF9AC7EC), accentEnd = Color(0xFFB7CBF2), idleRing = Color(0x33414352),
            textPrimary = Color(0xFF43485A), textSecondary = Color(0xFF8F93A6),
            good = Color(0xFF8FD9BE), warn = Color(0xFFF3C696), bad = Color(0xFFF3A3AE),
            blob1 = Color(0xFFD7E8FA), blob2 = Color(0xFFDEE3FB), blob3 = Color(0xFFDBF3E6),
        )

        val mint = Palette(
            id = "mint", nameRU = "Мята", nameEN = "Mint",
            background = Color(0xFFEFFAF4), card = Color(0xB3FFFFFF), cardBorder = Color(0x80FFFFFF),
            accentStart = Color(0xFF8FD8BE), accentEnd = Color(0xFFAEE7CE), idleRing = Color(0x332F4740),
            textPrimary = Color(0xFF3A4A44), textSecondary = Color(0xFF8B9C94),
            good = Color(0xFF8FD9BE), warn = Color(0xFFF3C696), bad = Color(0xFFF3A3AE),
            blob1 = Color(0xFFD6F1E2), blob2 = Color(0xFFCFEBE0), blob3 = Color(0xFFD9EEF8),
        )

        val lavender = Palette(
            id = "lavender", nameRU = "Лаванда", nameEN = "Lavender",
            background = Color(0xFFF5F1FC), card = Color(0xB3FFFFFF), cardBorder = Color(0x80FFFFFF),
            accentStart = Color(0xFFBFA8EE), accentEnd = Color(0xFFD1C2F3), idleRing = Color(0x33413C52),
            textPrimary = Color(0xFF48435A), textSecondary = Color(0xFF938EA6),
            good = Color(0xFF8FD9BE), warn = Color(0xFFF3C696), bad = Color(0xFFF3A3AE),
            blob1 = Color(0xFFE6DEFB), blob2 = Color(0xFFEEE1FA), blob3 = Color(0xFFDDE7FB),
        )

        val peach = Palette(
            id = "peach", nameRU = "Персик", nameEN = "Peach",
            background = Color(0xFFFDF5EC), card = Color(0xB3FFFFFF), cardBorder = Color(0x80FFFFFF),
            accentStart = Color(0xFFF0B583), accentEnd = Color(0xFFF6CDA3), idleRing = Color(0x3352453C),
            textPrimary = Color(0xFF564A3E), textSecondary = Color(0xFFA69A8C),
            good = Color(0xFF8FD9BE), warn = Color(0xFFF3C696), bad = Color(0xFFF3A3AE),
            blob1 = Color(0xFFFAE2C8), blob2 = Color(0xFFF8D9D2), blob3 = Color(0xFFF7EEC4),
        )

        /** Розовая тема остаётся в списке выбора, но не служит темой по умолчанию. */
        val rose = Palette(
            id = "rose", nameRU = "Роза", nameEN = "Rose",
            background = Color(0xFFFDF0F5), card = Color(0xB3FFFFFF), cardBorder = Color(0x80FFFFFF),
            accentStart = Color(0xFFEEA6C1), accentEnd = Color(0xFFF3BFD4), idleRing = Color(0x3352414A),
            textPrimary = Color(0xFF564650), textSecondary = Color(0xFFA6919C),
            good = Color(0xFF8FD9BE), warn = Color(0xFFF3C696), bad = Color(0xFFF3A3AE),
            blob1 = Color(0xFFF9DCE9), blob2 = Color(0xFFF6D9DE), blob3 = Color(0xFFF7E9C4),
        )

        /** «Life night» — настоящий чёрный фон под AMOLED-экраны (экономия
         *  батареи) с нежными акцентами, как в macOS-версии. Идентификатор
         *  остался прежним, «amoled», — от него зависят сохранённый выбор и
         *  чёрная кнопка в покое (см. ConnectSlab). */
        val amoled = Palette(
            id = "amoled", nameRU = "Life night", nameEN = "Life night",
            background = Color(0xFF000000), card = Color(0xFF121214), cardBorder = Color(0x26FFFFFF),
            accentStart = Color(0xFF9AC2F2), accentEnd = Color(0xFFC2AEF7), idleRing = Color(0x33FFFFFF),
            textPrimary = Color(0xFFF5F6FA), textSecondary = Color(0xFF8C8F9C),
            good = Color(0xFF7FD9B8), warn = Color(0xFFF0C283), bad = Color(0xFFF08FA0),
            blob1 = Color(0xFF121722), blob2 = Color(0xFF17121F), blob3 = Color(0xFF0F1A18),
            isDark = true,
        )

        /** «Life» — основная тема с версии 3.0, в цветах панели управления
         *  серверами Life Panel и macOS-клиента: тёплый почти чёрный фон,
         *  коричневатые карточки и оранжевый акцент. */
        val life = Palette(
            id = "life", nameRU = "Life", nameEN = "Life",
            background = Color(0xFF0C0A09), card = Color(0xFF1C1714), cardBorder = Color(0x14FFFFFF),
            accentStart = Color(0xFFF97316), accentEnd = Color(0xFFFF9A4D), idleRing = Color(0x33FFFFFF),
            textPrimary = Color(0xFFF5F1ED), textSecondary = Color(0xFF9C9087),
            good = Color(0xFF3CCB7F), warn = Color(0xFFF5B451), bad = Color(0xFFFF6B6B),
            blob1 = Color(0xFF4A2310), blob2 = Color(0xFF2E1608), blob3 = Color(0xFF3A1C0C),
            isDark = true,
        )

        val amethyst = Palette(
            id = "amethyst", nameRU = "Аметист", nameEN = "Amethyst",
            background = Color(0xFF09080F), card = Color(0xFF1A1824), cardBorder = Color(0x1AFFFFFF),
            accentStart = Color(0xFF7C5CFF), accentEnd = Color(0xFFA48BFF), idleRing = Color(0x33FFFFFF),
            textPrimary = Color(0xFFF4F4F7), textSecondary = Color(0xFF8E8E9C),
            good = Color(0xFF4ADE9A), warn = Color(0xFFF5B451), bad = Color(0xFFFF6B6B),
            blob1 = Color(0xFF2A1A5E), blob2 = Color(0xFF5A1230), blob3 = Color(0xFF10283F),
            isDark = true,
        )

        val lagoon = Palette(
            id = "lagoon", nameRU = "Лагуна", nameEN = "Lagoon",
            background = Color(0xFF060B0D), card = Color(0xFF141C20), cardBorder = Color(0x1AFFFFFF),
            accentStart = Color(0xFF14B8A6), accentEnd = Color(0xFF3DDCC8), idleRing = Color(0x33FFFFFF),
            textPrimary = Color(0xFFF2F6F7), textSecondary = Color(0xFF8A979C),
            good = Color(0xFF4ADE9A), warn = Color(0xFFF5B451), bad = Color(0xFFFF6B6B),
            blob1 = Color(0xFF0E3B3A), blob2 = Color(0xFF1D1554), blob3 = Color(0xFF10283F),
            isDark = true,
        )

        val amber = Palette(
            id = "amber", nameRU = "Янтарь", nameEN = "Amber",
            background = Color(0xFF0B0906), card = Color(0xFF1E1A14), cardBorder = Color(0x1AFFFFFF),
            accentStart = Color(0xFFF59E0B), accentEnd = Color(0xFFFFC14D), idleRing = Color(0x33FFFFFF),
            textPrimary = Color(0xFFF7F5F2), textSecondary = Color(0xFF9C9488),
            good = Color(0xFF4ADE9A), warn = Color(0xFFF5B451), bad = Color(0xFFFF6B6B),
            blob1 = Color(0xFF4A2C08), blob2 = Color(0xFF5A1230), blob3 = Color(0xFF10283F),
            isDark = true,
        )

        /** Тот же набор и порядок, что в macOS-версии: сначала пять тёмных,
         *  затем пять светлых — в Настройках это ровно два ряда. */
        val all: List<Palette> = listOf(amoled, life, amethyst, lagoon, amber, sky, mint, lavender, peach, rose)

        fun named(id: String): Palette = all.firstOrNull { it.id == id } ?: life
    }
}

/** Диагональный градиент start→end — Android-аналог Palette.accentGradient. */
fun Palette.accentBrush(): Brush = Brush.linearGradient(listOf(accentStart, accentEnd))
