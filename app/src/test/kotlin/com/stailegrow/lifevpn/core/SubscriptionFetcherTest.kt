package com.stailegrow.lifevpn.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import java.io.ByteArrayInputStream
import org.junit.Test

class SubscriptionFetcherTest {

    @Test fun `invalid metadata cannot overflow counters or expiration`() {
        for (value in listOf("upload=-1; download=3", "upload=9223372036854775807; download=1", "upload=bad; download=1")) {
            assertNull(SubscriptionFetcher.parseUserInfo(value).first)
        }
        val info = SubscriptionFetcher.parseUserInfo("upload=bad; total=100; expire=9223372036854775807")
        assertNull(info.first)
        assertEquals(100L, info.second)
        assertNull(info.third)
        assertNull(SubscriptionFetcher.parseUserInfo("total=-1").second)
    }

    @Test fun `interval must be positive finite and representable in milliseconds`() {
        for (value in listOf("Infinity", "NaN", "1e309", "1e307", "-1", "0", "2garbage", "")) {
            assertNull(SubscriptionFetcher.parseUpdateInterval(value))
        }
        assertEquals(12.0, SubscriptionFetcher.parseUpdateInterval("0.5"))
    }

    @Test
    fun `delimiter dense body is handled and excessive links are rejected`() {
        val dense = "\n".repeat(1_000_000) + "vless://a@h:443"
        assertEquals(listOf("vless://a@h:443"), SubscriptionFetcher.decodeBody(dense))
        assertThrows(SubscriptionFetcher.TooManyLinks::class.java) {
            SubscriptionFetcher.decodeBody("vless://a@h:443\n".repeat(10_001))
        }
    }

    @Test
    fun `bounded response accepts valid utf8 at limit and rejects one extra byte`() {
        val bytes = "vless://ü@h:443".toByteArray(Charsets.UTF_8)
        assertArrayEquals(bytes, SubscriptionFetcher.readBounded(ByteArrayInputStream(bytes), bytes.size))
        assertThrows(SubscriptionFetcher.ResponseTooLarge::class.java) {
            SubscriptionFetcher.readBounded(ByteArrayInputStream(bytes + byteArrayOf(1)), bytes.size)
        }
    }

    @Test
    fun `bounded response rejects oversized streams without content length`() {
        val bytes = ByteArray(64 * 1024 + 1)
        assertThrows(SubscriptionFetcher.ResponseTooLarge::class.java) {
            SubscriptionFetcher.readBounded(ByteArrayInputStream(bytes), 64 * 1024)
        }
    }

    @Test
    fun `decodeBody returns plain links as is`() {
        val body = "vless://a@h:443\n# comment\nvless://b@h:443\n"
        val links = SubscriptionFetcher.decodeBody(body)
        assertEquals(listOf("vless://a@h:443", "vless://b@h:443"), links)
    }

    @Test
    fun `decodeBody decodes base64 body with url-safe alphabet and missing padding`() {
        val plain = "vless://a@h:443\nvless://b@h:443"
        val encoded = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(plain.toByteArray())

        val links = SubscriptionFetcher.decodeBody(encoded)
        assertEquals(listOf("vless://a@h:443", "vless://b@h:443"), links)
    }

    @Test
    fun `decodeBase64 round-trips standard padded base64`() {
        val original = "hello, life vpn"
        val encoded = java.util.Base64.getEncoder().encodeToString(original.toByteArray())
        assertEquals(original, SubscriptionFetcher.decodeBase64(encoded))
    }

    @Test
    fun `decodeHeaderValue unwraps base64 prefix`() {
        val encoded = java.util.Base64.getEncoder().encodeToString("Мой сервер".toByteArray())
        assertEquals("Мой сервер", SubscriptionFetcher.decodeHeaderValue("base64:$encoded"))
        assertEquals("Plain Title", SubscriptionFetcher.decodeHeaderValue("Plain Title"))
        assertNull(SubscriptionFetcher.decodeHeaderValue("   "))
    }

    @Test
    fun `parseUserInfo reads traffic and treats expire=0 as unlimited`() {
        val (used, total, expiresAt) = SubscriptionFetcher.parseUserInfo("upload=100; download=200; total=1000; expire=0")
        assertEquals(300L, used)
        assertEquals(1000L, total)
        assertNull(expiresAt)
    }

    @Test
    fun `parseUserInfo converts a nonzero expire to milliseconds`() {
        val (_, _, expiresAt) = SubscriptionFetcher.parseUserInfo("expire=1000")
        assertEquals(1_000_000L, expiresAt)
    }
}
