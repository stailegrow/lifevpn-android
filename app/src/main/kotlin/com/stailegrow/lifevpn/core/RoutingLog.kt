package com.stailegrow.lifevpn.core

import android.content.Context
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
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Журнал маршрутизации: куда ушло каждое соединение — в туннель, напрямую
 * или в блок. Тот же журнал, что в macOS-версии (Core/RoutingLog.swift).
 *
 * Источник — access-лог самого Xray: ядро пишет строку на каждое принятое
 * соединение, а мы раз в секунду дочитываем только новые байты. Файл
 * обнуляется при каждом подключении и не растёт бесконечно.
 */
object RoutingLog {

    enum class Route(val ru: String, val en: String) {
        PROXY("Туннель", "Tunnel"),
        DIRECT("Напрямую", "Direct"),
        BLOCK("Блок", "Blocked"),
        OTHER("Другое", "Other");

        val title: String get() = L.t(ru, en)

        companion object {
            fun of(tag: String): Route = when (tag) {
                "proxy" -> PROXY
                "direct" -> DIRECT
                "block" -> BLOCK
                else -> OTHER
            }
        }
    }

    data class Entry(
        val id: Long,
        val time: String,
        val network: String,
        val host: String,
        val port: String,
        val route: Route,
        /** Ни одно правило не совпало — ушло по маршруту по умолчанию. */
        val byDefault: Boolean,
    )

    private const val SHOWN_LIMIT = 400
    /** Выше этого размера файл обнуляется: журнал нужен, чтобы видеть
     *  текущие маршруты, а не хранить историю за недели. */
    private const val FILE_LIMIT = 8L * 1024 * 1024

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    private val _counts = MutableStateFlow<Map<Route, Int>>(emptyMap())
    val counts: StateFlow<Map<Route, Int>> = _counts.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var poller: Job? = null
    private var file: File? = null
    private var offset = 0L
    private var pending = StringBuilder()
    private var nextId = 0L

    fun file(context: Context): File = File(context.filesDir, "access.log").also { file = it }

    /** Перед запуском ядра: старый журнал к новой сессии отношения не имеет. */
    fun reset(context: Context) {
        end()
        synchronized(lock) {
            file(context).delete()
            offset = 0
            pending = StringBuilder()
        }
        _entries.value = emptyList()
        _counts.value = emptyMap()
    }

    fun begin(context: Context) {
        file(context)
        poller?.cancel()
        poller = scope.launch {
            while (isActive) {
                runCatching { poll() }
                delay(1_000)
            }
        }
    }

    fun end() {
        poller?.cancel()
        poller = null
    }

    private fun poll() {
        val fresh = ArrayList<Entry>()
        synchronized(lock) {
            val f = file ?: return
            if (!f.exists()) return
            val size = f.length()
            if (size < offset) { offset = 0; pending = StringBuilder() }   // файл обнулили
            if (size > FILE_LIMIT) {
                // Ядро пишет в режиме дозаписи, поэтому обрезка безопасна:
                // следующая строка просто ляжет в начало пустого файла.
                runCatching { RandomAccessFile(f, "rw").use { it.setLength(0) } }
                offset = 0
                pending = StringBuilder()
                return
            }
            if (size <= offset) return
            val toRead = minOf(size - offset, 1024L * 1024).toInt()
            val bytes = ByteArray(toRead)
            RandomAccessFile(f, "r").use { raf ->
                raf.seek(offset)
                raf.readFully(bytes)
            }
            offset += toRead
            pending.append(String(bytes, Charsets.UTF_8))
            val lastNewline = pending.lastIndexOf("\n")
            if (lastNewline < 0) return
            val complete = pending.substring(0, lastNewline)
            pending = StringBuilder(pending.substring(lastNewline + 1))
            for (line in complete.split('\n')) parse(line)?.let { fresh.add(it) }
        }
        append(fresh)
    }

    private fun append(fresh: List<Entry>) {
        if (fresh.isEmpty()) return
        val counts = _counts.value.toMutableMap()
        for (e in fresh) counts[e.route] = (counts[e.route] ?: 0) + 1
        _counts.value = counts
        _entries.value = (fresh.asReversed() + _entries.value).take(SHOWN_LIMIT)
    }

    /**
     * `2026/10/23 06:39:53.219282 from tcp:10.0.0.2:42840 accepted tcp:github.com:443 [tun -> direct]`
     *
     * HTTP-прокси пишет адрес как `//host:port`; служебные запросы DNS
     * самого ядра (`from DNS`) к трафику пользователя отношения не имеют.
     */
    private fun parse(line: String): Entry? {
        val accepted = line.indexOf(" accepted ")
        if (accepted < 0) return null
        val head = line.substring(0, accepted)
        if (head.contains(" from DNS")) return null
        val open = line.indexOf(" [", accepted)
        val close = if (open >= 0) line.indexOf(']', open) else -1
        if (open < 0 || close < 0) return null

        val headParts = head.split(' ')
        val time = if (headParts.size > 1) headParts[1].take(8) else ""

        var target = line.substring(accepted + " accepted ".length, open)
        var network = "tcp"
        when {
            target.startsWith("//") -> target = target.substring(2)
            target.startsWith("tcp:") || target.startsWith("udp:") -> {
                network = target.substring(0, 3)
                target = target.substring(4)
            }
        }
        val colon = target.lastIndexOf(':')
        if (colon < 0) return null
        var host = target.substring(0, colon)
        val port = target.substring(colon + 1)
        if (host.startsWith("[") && host.endsWith("]")) host = host.substring(1, host.length - 1)

        val detour = line.substring(open + 2, close)
        val separator = listOf(" ==> ", " -> ", " >> ").firstOrNull { detour.contains(it) } ?: return null
        val sides = detour.split(separator)
        if (sides.size != 2) return null

        return Entry(
            id = ++nextId,
            time = time,
            network = network.uppercase(Locale.ROOT),
            host = host,
            port = port,
            route = Route.of(sides[1].trim()),
            byDefault = separator == " >> ",
        )
    }

    /** Весь журнал текущей сессии текстом — не только то, что видно в окне. */
    fun report(): String {
        val all = ArrayList<Entry>()
        synchronized(lock) {
            val f = file
            if (f != null && f.exists()) {
                runCatching { f.readText(Charsets.UTF_8) }.getOrNull()
                    ?.split('\n')?.forEach { line -> parse(line)?.let { all.add(it) } }
            }
        }
        if (all.isEmpty()) all.addAll(_entries.value.asReversed())

        val stamp = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.ROOT).format(Date())
        val summary = Route.values().mapNotNull { route ->
            val n = all.count { it.route == route }
            if (n > 0) "${route.ru}: $n" else null
        }.joinToString("  ·  ")
        val sb = StringBuilder()
        sb.append("Life VPN — журнал маршрутизации\n")
        sb.append("Выгружен: $stamp\n")
        sb.append("Соединений: ${all.size}  ·  $summary\n\n")
        sb.append("Время     Сеть  Маршрут    Правило      Адрес\n")
        for (e in all) {
            sb.append(e.time).append("  ").append(e.network).append("   ")
                .append(e.route.ru.padEnd(10)).append(' ')
                .append((if (e.byDefault) "по умолч." else "правило").padEnd(12)).append(' ')
                .append(e.host).append(':').append(e.port).append('\n')
        }
        return sb.toString()
    }

    fun exportFileName(): String =
        "LifeVPN-routing-" + SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.ROOT).format(Date()) + ".txt"
}
