package com.stailegrow.lifevpn.core

import com.stailegrow.lifevpn.model.RoutingPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * В режиме TUN ядро не должно открывать ни одного локального порта: любой
 * порт на 127.0.0.1 доступен чужим приложениям на телефоне и выдаёт VPN.
 */
class LocalExposureTest {

    private val config = LinkParser.parse(
        "vless://11111111-2222-3333-4444-555555555555@example.com:443?security=reality&sni=example.org&pbk=pub&type=tcp",
    )

    @Test
    fun `tun config has no metrics, stats or api`() {
        val options = XrayConfigBuilder.Options(
            routing = RoutingPreset.bypassRU.make(),
            tunFileDescriptor = 42,
            accessLogPath = "/tmp/access.log",
        )
        val tree = XrayConfigBuilder.makeTree(config, options)
        for (key in listOf("metrics", "stats", "api", "policy")) {
            assertFalse("в конфиге не должно быть \"$key\"", tree.has(key))
        }
    }

    @Test
    fun `tun config has only the tun inbound and nothing listens`() {
        val options = XrayConfigBuilder.Options(tunFileDescriptor = 42)
        val inbounds = XrayConfigBuilder.makeTree(config, options).getJSONArray("inbounds")
        assertEquals(1, inbounds.length())
        val tun = inbounds.getJSONObject(0)
        assertEquals("tun", tun.getString("protocol"))
        assertFalse(tun.has("listen"))
        assertFalse(tun.has("port"))
    }

    @Test
    fun `isolation applies only to bypass-ru when enabled`() {
        assertTrue(IsolatedApps.appliesTo(RoutingPreset.bypassRU.make(), enabled = true))
        assertFalse(IsolatedApps.appliesTo(RoutingPreset.bypassRU.make(), enabled = false))
        assertFalse(IsolatedApps.appliesTo(RoutingPreset.global.make(), enabled = true))
    }

    @Test
    fun `isolation list contains MAX and never the app itself`() {
        val packages = IsolatedApps.known.map { it.packageName }
        assertTrue("ru.oneme.app" in packages)
        assertFalse("com.stailegrow.lifevpn" in packages)
        assertEquals(packages.size, packages.toSet().size)
    }
}
