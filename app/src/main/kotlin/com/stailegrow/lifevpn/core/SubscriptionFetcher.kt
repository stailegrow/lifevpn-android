package com.stailegrow.lifevpn.core

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Загрузка и разбор подписки — Android-аналог Core/SubscriptionFetcher.swift.
 *
 * Панели отдают список по-разному: то простым текстом, то base64 целиком, то
 * url-safe base64 без выравнивания. Часть панелей смотрит на User-Agent и
 * незнакомому клиенту подсовывает формат другого клиента — поэтому при
 * пустом результате пробуем ещё раз под распространёнными именами.
 */
object SubscriptionFetcher {

    data class Payload(
        val links: List<String> = emptyList(),
        val title: String? = null,
        val announce: String? = null,
        val updateIntervalHours: Double? = null,
        val usedBytes: Long? = null,
        val totalBytes: Long? = null,
        val expiresAt: Long? = null,
        val rawUserInfo: String? = null,
    )

    open class FetchException(message: String) : Exception(message)

    internal class ResponseTooLarge : FetchException(L.t("Ответ подписки слишком большой.", "Subscription response is too large."))
    internal class TooManyLinks : FetchException(L.t("В подписке слишком много ссылок.", "Subscription contains too many links."))
    internal const val MAX_SUBSCRIPTION_BYTES = 8 * 1024 * 1024

    private val userAgents = listOf(
        "LifeVPN/${com.stailegrow.lifevpn.BuildConfig.VERSION_NAME}",
        "v2rayNG/1.9.5",
        "Happ/1.0",
    )

    internal const val MAX_REDIRECTS = 5
    private const val MAX_HEADER_BYTES = 64 * 1024

    /**
     * Сетевые зависимости — подменяются в юнит-тестах. `resolve` вызывается
     * ровно один раз на каждый хост цепочки: проверенный адрес и адрес, на
     * который реально идёт соединение, — один и тот же объект, повторного
     * независимого резолва (окна для DNS rebinding) нет.
     */
    internal interface Transport {
        fun resolve(host: String): List<InetAddress>
        fun open(address: InetAddress, port: Int, host: String, timeoutMs: Int): Socket
    }

    /**
     * Обычный транспорт: системный резолвер, TCP на проверенный адрес, TLS
     * поверх него с SNI = имя хоста, системным хранилищем сертификатов и
     * штатной проверкой имени. Никаких trust-all и пропуска проверок.
     */
    internal object SystemTransport : Transport {
        override fun resolve(host: String): List<InetAddress> = InetAddress.getAllByName(host).toList()

        override fun open(address: InetAddress, port: Int, host: String, timeoutMs: Int): Socket {
            val raw = Socket()
            try {
                raw.connect(InetSocketAddress(address, port), timeoutMs)
                raw.soTimeout = timeoutMs
                val tls = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                    .createSocket(raw, host, port, true) as SSLSocket
                tls.soTimeout = timeoutMs
                tls.startHandshake()
                if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(host, tls.session)) {
                    tls.close()
                    throw FetchException(L.t("Сертификат сервера подписки не подходит к его адресу.", "The subscription server certificate does not match its address."))
                }
                return tls
            } catch (e: Exception) {
                try { raw.close() } catch (_: Exception) {}
                throw e
            }
        }
    }

    @Volatile internal var transport: Transport = SystemTransport

    /** Блокирующий вызов — звать только с Dispatchers.IO. */
    fun fetch(urlString: String, timeoutMs: Int = 20000): Payload {
        val trimmed = urlString.trim()
        val url = try {
            URL(trimmed)
        } catch (e: Exception) {
            throw FetchException(L.t("Это не похоже на ссылку подписки.", "This does not look like a subscription link."))
        }
        val scheme = url.protocol?.lowercase()
        if (scheme != "http" && scheme != "https") {
            throw FetchException(L.t("Это не похоже на ссылку подписки.", "This does not look like a subscription link."))
        }
        requireSecure(url)

        var lastFailure: FetchException? = null
        for (agent in userAgents) {
            try {
                val payload = load(url, agent, timeoutMs)
                if (payload.links.isNotEmpty()) return payload
                lastFailure = FetchException(L.t("Подписка ответила, но ни одной ссылки в ответе нет.", "The subscription answered, but the reply holds no links."))
            } catch (e: ResponseTooLarge) {
                throw e
            } catch (e: TooManyLinks) {
                throw e
            } catch (e: InterruptedException) {
                throw e
            } catch (e: FetchException) {
                lastFailure = e
            } catch (e: Exception) {
                lastFailure = FetchException(e.message ?: L.t("Не удалось загрузить подписку.", "Could not load the subscription."))
            }
        }
        throw lastFailure ?: FetchException(L.t("Не удалось загрузить подписку.", "Could not load the subscription."))
    }

    /** Только HTTPS: по голому http список узлов может подменить кто угодно на пути. */
    internal fun requireSecure(url: URL) {
        if (url.protocol?.lowercase() != "https" || url.host.isNullOrEmpty()) {
            throw FetchException(
                L.t(
                    "Для подписки нужна защищённая ссылка https://. Запрос по незащищённому адресу не отправлен, прежние узлы сохранены.",
                    "A secure https:// subscription link is required. No request was sent to the insecure address; existing nodes are kept.",
                ),
            )
        }
    }

    /**
     * Адреса, на которые ссылка подписки вести не может: loopback, частные,
     * link-local, CGNAT, unspecified, multicast, зарезервированные, ULA IPv6
     * и IPv6-формы, внутри которых спрятан такой IPv4 (::ffff:a.b.c.d,
     * ::a.b.c.d, 64:ff9b::/96, 6to4 2002::/16).
     */
    internal fun isForbiddenAddress(address: InetAddress): Boolean {
        if (address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress ||
            address.isAnyLocalAddress || address.isMulticastAddress
        ) return true
        val b = address.address
        if (b.size == 4) return isForbiddenIPv4(b)
        if (b.size != 16) return true
        val u = IntArray(16) { b[it].toInt() and 0xFF }
        if ((u[0] and 0xFE) == 0xFC) return true                          // fc00::/7 ULA
        if (u[0] == 0xFE && (u[1] and 0xC0) == 0x80) return true            // fe80::/10
        if (u[0] == 0xFE && (u[1] and 0xC0) == 0xC0) return true            // fec0::/10
        if (u[0] == 0x20 && u[1] == 0x01 && u[2] == 0x0D && u[3] == 0xB8) return true // 2001:db8::/32
        val first10Zero = (0 until 10).all { u[it] == 0 }
        if (first10Zero && u[10] == 0xFF && u[11] == 0xFF) return isForbiddenIPv4(b.copyOfRange(12, 16))
        if ((0 until 12).all { u[it] == 0 }) return true                    // ::a.b.c.d и прочие ::/96
        if (u[0] == 0x00 && u[1] == 0x64 && u[2] == 0xFF && u[3] == 0x9B && (4 until 12).all { u[it] == 0 }) {
            return isForbiddenIPv4(b.copyOfRange(12, 16))                   // 64:ff9b::/96 NAT64
        }
        if (u[0] == 0x20 && u[1] == 0x02) return isForbiddenIPv4(b.copyOfRange(2, 6)) // 2002::/16 6to4
        return false
    }

    private fun isForbiddenIPv4(b: ByteArray): Boolean {
        val a0 = b[0].toInt() and 0xFF
        val a1 = b[1].toInt() and 0xFF
        return a0 == 0 || a0 == 10 || a0 == 127 ||
            (a0 == 100 && a1 in 64..127) ||          // 100.64.0.0/10 CGNAT
            (a0 == 169 && a1 == 254) ||
            (a0 == 172 && a1 in 16..31) ||
            (a0 == 192 && a1 == 168) ||
            (a0 == 192 && a1 == 0 && (b[2].toInt() and 0xFF) in setOf(0, 2)) ||
            (a0 == 198 && a1 in 18..19) ||
            (a0 == 198 && a1 == 51 && (b[2].toInt() and 0xFF) == 100) ||
            (a0 == 203 && a1 == 0 && (b[2].toInt() and 0xFF) == 113) ||
            a0 >= 224
    }

    /** Резолвит хост один раз. Ошибка резолва — не разрешение, а ошибка. */
    internal fun vettedAddresses(host: String): List<InetAddress> {
        val addresses = try {
            transport.resolve(host)
        } catch (e: Exception) {
            throw FetchException(L.t("Не удалось найти сервер подписки ($host).", "Could not resolve the subscription server ($host)."))
        }
        if (addresses.isEmpty()) {
            throw FetchException(L.t("Не удалось найти сервер подписки ($host).", "Could not resolve the subscription server ($host)."))
        }
        // Хватит одного «внутреннего» адреса среди ответов: смешанная выдача —
        // признак попытки увести запрос в локальную сеть.
        if (addresses.any(::isForbiddenAddress)) {
            throw FetchException(L.t("Ссылки на локальную сеть не поддерживаются.", "Links to the local network are not supported."))
        }
        return addresses
    }

    internal class Response(val code: Int, val headers: Map<String, String>, val body: ByteArray)

    /**
     * Загрузка с ручной обработкой перенаправлений: каждый адрес цепочки
     * проверяется ДО запроса (только HTTPS, публичный адрес), не больше
     * MAX_REDIRECTS шагов, общий срок на всю цепочку.
     */
    internal fun request(start: URL, userAgent: String, timeoutMs: Int): Response {
        val deadline = System.nanoTime() + timeoutMs.toLong() * 2 * 1_000_000
        var url = start
        for (step in 0..MAX_REDIRECTS) {
            requireSecure(url)
            val response = exchange(url, userAgent, timeoutMs, deadline)
            if (response.code !in setOf(301, 302, 303, 307, 308)) return response
            if (step == MAX_REDIRECTS) {
                throw FetchException(L.t("Слишком много перенаправлений при загрузке подписки.", "Too many redirects while loading the subscription."))
            }
            val location = response.headers["location"]
                ?: throw FetchException(L.t("Сервер подписки прислал перенаправление без адреса.", "The subscription server sent a redirect without an address."))
            url = try {
                URL(url, location)
            } catch (e: Exception) {
                throw FetchException(L.t("Сервер подписки прислал неверный адрес перенаправления.", "The subscription server sent an invalid redirect address."))
            }
        }
        throw FetchException(L.t("Слишком много перенаправлений при загрузке подписки.", "Too many redirects while loading the subscription."))
    }

    private fun remainingMs(deadline: Long, timeoutMs: Int): Int {
        val left = (deadline - System.nanoTime()) / 1_000_000
        if (left <= 0) throw FetchException(L.t("Сервер подписки не ответил вовремя.", "The subscription server did not answer in time."))
        return minOf(left, timeoutMs.toLong()).toInt().coerceAtLeast(1)
    }

    private fun exchange(url: URL, userAgent: String, timeoutMs: Int, deadline: Long): Response {
        val host = url.host
        val port = if (url.port > 0) url.port else 443
        var lastError: Exception? = null
        for (address in vettedAddresses(host)) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            val socket = try {
                transport.open(address, port, host, remainingMs(deadline, timeoutMs))
            } catch (e: FetchException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                continue
            }
            socket.use {
                val path = (url.path.ifEmpty { "/" }) + (url.query?.let { "?$it" } ?: "")
                val hostHeader = if (url.port > 0 && url.port != 443) "$host:${url.port}" else host
                val head = "GET $path HTTP/1.1\r\n" +
                    "Host: $hostHeader\r\n" +
                    "User-Agent: $userAgent\r\n" +
                    "Accept: */*\r\n" +
                    "Accept-Encoding: identity\r\n" +
                    "Cache-Control: no-cache\r\n" +
                    "Connection: close\r\n\r\n"
                it.getOutputStream().apply { write(head.toByteArray(Charsets.UTF_8)); flush() }
                it.soTimeout = remainingMs(deadline, timeoutMs)
                return readResponse(it.getInputStream())
            }
        }
        throw FetchException(lastError?.message ?: L.t("Не удалось подключиться к серверу подписки.", "Could not connect to the subscription server."))
    }

    /** Разбор ответа HTTP/1.x: статус, заголовки, тело (chunked, Content-Length
     *  или до закрытия). Тело ограничено MAX_SUBSCRIPTION_BYTES. */
    internal fun readResponse(input: InputStream, limit: Int = MAX_SUBSCRIPTION_BYTES): Response {
        val stream = java.io.BufferedInputStream(input)
        val status = readLine(stream) ?: throw FetchException(L.t("Сервер подписки закрыл соединение без ответа.", "The subscription server closed the connection without a reply."))
        val parts = status.split(' ', limit = 3)
        if (parts.size < 2 || !parts[0].startsWith("HTTP/1.")) throw FetchException(L.t("Сервер подписки ответил не по HTTP.", "The subscription server did not reply over HTTP."))
        val code = parts[1].toIntOrNull() ?: throw FetchException(L.t("Сервер подписки ответил не по HTTP.", "The subscription server did not reply over HTTP."))
        val headers = LinkedHashMap<String, String>()
        var headerBytes = 0
        while (true) {
            val line = readLine(stream) ?: break
            if (line.isEmpty()) break
            headerBytes += line.length
            if (headerBytes > MAX_HEADER_BYTES) throw ResponseTooLarge()
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val name = line.substring(0, colon).trim().lowercase()
            val value = line.substring(colon + 1).trim()
            headers[name] = headers[name]?.let { "$it, $value" } ?: value
        }
        if (code in setOf(301, 302, 303, 307, 308) || code !in 200..299) return Response(code, headers, ByteArray(0))
        val encoding = headers["content-encoding"]?.lowercase()
        if (encoding != null && encoding != "identity") {
            throw FetchException(L.t("Сервер подписки прислал сжатый ответ, хотя сжатие не запрашивалось.", "The subscription server sent a compressed reply that was not requested."))
        }
        val body = when {
            headers["transfer-encoding"]?.lowercase()?.contains("chunked") == true -> readChunked(stream, limit)
            headers["content-length"] != null -> {
                val length = headers["content-length"]!!.toLongOrNull()?.takeIf { it >= 0 }
                    ?: throw FetchException(L.t("Сервер подписки прислал неверный Content-Length.", "The subscription server sent an invalid Content-Length."))
                if (length > limit) throw ResponseTooLarge()
                val data = readExact(stream, length.toInt())
                if (data.size.toLong() != length) throw FetchException(L.t("Ответ подписки оборвался.", "The subscription reply was cut short."))
                data
            }
            else -> readBounded(stream, limit)
        }
        return Response(code, headers, body)
    }

    private fun readLine(stream: InputStream): String? {
        val out = ByteArrayOutputStream()
        while (true) {
            val c = stream.read()
            if (c < 0) return if (out.size() == 0) null else out.toString("UTF-8")
            if (c == '\n'.code) break
            if (c != '\r'.code) out.write(c)
            if (out.size() > MAX_HEADER_BYTES) throw ResponseTooLarge()
        }
        return out.toString("UTF-8")
    }

    private fun readChunked(stream: InputStream, limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        while (true) {
            val sizeLine = readLine(stream) ?: throw FetchException(L.t("Ответ подписки оборвался.", "The subscription reply was cut short."))
            val size = sizeLine.substringBefore(';').trim().toLongOrNull(16)
                ?: throw FetchException(L.t("Ответ подписки повреждён.", "The subscription reply is damaged."))
            if (size < 0) throw FetchException(L.t("Ответ подписки повреждён.", "The subscription reply is damaged."))
            if (size == 0L) break
            if (out.size() + size > limit) throw ResponseTooLarge()
            val chunk = readExact(stream, size.toInt())
            if (chunk.size.toLong() != size) throw FetchException(L.t("Ответ подписки оборвался.", "The subscription reply was cut short."))
            out.write(chunk)
            readLine(stream)
        }
        return out.toByteArray()
    }

    /** Ровно `count` байт (или меньше, если поток кончился раньше). */
    private fun readExact(input: InputStream, count: Int): ByteArray {
        val out = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val n = input.read(out, offset, count - offset)
            if (n < 0) break
            offset += n
        }
        return if (offset == count) out else out.copyOf(offset)
    }

    /** Читает не больше `limit` байт; если поток длиннее — ResponseTooLarge. */
    internal fun readBounded(input: InputStream, limit: Int = MAX_SUBSCRIPTION_BYTES): ByteArray {
        require(limit >= 0)
        val output = ByteArrayOutputStream(minOf(limit, 8192))
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = input.read(buffer, 0, minOf(buffer.size, limit - total + 1))
            if (count < 0) break
            if (count == 0) continue
            if (count > limit - total) throw ResponseTooLarge()
            output.write(buffer, 0, count)
            total += count
        }
        return output.toByteArray()
    }

    private fun load(url: URL, userAgent: String, timeoutMs: Int): Payload {
        val response = request(url, userAgent, timeoutMs)
        val code = response.code
        if (code !in 200..299) throw FetchException(L.t("Сервер подписки ответил кодом $code.", "The subscription server replied with code $code."))

        val body = response.body.toString(Charsets.UTF_8)
        var payload = Payload(links = decodeBody(body))
        payload = payload.copy(
            title = header(response, "profile-title")?.let(::decodeHeaderValue),
            announce = header(response, "announce")?.let(::decodeHeaderValue),
        )
        header(response, "subscription-userinfo")?.let { info ->
            val (used, total, expiresAt) = parseUserInfo(info)
            payload = payload.copy(rawUserInfo = info, usedBytes = used, totalBytes = total, expiresAt = expiresAt)
        }
        payload = payload.copy(updateIntervalHours = parseUpdateInterval(header(response, "profile-update-interval")))
        return payload
    }

    private fun header(response: Response, name: String): String? =
        response.headers[name.lowercase()]?.trim()?.takeIf { it.isNotEmpty() }

    // MARK: - Разбор тела

    fun decodeBody(body: String): List<String> {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (trimmed.contains("://")) return links(trimmed)
        decodeBase64(trimmed)?.let { return links(it) }
        return emptyList()
    }

    private fun links(text: String): List<String> {
        val result = ArrayList<String>()
        for (line in text.lineSequence()) {
            val value = line.trim()
            if (value.isEmpty() || value.startsWith("#") || !value.contains("://")) continue
            if (result.size >= 10_000) throw TooManyLinks()
            result.add(value)
        }
        return result
    }

    private const val BASE64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    /**
     * Своя реализация, а не java.util.Base64 (API 26+) или android.util.Base64
     * (недоступен в чистых JVM юнит-тестах) — так работает одинаково и на
     * minSdk 24, и под JUnit без Robolectric.
     */
    fun decodeBase64(value: String): String? {
        val normalized = value
            .replace('-', '+')
            .replace('_', '/')
            .filterNot { it.isWhitespace() }
            .trimEnd('=')
        if (normalized.isEmpty() || normalized.any { it !in BASE64_ALPHABET }) return null

        val output = ByteArrayOutputStream()
        var buffer = 0
        var bitsCollected = 0
        for (c in normalized) {
            buffer = (buffer shl 6) or BASE64_ALPHABET.indexOf(c)
            bitsCollected += 6
            if (bitsCollected >= 8) {
                bitsCollected -= 8
                output.write((buffer shr bitsCollected) and 0xFF)
            }
        }
        return try {
            String(output.toByteArray(), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    /** Заголовки с не-ASCII панели присылают как `base64:<...>`. */
    fun decodeHeaderValue(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.lowercase().startsWith("base64:")) {
            val payload = trimmed.substring("base64:".length)
            return decodeBase64(payload) ?: payload
        }
        return trimmed.ifEmpty { null }
    }

    internal fun parseUpdateInterval(value: String?): Double? {
        val days = value?.trim()?.toDoubleOrNull() ?: return null
        val hours = days * 24
        return hours.takeIf { days > 0 && it > 0 && (it * 3600_000).isFinite() }
    }

    /** `upload=1; download=2; total=3; expire=1767225600` -> (used, total, expiresAtMillis) */
    fun parseUserInfo(value: String): Triple<Long?, Long?, Long?> {
        val fields = mutableMapOf<String, Long?>()
        for (pair in value.split(";")) {
            val parts = pair.split("=", limit = 2)
            if (parts.size != 2) continue
            val key = parts[0].trim().lowercase()
            fields[key] = parts[1].trim().toLongOrNull()?.takeIf { it >= 0 }
        }
        val hasTraffic = fields.containsKey("upload") || fields.containsKey("download")
        val upload = if (fields.containsKey("upload")) fields["upload"] else 0L
        val download = if (fields.containsKey("download")) fields["download"] else 0L
        val used = if (hasTraffic && upload != null && download != null && upload <= Long.MAX_VALUE - download) upload + download else null
        val total = fields["total"]
        // expire=0 у панелей значит «бессрочно», а не 1970 год.
        val expiresAt = fields["expire"]?.takeIf { it > 0 && it <= Long.MAX_VALUE / 1000 }?.let { it * 1000 }
        return Triple(used, total, expiresAt)
    }
}
