package com.stailegrow.lifevpn.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * Скачивание обновления при сбоях GitHub: 503/504 и обрывы повторяются —
 * сначала обычным путём, затем напрямую мимо туннеля; ошибки проверки и
 * 404 не повторяются.
 */
class UpdateRetryTest {

    private val noSleep: (Long) -> Unit = {}

    @Test
    fun `retryable statuses`() {
        for (code in listOf(408, 429, 500, 502, 503, 504)) assertTrue("$code", UpdateChecker.isRetryableStatus(code))
        for (code in listOf(200, 301, 403, 404)) assertFalse("$code", UpdateChecker.isRetryableStatus(code))
    }

    @Test
    fun `plan goes through tunnel twice then direct twice`() {
        assertEquals(listOf(false, false, true, true), UpdateChecker.DOWNLOAD_PLAN.map { it.direct })
    }

    @Test
    fun `503 and 504 retried, then direct succeeds`() {
        val routes = mutableListOf<Boolean>()
        val result = UpdateChecker.withRetries(sleep = noSleep) { direct ->
            routes += direct
            if (routes.size < 3) throw UpdateChecker.UpdateException("GitHub вернул код 503", retryable = true)
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(listOf(false, false, true), routes)
    }

    @Test
    fun `non retryable error stops at once`() {
        val routes = mutableListOf<Boolean>()
        try {
            UpdateChecker.withRetries(sleep = noSleep) { direct ->
                routes += direct
                throw UpdateChecker.UpdateException("APK подписан чужим ключом")
            }
            fail("ожидалась ошибка")
        } catch (e: UpdateChecker.UpdateException) {
            assertEquals("APK подписан чужим ключом", e.message)
        }
        assertEquals(listOf(false), routes)
    }

    @Test
    fun `all attempts fail - last error, four attempts`() {
        var count = 0
        try {
            UpdateChecker.withRetries(sleep = noSleep) {
                count++
                throw UpdateChecker.UpdateException("таймаут $count", retryable = true)
            }
            fail("ожидалась ошибка")
        } catch (e: UpdateChecker.UpdateException) {
            assertEquals("таймаут 4", e.message)
            assertTrue(e.retryable)
        }
        assertEquals(4, count)
    }

    @Test
    fun `bounded and chunked bodies decode exactly`() {
        val bounded = DirectHttp.BoundedInputStream(ByteArrayInputStream("hello-extra".toByteArray()), 5)
        assertArrayEquals("hello".toByteArray(), bounded.readBytes())

        val chunked = DirectHttp.ChunkedInputStream(
            ByteArrayInputStream("5\r\nhello\r\n6;ext=1\r\n world\r\n0\r\n\r\n".toByteArray()),
        )
        assertArrayEquals("hello world".toByteArray(), chunked.readBytes())
    }
}
