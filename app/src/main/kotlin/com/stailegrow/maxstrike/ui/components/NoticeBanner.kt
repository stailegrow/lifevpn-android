package com.stailegrow.maxstrike.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stailegrow.maxstrike.ui.theme.HudType
import com.stailegrow.maxstrike.ui.theme.LocalPalette
import com.stailegrow.maxstrike.ui.theme.cutRect

enum class NoticeTone { INFO, WARNING, ERROR }

/**
 * Плашка-уведомление — Android-аналог NoticeBanner из Components.swift.
 * Раньше такие сообщения (статус быстрого добавления, ошибка подписки,
 * причина отката роутинга) висели голым текстом посреди экрана — теперь у
 * них своя маленькая панель того же визуального языка, что и остальной
 * интерфейс, вместо текста "в воздухе".
 */
@Composable
fun NoticeBanner(text: String, tone: NoticeTone = NoticeTone.INFO, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val accent = when (tone) {
        NoticeTone.INFO -> palette.accent
        NoticeTone.WARNING -> palette.warn
        NoticeTone.ERROR -> palette.bad
    }
    val shape = cutRect(16.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(accent.copy(alpha = 0.14f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(text = text, style = HudType.body(12.sp), color = accent)
    }
}
