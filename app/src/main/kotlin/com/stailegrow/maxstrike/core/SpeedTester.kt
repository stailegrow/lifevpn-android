package com.stailegrow.maxstrike.core

import java.net.HttpURLConnection
import java.net.URL

/**
 * Замер скорости — карточка "// СКОРОСТЬ" на главном экране. Тот же
 * принцип, что у IPChecker: наш процесс не исключён из VPN-маршрута, так
 * что обычный HTTP-запрос из приложения сам едет через TUN, пока туннель
 * поднят — никакого отдельного сокета на socksPort/httpPort заводить не
 * нужно.
 *
 * Источники теста — сразу несколько, пробуем по очереди, пока один не
 * сработает (см. CANDIDATES). Раньше был единственный источник
 * (fsn1-speed.hetzner.com), и он честно работал, пока проверялся только
 * при поднятом VPN — но пользователь сообщил, что без VPN замер вообще не
 * идёт. Причина — тот же класс проблемы, что уже нашли на PingTester:
 * Hetzner активно используется под VPN/прокси-инфраструктуру, и часть его
 * подсетей блокируется на уровне российских операторов/DPI напрямую (без
 * туннеля запрос идёт голым с обычного мобильного IP прямо на Hetzner —
 * и обрывается); через VPN тот же запрос уходит зашифрованным на СВОЙ
 * VLESS-сервер, а до Hetzner долетает уже из дата-центра, где эту блокировку
 * встретить неоткуда — поэтому раньше и работало только с VPN.
 *
 * У Cloudflare (speed.cloudflare.com/__down) ровно обратная картина: сам
 * сервис не блокируется в РФ (это популярнейший мировой CDN, его массово
 * используют и российские сайты), но их встроенная защита от ботов
 * блокирует запросы именно с IP датацентров/прокси-провайдеров — то есть
 * стабильно давала 403 именно когда запрос шёл через VLESS-сервер (тоже
 * датацентровый IP), а с обычного мобильного IP без VPN должна отвечать
 * нормально. Ровно дополняет Hetzner: где не работает один — должен
 * работать другой. Третий источник (CacheFly) — старый общеизвестный
 * публичный тестовый хостинг, много лет используется сторонними
 * скорость-тестами именно как раз потому, что почти нигде не блокируется;
 * он тут просто на случай, если оба первых источника внезапно откажут
 * одновременно на какой-то конкретной сети.
 *
 * Полные файлы у всех источников — десятки-сотни МБ, качать целиком не
 * нужно: читаем, пока не наберём достаточно данных или не кончится
 * MAX_DURATION_MS, что раньше, и просто прерываем поток.
 *
 * Блокирующий вызов — звать только с Dispatchers.IO.
 */
object SpeedTester {

    private data class Candidate(val url: String, val label: String)

    // Порядок важен: Cloudflare первым, потому что с обычного (без VPN)
    // мобильного IP он должен пройти почти всегда, а вот Hetzner именно в
    // этом случае у части операторов рискует не открыться вовсе — быстрее
    // получить рабочий результат с первой попытки, чем ждать таймаут.
    private val CANDIDATES = listOf(
        Candidate("https://speed.cloudflare.com/__down?bytes=104857600", "Cloudflare"),
        Candidate("https://fsn1-speed.hetzner.com/100MB.bin", "Hetzner"),
        Candidate("https://cachefly.cachefly.net/100mb.test", "CacheFly"),
    )

    private const val MAX_DURATION_MS = 10_000L

    // Раньше было общее значение (TIMEOUT_MS = 15000) и на подключение, и
    // на чтение. Раздельные тайм-ауты нужны именно из-за перебора
    // источников: если конкретный хост заблокирован (DPI просто роняет
    // SYN/ClientHello), это обычно видно уже на этапе установления
    // соединения — незачем ждать те же 15 секунд, что и на чтение данных
    // у медленной, но рабочей сети, прежде чем перейти к следующему
    // источнику из CANDIDATES.
    private const val CONNECT_TIMEOUT_MS = 6_000
    // Через VPN-туннель (Reality/XHTTP поверх TLS, да ещё и с самим
    // подключением к серверу до кучи) до первого байта уходит заметно
    // больше времени, чем при прямом соединении без VPN — 8 секунд были
    // слишком жёстким таймаутом и на не самой быстрой сети роняли замер
    // ещё до того, как он успевал толком начаться.
    private const val READ_TIMEOUT_MS = 15_000

    // Нашли причину «сервер вернул код 403»: HttpURLConnection по
    // умолчанию шлёт заголовок вида "User-Agent: Java/17.0.2" — Cloudflare
    // (как и многие другие WAF) блокирует именно такую сигнатуру как
    // подозрительную, ещё до того как запрос вообще доходит до раздачи
    // тестовых байт. Обычный браузерный User-Agent решает проблему.
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Mobile Safari/537.36"

    class SpeedTestException(message: String) : Exception(message)

    data class Result(val mbps: Double)

    fun measureBlocking(): Result {
        var lastError: SpeedTestException? = null
        for (candidate in CANDIDATES) {
            try {
                return measureFrom(candidate.url)
            } catch (e: SpeedTestException) {
                // Не тот источник — пробуем следующий из CANDIDATES.
                // Наружу отдаём ошибку только если отказали вообще все.
                lastError = e
            }
        }
        throw lastError ?: SpeedTestException(
            L.t("Не удалось измерить скорость ни на одном источнике.", "Could not measure speed on any source."),
        )
    }

    private fun measureFrom(url: String): Result {
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw SpeedTestException(L.t("Неверный адрес теста.", "Invalid test address."))
        }
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.setRequestProperty("User-Agent", USER_AGENT)

        try {
            // Раньше ошибка соединения/таймаут здесь вообще ничем не
            // ловились — наружу уходило сырое исключение вместо понятного
            // сообщения (или замер просто выглядел как «не работает»).
            val code = try {
                connection.responseCode
            } catch (e: java.io.IOException) {
                throw SpeedTestException(L.t("Не удалось подключиться к серверу замера (${e.message ?: "таймаут"}).", "Could not connect to the test server (${e.message ?: "timeout"})."))
            }
            if (code !in 200..299) throw SpeedTestException(L.t("Сервер вернул код $code.", "The server returned code $code."))

            val start = System.nanoTime()
            var totalBytes = 0L
            try {
                connection.inputStream.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val elapsedMs = (System.nanoTime() - start) / 1_000_000
                        if (elapsedMs > MAX_DURATION_MS) break
                        val read = input.read(buffer)
                        if (read == -1) break
                        totalBytes += read
                    }
                }
            } catch (e: java.io.IOException) {
                // Обрыв/таймаут посреди скачивания — если что-то уже успело
                // прийти, считаем замер по тому, что есть, вместо того чтобы
                // ронять весь результат из-за одного сбойного чтения.
                if (totalBytes <= 0) {
                    throw SpeedTestException(L.t("Соединение оборвалось раньше, чем пришли данные (${e.message ?: "таймаут"}).", "The connection dropped before any data arrived (${e.message ?: "timeout"})."))
                }
            }
            val elapsedSec = (System.nanoTime() - start) / 1_000_000_000.0
            if (totalBytes <= 0 || elapsedSec <= 0.0) {
                throw SpeedTestException(L.t("Пришло 0 байт — проверь соединение.", "0 bytes arrived — check the connection."))
            }

            val mbps = (totalBytes * 8) / elapsedSec / 1_000_000.0
            return Result(mbps)
        } finally {
            connection.disconnect()
        }
    }
}
