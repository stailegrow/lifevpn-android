package com.stailegrow.lifevpn.core

import android.net.TrafficStats
import android.os.Process
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

/**
 * Трафик VPN за текущую сессию.
 *
 * Раньше счётчики брались у ядра через его HTTP-эндпоинт `metrics` на
 * 127.0.0.1. Этот порт был без пароля, а петлевой адрес доступен любому
 * приложению на телефоне: по нему можно было найти работающий Xray (то есть
 * понять, что включён VPN), увидеть объём трафика и нагружать ядро через
 * /debug/pprof. Теперь ядро не открывает ни одного локального порта.
 *
 * Считаем системным счётчиком Android по UID приложения: весь трафик
 * устройства в туннеле уходит наружу через сокеты ядра, а ядро живёт в нашем
 * процессе. Это зашифрованный объём на проводе: туда же попадают соединения
 * ядра по маршруту «напрямую» и служебные запросы самого приложения, так что
 * цифра — оценка расхода, а не точный счёт только по outbound «proxy».
 */
object SessionTraffic {

    data class Snapshot(val uplink: Long = 0, val downlink: Long = 0) {
        val total: Long get() = uplink + downlink
    }

    private val _traffic = MutableStateFlow(Snapshot())
    val traffic: StateFlow<Snapshot> = _traffic.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var poller: Job? = null
    private var baseTx = 0L
    private var baseRx = 0L

    /** Перед запуском ядра: новая сессия считается с нуля. */
    fun reset() {
        stop()
        baseTx = read { TrafficStats.getUidTxBytes(it) } ?: 0L
        baseRx = read { TrafficStats.getUidRxBytes(it) } ?: 0L
        _traffic.value = Snapshot()
    }

    fun start() {
        poller?.cancel()
        poller = scope.launch {
            while (isActive) {
                runCatching { poll() }
                delay(2_000)
            }
        }
    }

    fun stop() {
        poller?.cancel()
        poller = null
    }

    private fun poll() {
        val tx = read { TrafficStats.getUidTxBytes(it) } ?: return
        val rx = read { TrafficStats.getUidRxBytes(it) } ?: return
        val current = _traffic.value
        // Счётчики системы только растут; если вдруг меньше базы (сброс
        // статистики системой) — прежние цифры нулями не затираем.
        _traffic.value = Snapshot(
            uplink = maxOf(tx - baseTx, current.uplink),
            downlink = maxOf(rx - baseRx, current.downlink),
        )
    }

    /** TrafficStats.UNSUPPORTED (-1) — устройство не отдаёт статистику. */
    private inline fun read(block: (Int) -> Long): Long? =
        block(Process.myUid()).takeIf { it >= 0 }
}
