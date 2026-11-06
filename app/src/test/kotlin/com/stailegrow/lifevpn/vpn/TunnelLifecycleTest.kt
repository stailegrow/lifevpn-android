package com.stailegrow.lifevpn.vpn

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors

class TunnelLifecycleTest {
    @Test fun disconnectInvalidatesSuspendedConnect() {
        val life = TunnelLifecycle()
        val old = life.advance()
        var connected = false
        val disconnect = life.advance()
        assertFalse(life.runIfCurrent(old) { connected = true })
        assertTrue(life.runIfCurrent(disconnect) { connected = false })
        assertFalse(connected)
    }

    @Test fun staleFailureCannotTearDownReplacement() {
        val life = TunnelLifecycle()
        val old = life.advance()
        val replacement = life.advance()
        var running = false
        life.runIfCurrent(replacement) { running = true }
        assertFalse(life.runIfCurrent(old) { running = false })
        assertTrue(running)
    }

    @Test fun destroyPermanentlyRejectsWorkAndCleansOnce() {
        val life = TunnelLifecycle()
        val token = life.advance()
        var cleaned = 0
        life.destroy { cleaned++ }
        life.destroy { cleaned++ }
        assertFalse(life.runIfCurrent(token) { fail() })
        assertFalse(life.runIfCurrent(life.advance()) { fail() })
        assertEquals(1, cleaned)
    }

    @Test fun destroyWaitsForAcquisitionThenCleansIt() {
        val life = TunnelLifecycle()
        val token = life.advance()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val destroying = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        var running = false
        try {
            val start = pool.submit {
                life.runIfCurrent(token) {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    running = true
                }
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val stop = pool.submit {
                destroying.countDown()
                life.destroy { running = false }
            }
            assertTrue(destroying.await(5, TimeUnit.SECONDS))
            release.countDown()
            start.get(5, TimeUnit.SECONDS)
            stop.get(5, TimeUnit.SECONDS)
            assertFalse(running)
            assertFalse(life.runIfCurrent(token) { running = true })
        } finally { release.countDown(); pool.shutdownNow() }
    }
}
