package com.stailegrow.lifevpn.core

import com.stailegrow.lifevpn.vpn.LifeVpnService
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * HTTPS GET мимо собственного туннеля — запасной путь для скачивания
 * обновления, когда путь через VPN-сервер даёт 503/504 или таймауты.
 *
 * Обычный HttpURLConnection нашего процесса всегда идёт через TUN (сам
 * процесс из VPN не исключён), а сокету, созданному здесь, перед
 * соединением делается LifeVpnService.protectSocket() — тот же приём, что у
 * замера задержки (PingTester). Если VPN выключен, это просто обычное
 * соединение. TLS — системный: хранилище сертификатов и проверка имени
 * хоста штатные. Перенаправления — только https, не больше MAX_REDIRECTS.
 * Подлинность файла от пути не зависит: APK всё равно проверяется по
 * сертификату издателя (UpdateChecker.verifyDownloadedApk).
 */
internal object DirectHttp {

    class Response(val code: Int, val contentLength: Long, val body: InputStream, private val socket: Socket) : AutoCloseable {
        override fun close() {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private class Reply(val response: Response, val location: String?)

    private const val MAX_REDIRECTS = 5
    private const val MAX_HEADER_BYTES = 64 * 1024
    private val REDIRECTS = setOf(301, 302, 303, 307, 308)

    fun get(start: URL, userAgent: String, timeoutMs: Int): Response {
        var url = start
        for (step in 0..MAX_REDIRECTS) {
            if (url.protocol?.lowercase() != "https" || url.host.isNullOrEmpty()) {
                throw IOException("insecure redirect")
            }
            val reply = exchange(url, userAgent, timeoutMs)
            if (reply.response.code !in REDIRECTS) return reply.response
            reply.response.close()
            val location = reply.location
            if (location == null || step == MAX_REDIRECTS) throw IOException("bad redirect")
            url = URL(url, location)
        }
        throw IOException("too many redirects")
    }

    private fun exchange(url: URL, userAgent: String, timeoutMs: Int): Reply {
        val host = url.host
        val port = if (url.port > 0) url.port else 443
        val raw = Socket()
        try {
            LifeVpnService.protectSocket(raw)
            raw.connect(InetSocketAddress(host, port), timeoutMs)
            raw.soTimeout = timeoutMs
            val tls = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                .createSocket(raw, host, port, true) as SSLSocket
            tls.soTimeout = timeoutMs
            tls.startHandshake()
            if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(host, tls.session)) {
                throw IOException("certificate does not match $host")
            }
            val path = (url.path.ifEmpty { "/" }) + (url.query?.let { "?$it" } ?: "")
            val head = "GET $path HTTP/1.1\r\n" +
                "Host: $host\r\n" +
                "User-Agent: $userAgent\r\n" +
                "Accept: */*\r\n" +
                "Accept-Encoding: identity\r\n" +
                "Connection: close\r\n\r\n"
            tls.getOutputStream().apply { write(head.toByteArray(Charsets.UTF_8)); flush() }

            val input = BufferedInputStream(tls.getInputStream())
            val status = readLine(input) ?: throw IOException("connection closed without a reply")
            val parts = status.split(' ', limit = 3)
            if (parts.size < 2 || !parts[0].startsWith("HTTP/1.")) throw IOException("not an HTTP reply")
            val code = parts[1].toIntOrNull() ?: throw IOException("not an HTTP reply")
            val headers = HashMap<String, String>()
            var total = 0
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                total += line.length
                if (total > MAX_HEADER_BYTES) throw IOException("headers too large")
                val colon = line.indexOf(':')
                if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
            }
            val chunked = headers["transfer-encoding"]?.lowercase()?.contains("chunked") == true
            val length = if (chunked) -1L else headers["content-length"]?.toLongOrNull() ?: -1L
            val body: InputStream = when {
                chunked -> ChunkedInputStream(input)
                length >= 0 -> BoundedInputStream(input, length)
                else -> input
            }
            return Reply(Response(code, length, body, tls), headers["location"])
        } catch (e: Exception) {
            try { raw.close() } catch (_: Exception) {}
            throw e
        }
    }

    internal fun readLine(stream: InputStream): String? {
        val out = ByteArrayOutputStream()
        while (true) {
            val c = stream.read()
            if (c < 0) return if (out.size() == 0) null else out.toString("UTF-8")
            if (c == '\n'.code) break
            if (c != '\r'.code) out.write(c)
            if (out.size() > MAX_HEADER_BYTES) throw IOException("line too long")
        }
        return out.toString("UTF-8")
    }

    /** Ровно `left` байт тела, дальше — конец потока. */
    internal class BoundedInputStream(private val input: InputStream, private var left: Long) : InputStream() {
        override fun read(): Int {
            if (left <= 0) return -1
            val c = input.read()
            if (c >= 0) left--
            return c
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (left <= 0) return -1
            val n = input.read(b, off, minOf(len.toLong(), left).toInt())
            if (n > 0) left -= n
            return n
        }
    }

    /** Тело в chunked-кодировке. */
    internal class ChunkedInputStream(private val input: InputStream) : InputStream() {
        private var left = 0L
        private var finished = false

        private fun nextChunk(): Boolean {
            if (finished) return false
            val line = readLine(input) ?: throw IOException("chunked body cut short")
            val size = line.substringBefore(';').trim().toLongOrNull(16) ?: throw IOException("bad chunk size")
            if (size < 0) throw IOException("bad chunk size")
            if (size == 0L) {
                finished = true
                return false
            }
            left = size
            return true
        }

        override fun read(): Int {
            val buf = ByteArray(1)
            val n = read(buf, 0, 1)
            return if (n < 0) -1 else buf[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (left == 0L && !nextChunk()) return -1
            val n = input.read(b, off, minOf(len.toLong(), left).toInt())
            if (n < 0) throw IOException("chunked body cut short")
            left -= n
            if (left == 0L) readLine(input)
            return n
        }
    }
}
