package com.stailegrow.lifevpn.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stailegrow.lifevpn.core.L
import com.stailegrow.lifevpn.core.PingQuality
import com.stailegrow.lifevpn.model.ProxyConfig
import com.stailegrow.lifevpn.ui.theme.HudType
import com.stailegrow.lifevpn.ui.theme.LocalPalette

@Composable
fun LatencyCard(servers: List<ProxyConfig>, pingHistory: Map<String, List<Int>>, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    HudCard(title = L.t("Задержка", "Latency"), modifier = modifier) {
        Text(L.t("Опрос каждые 10 с", "Polled every 10 s"), style = HudType.body(10.sp),
            color = palette.textSecondary, modifier = Modifier.padding(bottom = 12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (config in servers) {
                LatencyRow(extractTitle(config.displayName), pingHistory[config.id].orEmpty())
            }
        }
    }
}

@Composable
private fun LatencyRow(name: String, history: List<Int>) {
    val palette = LocalPalette.current
    val last = history.lastOrNull()
    val color = when (last?.let { PingQuality.of(it) }) {
        PingQuality.GOOD -> palette.good
        PingQuality.FAIR -> palette.warn
        PingQuality.POOR -> palette.bad
        null -> palette.textSecondary
    }
    Column(Modifier.fillMaxWidth()
        .background(palette.textPrimary.copy(alpha = 0.035f), RoundedCornerShape(12.dp))
        .padding(horizontal = 10.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(name, style = HudType.body(12.sp), color = palette.textPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(last?.let { L.t("$it мс", "$it ms") } ?: "—", style = HudType.code(11.sp), color = color)
        }
        Sparkline(history, color, Modifier.fillMaxWidth().height(40.dp).padding(top = 4.dp))
    }
}
