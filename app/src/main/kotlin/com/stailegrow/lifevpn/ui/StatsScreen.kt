package com.stailegrow.lifevpn.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stailegrow.lifevpn.core.ServerStore
import com.stailegrow.lifevpn.ui.components.LatencyCard
import com.stailegrow.lifevpn.ui.components.RoutingLogCard
import com.stailegrow.lifevpn.ui.components.SpeedCard
import kotlinx.coroutines.delay

/**
 * «Статистика» — как в macOS-версии: замер скорости, история задержки по
 * всем узлам и журнал маршрутизации. Пока вкладка открыта, задержка
 * опрашивается раз в 10 секунд (тот же лёгкий режим, что на «Серверах»).
 */
@Composable
fun StatsScreen(modifier: Modifier = Modifier) {
    val servers by ServerStore.servers.collectAsState()
    val pingHistory by ServerStore.pingHistory.collectAsState()

    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000)
            ServerStore.pingAll(light = true)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
    ) {
        item(key = "speed") { SpeedCard() }
        if (servers.isNotEmpty()) {
            item(key = "latency") { LatencyCard(servers = servers, pingHistory = pingHistory) }
        }
        item(key = "routing") { RoutingLogCard() }
    }
}
