package dev.detour.app.data

import dev.detour.app.core.AppRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class AppRouteOrderingTest {
    @Test
    fun `configured apps come first and order stays frozen after route changes`() {
        val apps = listOf(
            AppInfo("chrome", "Chrome", false),
            AppInfo("telegram", "Telegram", false),
            AppInfo("youtube", "YouTube", false),
            AppInfo("adguard", "AdGuard", false),
        )
        val initial = mapOf(
            "telegram" to AppRoute.VPN,
            "youtube" to AppRoute.DPI,
            "chrome" to AppRoute.DIRECT,
            "adguard" to AppRoute.DIRECT,
        )

        val order = AppRouteOrdering.snapshot(apps, initial)
        val changed = initial + ("chrome" to AppRoute.VPN)

        assertEquals(listOf("telegram", "youtube", "adguard", "chrome"), order)
        assertEquals(order, AppRouteOrdering.apply(apps, order).map(AppInfo::packageName))
    }
}
