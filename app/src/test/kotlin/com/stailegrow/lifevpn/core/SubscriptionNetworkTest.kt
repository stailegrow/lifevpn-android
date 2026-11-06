package com.stailegrow.lifevpn.core

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket
import java.net.URL

/**
 * B03: HTTPS, ручные перенаправления, проверка фактического адреса
 * соединения (без повторного резолва) — на поддельном транспорте.
 */
class SubscriptionNetworkTest {

    private class FakeSocket(private val reply: ByteArray) : Socket() {
        val sent = ByteArrayOutputStream()
        override fun getInputStream(): InputStream = ByteArrayInputStream(reply)
        override fun getOutputStream(): OutputStream = sent
        override fun close() {}
        override fun setSoTimeout(timeout: Int) {}
    }

    private class FakeTransport(
        val dns: MutableMap<String, List<String>>,
        val replies: Map<String, String>,
    ) : SubscriptionFetcher.Transport {
        val resolved = mutableListOf<String>()
        val dialed = mutableListOf<Pair<String, String>>()
        override fun resolve(host: String): List<InetAddress> {
            resolved.add(host)
            val list = dns[host] ?: throw java.net.UnknownHostException(host)
            return list.map { InetAddress.getByAddress(host, InetAddress.getByName(it).address) }
        }
        override fun open(address: InetAddress, port: Int, host: String, timeoutMs: Int): Socket {
            dialed.add(host to address.hostAddress!!)
            return FakeSocket((replies[host] ?: "HTTP/1.1 500 X\r\n\r\n").toByteArray())
        }
    }

    private fun ok(body: String, extra: String = "") =
        "HTTP/1.1 200 OK\r\nContent-Length: ${body.toByteArray().size}\r\n$extra\r\n$body"

    @After fun restore() { SubscriptionFetcher.transport = SubscriptionFetcher.SystemTransport }

    private fun install(dns: Map<String, List<String>>, replies: Map<String, String>): FakeTransport =
        FakeTransport(dns.toMutableMap(), replies).also { SubscriptionFetcher.transport = it }

    @Test fun `plain http is rejected before any network access`() {
        val t = install(mapOf("a.example" to listOf("93.184.216.34")), emptyMap())
        assertThrows(SubscriptionFetcher.FetchException::class.java) { SubscriptionFetcher.fetch("http://a.example/sub") }
        assertTrue(t.resolved.isEmpty())
        assertTrue(t.dialed.isEmpty())
    }

    @Test fun `https fetch dials exactly the vetted address once`() {
        val t = install(mapOf("a.example" to listOf("93.184.216.34")),
            mapOf("a.example" to ok("vless://u@h.example:443\n", "subscription-userinfo: upload=1; download=2\r\n")))
        val payload = SubscriptionFetcher.fetch("https://a.example/sub?token=x")
        assertEquals(listOf("vless://u@h.example:443"), payload.links)
        assertEquals(3L, payload.usedBytes)
        assertEquals(listOf("a.example"), t.resolved)
        assertEquals(listOf("a.example" to "93.184.216.34"), t.dialed)
    }

    @Test fun `private ipv4 and ipv6 destinations are rejected`() {
        for (ip in listOf("127.0.0.1", "10.1.2.3", "172.16.0.1", "192.168.1.1", "169.254.169.254", "100.64.0.1",
            "0.0.0.0", "::1", "fe80::1", "fd00::1", "::ffff:192.168.1.1", "64:ff9b::a00:1", "2002:c0a8:101::1", "224.0.0.1")) {
            assertTrue(ip, SubscriptionFetcher.isForbiddenAddress(InetAddress.getByName(ip)))
        }
        for (ip in listOf("93.184.216.34", "1.1.1.1", "2606:4700:4700::1111")) {
            assertFalse(ip, SubscriptionFetcher.isForbiddenAddress(InetAddress.getByName(ip)))
        }
        val t = install(mapOf("evil.example" to listOf("93.184.216.34", "192.168.1.1")), emptyMap())
        assertThrows(SubscriptionFetcher.FetchException::class.java) { SubscriptionFetcher.fetch("https://evil.example/s") }
        assertTrue("смешанная выдача не подключается вовсе", t.dialed.isEmpty())
    }

    @Test fun `dns failure is an error not a permission`() {
        val t = install(emptyMap(), emptyMap())
        assertThrows(SubscriptionFetcher.FetchException::class.java) { SubscriptionFetcher.fetch("https://missing.example/s") }
        assertTrue(t.dialed.isEmpty())
    }

    @Test fun `redirect to https cdn is followed and re-vetted`() {
        val t = install(
            mapOf("a.example" to listOf("93.184.216.34"), "cdn.example" to listOf("151.101.1.1")),
            mapOf("a.example" to "HTTP/1.1 302 Found\r\nLocation: https://cdn.example/final\r\n\r\n",
                "cdn.example" to ok("vless://u@h.example:443")),
        )
        assertEquals(1, SubscriptionFetcher.fetch("https://a.example/s").links.size)
        assertEquals(listOf("a.example" to "93.184.216.34", "cdn.example" to "151.101.1.1"), t.dialed.take(2))
    }

    @Test fun `redirect downgrade and private rebinding are blocked before dialing`() {
        var t = install(mapOf("a.example" to listOf("93.184.216.34")),
            mapOf("a.example" to "HTTP/1.1 301 Moved\r\nLocation: http://a.example/plain\r\n\r\n"))
        assertThrows(SubscriptionFetcher.FetchException::class.java) { SubscriptionFetcher.fetch("https://a.example/s") }
        assertTrue(t.dialed.all { it.first == "a.example" })

        t = install(mapOf("a.example" to listOf("93.184.216.34"), "inner.example" to listOf("10.0.0.5")),
            mapOf("a.example" to "HTTP/1.1 307 Temp\r\nLocation: https://inner.example/x\r\n\r\n"))
        assertThrows(SubscriptionFetcher.FetchException::class.java) { SubscriptionFetcher.fetch("https://a.example/s") }
        assertTrue("внутренний адрес не набирался", t.dialed.none { it.first == "inner.example" })
    }

    @Test fun `redirect loop is bounded`() {
        val t = install(mapOf("a.example" to listOf("93.184.216.34")),
            mapOf("a.example" to "HTTP/1.1 302 Found\r\nLocation: /s\r\n\r\n"))
        assertThrows(SubscriptionFetcher.FetchException::class.java) { SubscriptionFetcher.request(URL("https://a.example/s"), "t", 1000) }
        assertEquals(SubscriptionFetcher.MAX_REDIRECTS + 1, t.dialed.size)
    }

    @Test fun `chunked and length-less bodies are read with the 8 MiB limit`() {
        val chunked = "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n5\r\nhello\r\n6\r\n world\r\n0\r\n\r\n"
        assertArrayEquals("hello world".toByteArray(), SubscriptionFetcher.readResponse(ByteArrayInputStream(chunked.toByteArray())).body)
        val open = "HTTP/1.1 200 OK\r\n\r\n" + "a".repeat(100)
        assertEquals(100, SubscriptionFetcher.readResponse(ByteArrayInputStream(open.toByteArray())).body.size)
        assertThrows(SubscriptionFetcher.ResponseTooLarge::class.java) {
            SubscriptionFetcher.readResponse(ByteArrayInputStream(("HTTP/1.1 200 OK\r\n\r\n" + "a".repeat(65)).toByteArray()), 64)
        }
        assertThrows(SubscriptionFetcher.ResponseTooLarge::class.java) {
            SubscriptionFetcher.readResponse(ByteArrayInputStream("HTTP/1.1 200 OK\r\nContent-Length: 65\r\n\r\n".toByteArray()), 64)
        }
        assertThrows(SubscriptionFetcher.ResponseTooLarge::class.java) {
            val big = "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n41\r\n" + "a".repeat(65) + "\r\n0\r\n\r\n"
            SubscriptionFetcher.readResponse(ByteArrayInputStream(big.toByteArray()), 64)
        }
        assertThrows(SubscriptionFetcher.FetchException::class.java) {
            SubscriptionFetcher.readResponse(ByteArrayInputStream("HTTP/1.1 200 OK\r\nContent-Encoding: gzip\r\n\r\nx".toByteArray()))
        }
    }

    @Test fun `request sets host header and identity encoding`() {
        val t = install(mapOf("a.example" to listOf("93.184.216.34")), mapOf("a.example" to ok("vless://u@h.example:443")))
        var socket: FakeSocket? = null
        SubscriptionFetcher.transport = object : SubscriptionFetcher.Transport by t {
            override fun open(address: InetAddress, port: Int, host: String, timeoutMs: Int): Socket =
                (t.open(address, port, host, timeoutMs) as FakeSocket).also { socket = it }
        }
        SubscriptionFetcher.fetch("https://a.example/s?x=1")
        val sent = socket!!.sent.toString("UTF-8")
        assertTrue(sent.startsWith("GET /s?x=1 HTTP/1.1\r\n"))
        assertTrue(sent.contains("Host: a.example\r\n"))
        assertTrue(sent.contains("Accept-Encoding: identity\r\n"))
    }
}
