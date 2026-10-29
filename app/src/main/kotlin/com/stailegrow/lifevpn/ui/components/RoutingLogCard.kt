package com.stailegrow.lifevpn.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.stailegrow.lifevpn.core.ConnectionManager
import com.stailegrow.lifevpn.core.ConnectionState
import com.stailegrow.lifevpn.core.L
import com.stailegrow.lifevpn.core.RoutingLog
import com.stailegrow.lifevpn.ui.theme.HudType
import com.stailegrow.lifevpn.ui.theme.LocalPalette
import com.stailegrow.lifevpn.ui.theme.Palette
import com.stailegrow.lifevpn.ui.theme.cutRect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Журнал маршрутизации на вкладке «Статистика» — как в macOS-версии: видно,
 * какой адрес ушёл в туннель, какой напрямую, а какой отсечён. Кнопка
 * «Выгрузить» сохраняет журнал всей сессии в .txt через системный диалог.
 */
@Composable
fun RoutingLogCard(modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val entries by RoutingLog.entries.collectAsState()
    val counts by RoutingLog.counts.collectAsState()
    val connectionState by ConnectionManager.state.collectAsState()
    var filter by remember { mutableStateOf<RoutingLog.Route?>(null) }

    val shown = remember(entries, filter) {
        (if (filter == null) entries else entries.filter { it.route == filter }).take(150)
    }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use {
                            it.write(RoutingLog.report().toByteArray(Charsets.UTF_8))
                        }
                    }
                }
            }
        }
    }

    HudCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = L.t("Журнал маршрутизации", "Routing log").uppercase(),
                style = HudType.label(11.sp).copy(letterSpacing = 0.06.em),
                color = palette.textSecondary,
                modifier = Modifier.weight(1f),
            )
            val enabled = entries.isNotEmpty()
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(cutRect(16.dp))
                    .background(palette.accent.copy(alpha = if (enabled) 0.14f else 0.06f))
                    .clickable(enabled = enabled) { exporter.launch(RoutingLog.exportFileName()) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = L.t("Выгрузить", "Export"),
                    style = HudType.label(11.sp),
                    color = if (enabled) palette.accent else palette.textSecondary.copy(alpha = 0.4f),
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .padding(top = 12.dp)
                .horizontalScroll(rememberScrollState()),
        ) {
            FilterChip(null, L.t("Все", "All"), counts.values.sum(), filter == null, palette) { filter = null }
            for (route in listOf(RoutingLog.Route.PROXY, RoutingLog.Route.DIRECT, RoutingLog.Route.BLOCK)) {
                FilterChip(route, route.title, counts[route] ?: 0, filter == route, palette) { filter = route }
            }
        }

        if (shown.isEmpty()) {
            Text(
                text = when {
                    connectionState !is ConnectionState.Connected && entries.isEmpty() ->
                        L.t("Подключитесь — здесь появится, куда идёт каждое соединение",
                            "Connect to see where each connection goes")
                    filter == null -> L.t("Соединений пока нет", "No connections yet")
                    else -> L.t("В этой группе пока пусто", "Nothing here yet")
                },
                style = HudType.body(12.sp),
                color = palette.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
            )
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier
                    .padding(top = 10.dp)
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                for (entry in shown) {
                    LogRow(entry, palette)
                }
            }
        }
    }
}

private fun routeColor(route: RoutingLog.Route, palette: Palette): Color = when (route) {
    RoutingLog.Route.PROXY -> palette.accent
    RoutingLog.Route.DIRECT -> palette.good
    RoutingLog.Route.BLOCK -> palette.bad
    RoutingLog.Route.OTHER -> palette.textSecondary
}

@Composable
private fun FilterChip(
    route: RoutingLog.Route?,
    title: String,
    count: Int,
    selected: Boolean,
    palette: Palette,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) palette.textPrimary.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        if (route != null) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(routeColor(route, palette)),
            )
            Spacer(modifier = Modifier.width(5.dp))
        }
        Text(
            text = "$title  $count",
            style = HudType.label(11.sp),
            color = if (selected) palette.textPrimary else palette.textSecondary,
        )
    }
}

@Composable
private fun LogRow(entry: RoutingLog.Entry, palette: Palette) {
    val color = routeColor(entry.route, palette)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(palette.textPrimary.copy(alpha = 0.035f))
            .padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        Text(
            text = entry.time,
            style = HudType.code(10.sp),
            color = palette.textSecondary.copy(alpha = 0.8f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(66.dp)
                .clip(RoundedCornerShape(50))
                .background(color.copy(alpha = 0.14f))
                .padding(vertical = 2.dp),
        ) {
            Text(text = entry.route.title, style = HudType.label(9.sp), color = color)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = palette.textPrimary)) { append(entry.host) }
                withStyle(SpanStyle(color = palette.textSecondary.copy(alpha = 0.7f))) { append(":" + entry.port) }
            },
            style = HudType.code(11.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (entry.network == "UDP") {
            Text(text = "UDP", style = HudType.code(9.sp), color = palette.textSecondary.copy(alpha = 0.7f))
        }
    }
}
