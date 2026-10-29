package com.stailegrow.lifevpn.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.URL

/**
 * Трафик через туннель за текущую сессию — как в macOS-версии
 * (Core/SessionTraffic.swift).
 *
 * Ядро само считает байты по каждому outbound (`stats` + `policy.system`) и
 * отдаёт счётчики через встроенный HTTP-эндпоинт `metrics` на 127.0.0.1.
 * Берём только outbound «proxy»: прямой и заблокированный трафик через VPN
 * не ходит и в цифру не входит. Петлевой адрес в TUN не попадает, поэтому
 * запрос к счётчикам идёт прямо к ядру внутри процесса.
 */
object SessionTraffic {

    data class Snapshot(val uplink: Long = 0, val downlink: Long = 0) {
        val total: Long get() = uplink + downlink
    }

    private val _traffic = MutableStateFlow(Snapshot())
    val traffic: StateFlow<Snapshot> = _traffic.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var poller: Job? = null
    private var port: Int? = null

    /** Свободный локальный порт: занятый уронил бы ядро на старте. */
    fun freePort(): Int? = runCatching {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
    }.getOrNull()

    /** Перед запуском ядра: новая сессия считается с нуля. */
    fun reset(port: Int?) {
        stop()
        this.port = port
        _traffic.value = Snapshot()
    }

    fun start() {
        val p = port ?: return
        poller?.cancel()
        poller = scope.launch {
            while (isActive) {
                runCatching { poll(p) }
                delay(2_000)
            }
        }
    }

    fun stop() {
        poller?.cancel()
        poller = null
    }

    private fun poll(port: Int) {
        val connection = URL("http://127.0.0.1:$port/debug/vars").openConnection(Proxy.NO_PROXY) as HttpURLConnection
        connection.connectTimeout = 1_500
        connection.readTimeout = 1_500
        try {
            if (connection.responseCode != 200) return
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val proxy = JSONObject(body).optJSONObject("stats")
                ?.optJSONObject("outbound")
                ?.optJSONObject("proxy") ?: return
            val up = proxy.optLong("uplink", 0)
            val down = proxy.optLong("downlink", 0)
            // Счётчики ядра только растут; меньшее значение — уже другой
            // процесс ядра, прежние цифры нулями не затираем.
            val current = _traffic.value
            _traffic.value = Snapshot(maxOf(up, current.uplink), maxOf(down, current.downlink))
        } finally {
            connection.disconnect()
        }
    }
}
