package com.stailegrow.maxstrike.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.stailegrow.maxstrike.core.L
import com.stailegrow.maxstrike.core.UpdateChecker

/**
 * Ненавязчивый баннер новой версии — Android-аналог NoticeBanner (та же
 * плашка, что уже используется для отката маршрутизации и т.п.), но с
 * тапом: сама плашка и есть кнопка действия, отдельной кнопки не нужно.
 * Живёт на главном экране (см. HomeScreen.kt) и подписан на общий
 * UpdateChecker.state, поэтому появляется/меняется сам, без дополнительной
 * логики в HomeScreen — если проверять/качать нечего, просто ничего не
 * рисует (composable, вернувший Unit, не занимает места в LazyColumn).
 */
@Composable
fun UpdateBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by UpdateChecker.state.collectAsState()

    data class Content(val text: String, val tone: NoticeTone, val onClick: () -> Unit)

    val content: Content? = when (val s = state) {
        is UpdateChecker.State.Available -> Content(
            text = L.t(
                "Доступна версия ${s.info.version} — нажмите, чтобы скачать",
                "Version ${s.info.version} is available — tap to download",
            ),
            tone = NoticeTone.INFO,
            onClick = { UpdateChecker.startDownload(context, s.info) },
        )
        is UpdateChecker.State.Downloading -> Content(
            text = L.t("Скачивание обновления… ${s.progress}%", "Downloading update… ${s.progress}%"),
            tone = NoticeTone.INFO,
            onClick = {},
        )
        is UpdateChecker.State.ReadyToInstall -> Content(
            text = L.t("Обновление скачано — нажмите, чтобы установить", "Update downloaded — tap to install"),
            tone = NoticeTone.INFO,
            onClick = {
                if (UpdateChecker.canInstallPackages(context)) {
                    UpdateChecker.installUpdate(context, s.apkFile)
                } else {
                    UpdateChecker.openInstallPermissionSettings(context)
                }
            },
        )
        is UpdateChecker.State.Failed -> if (s.duringDownload) {
            Content(
                text = L.t(
                    "Не удалось скачать обновление: ${s.message}. Нажмите, чтобы повторить",
                    "Could not download the update: ${s.message}. Tap to retry",
                ),
                tone = NoticeTone.ERROR,
                onClick = { s.info?.let { UpdateChecker.startDownload(context, it) } },
            )
        } else {
            null
        }
        else -> null
    }

    if (content != null) {
        NoticeBanner(
            text = content.text,
            tone = content.tone,
            modifier = modifier.fillMaxWidth().clickable(onClick = content.onClick),
        )
    }
}
