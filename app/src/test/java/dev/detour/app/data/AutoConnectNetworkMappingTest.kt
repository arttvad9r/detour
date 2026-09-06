package dev.detour.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoConnectNetworkMappingTest {
    @Test
    fun `legacy auto-connect value is inherited by network triggers`() {
        val settings = RoutesMapping.toSettings(
            mapOf("auto_connect" to true),
        )

        assertTrue(settings.autoConnect)
        assertTrue(settings.autoConnectWifi)
        assertTrue(settings.autoConnectCellular)
    }

    @Test
    fun `explicit network trigger preferences override legacy value`() {
        val settings = RoutesMapping.toSettings(
            mapOf(
                "auto_connect" to true,
                "auto_connect_wifi" to false,
                "auto_connect_cellular" to true,
            ),
        )

        assertTrue(settings.autoConnect)
        assertFalse(settings.autoConnectWifi)
        assertTrue(settings.autoConnectCellular)
    }
}
