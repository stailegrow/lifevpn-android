package com.stailegrow.lifevpn.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stailegrow.lifevpn.core.ConnectionManager
import com.stailegrow.lifevpn.core.ConnectionState
import com.stailegrow.lifevpn.core.L
import com.stailegrow.lifevpn.core.ServerStore
import com.stailegrow.lifevpn.core.SessionTraffic
import com.stailegrow.lifevpn.model.Subscription
import com.stailegrow.lifevpn.ui.theme.HudType
import com.stailegrow.lifevpn.ui.theme.LocalPalette

/**
 * Карточка «Трафик» на вкладке «Статистика» — как в macOS-версии. Пока VPN
 * подключён — сколько прошло через туннель за эту сессию (всего, скачано,
 * отправлено); без подключения — расход по подпискам с панели.
 */
@Composable
fun TrafficCard(modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val connectionState by ConnectionManager.state.collectAsState()
    val traffic by SessionTraffic.traffic.collectAsState()
    val subscriptions by ServerStore.subscriptions.collectAsState()
    val connected = connectionState is ConnectionState.Connected

    HudCard(title = L.t("Трафик", "Traffic"), modifier = modifier) {
        if (connected) {
            BigValue(Subscription.formatBytes(traffic.total), L.t("за сессию", "this session"))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(top = 6.dp),
            ) {
                Text("↓ " + Subscription.formatBytes(traffic.downlink), style = HudType.code(12.sp), color = palette.textSecondary)
                Text("↑ " + Subscription.formatBytes(traffic.uplink), style = HudType.code(12.sp), color = palette.textSecondary)
            }
        } else {
            val total = subscriptions.mapNotNull { it.usedBytes }.sum()
            BigValue(Subscription.formatBytes(total), L.t("всего", "total"))
            if (subscriptions.isEmpty()) {
                Text(
                    text = L.t("Подключитесь — здесь появится трафик через туннель за сессию",
                        "Connect to see the traffic that went through the tunnel this session"),
                    style = HudType.body(12.sp),
                    color = palette.textSecondary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        for (subscription in subscriptions) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text(
                    text = subscription.displayName,
                    style = HudType.body(12.sp),
                    color = palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = subscription.trafficSummary ?: "—",
                    style = HudType.code(11.sp),
                    color = palette.textPrimary.copy(alpha = 0.85f),
                )
            }
        }
    }
}

/** Крупное число и подпись к нему: «192.34 ГБ за сессию». */
@Composable
private fun BigValue(text: String, caption: String) {
    val palette = LocalPalette.current
    val space = text.lastIndexOf(' ').takeIf { it > 0 } ?: text.lastIndexOf(' ')
    val number = if (space > 0) text.substring(0, space) else text
    val unit = if (space > 0) text.substring(space + 1) else ""
    Row(verticalAlignment = Alignment.Bottom) {
        Text(text = number, style = HudType.heading(26.sp), color = palette.textPrimary)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$unit $caption".trim(),
            style = HudType.label(12.sp),
            color = palette.textSecondary,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
}
