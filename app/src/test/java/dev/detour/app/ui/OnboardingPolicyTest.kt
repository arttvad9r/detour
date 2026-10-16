package dev.detour.app.ui

import dev.detour.app.core.AppRoute
import dev.detour.app.core.VlessKey
import dev.detour.app.core.VlessKeys
import dev.detour.app.core.VpnProfileKind
import dev.detour.app.data.RoutesMapping
import dev.detour.app.vpn.EffectiveRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingPolicyTest {
    private val fresh = RoutesMapping.toSettings(emptyMap())
    private val emptyRoutes = EffectiveRoutes(emptySet(), emptySet())
    private val dpiRoutes = EffectiveRoutes(emptySet(), setOf("dev.detour.fixture.video"))
    private val vpnRoutes = EffectiveRoutes(setOf("dev.detour.fixture.browser"), emptySet())
    private val vless = "vless://b831381d-6324-4d53-ad4f-8cda48b30811@example.com:443" +
        "?type=tcp&security=reality&fp=chrome&sni=example.com" +
        "&pbk=SbVKOEMjK0sIlbwg4akyBg5mL5KZwwB-ed4eEE7YnRc&sid=6ba85179"

    @Test fun `only an unconfigured installation gets automatic setup`() {
        assertTrue(shouldOfferOnboarding(fresh))
        assertFalse(shouldOfferOnboarding(fresh.copy(onboardingCompleted = true)))
        assertFalse(shouldOfferOnboarding(fresh.copy(routes = mapOf("example.app" to AppRoute.DPI))))
        assertFalse(shouldOfferOnboarding(fresh.copy(themeId = "detour_dark")))
        assertFalse(shouldOfferOnboarding(fresh.copy(dnsId = "adguard")))
        assertFalse(shouldOfferOnboarding(fresh.copy(autoConnect = true)))
    }

    @Test fun `an unfinished first run resumes after configuration has started`() {
        val partiallyConfigured = fresh.copy(onboardingStarted = true, routes = mapOf("example.app" to AppRoute.DPI))
        assertTrue(shouldOfferOnboarding(partiallyConfigured))
        assertFalse(shouldOfferOnboarding(partiallyConfigured.copy(onboardingCompleted = true)))
    }

    @Test fun `an existing unselected profile still suppresses first run`() {
        val configured = fresh.copy(vlessKeys = VlessKeys(listOf(VlessKey("one", "Example", vless)), null))
        assertFalse(shouldOfferOnboarding(configured))
        assertFalse(setupProfileReady(configured, SetupGoal.VLESS))
    }

    @Test fun `dpi needs an effective app route but no VPN profile`() {
        assertFalse(onboardingState(fresh, emptyRoutes, SetupGoal.DPI).canConnect)
        val state = onboardingState(fresh, dpiRoutes, SetupGoal.DPI)
        assertTrue(state.canConnect)
        assertEquals(1, state.routedCount)
    }

    @Test fun `leftover VPN assignments cannot bypass a missing profile in DPI setup`() {
        val mixed = EffectiveRoutes(vpnRoutes.vpnPackages, dpiRoutes.dpiPackages)
        val state = onboardingState(fresh, mixed, SetupGoal.DPI)
        assertTrue(state.missingVpnProfile)
        assertFalse(state.canConnect)
    }

    @Test fun `selected profile kind must match the subscription scenario`() {
        val configured = fresh.copy(vlessKeys = VlessKeys(listOf(VlessKey("one", "Example", vless)), "one"))
        assertTrue(onboardingState(configured, vpnRoutes, SetupGoal.VLESS).canConnect)
        assertFalse(onboardingState(configured, vpnRoutes, SetupGoal.SUBSCRIPTION).canConnect)
        val subscription = configured.copy(
            activeVpn = VpnProfileKind.SUBSCRIPTION,
            vlessKeys = VlessKeys(listOf(VlessKey("sub", "Example", "https://subscription.example/opaque")), "sub"),
        )
        assertTrue(onboardingState(subscription, vpnRoutes, SetupGoal.SUBSCRIPTION).canConnect)
        assertFalse(onboardingState(subscription, vpnRoutes, SetupGoal.VLESS).canConnect)
    }

    @Test fun `shared UID rejection blocks the guided connect action`() {
        val conflict = EffectiveRoutes(emptySet(), emptySet(), setOf(1001))
        val state = onboardingState(fresh, conflict, SetupGoal.DPI)
        assertTrue(state.routeConflict)
        assertFalse(state.canConnect)
    }

    @Test fun `Amnezia setup accepts its supported VLESS output`() {
        val configured = fresh.copy(vlessKeys = VlessKeys(listOf(VlessKey("one", "Example", vless)), "one"))
        assertTrue(onboardingState(configured, vpnRoutes, SetupGoal.WARP).canConnect)
    }

    @Test fun `unknown settings and unresolved inventory cannot enable connect`() {
        assertFalse(onboardingState(null, dpiRoutes, SetupGoal.DPI).canConnect)
        assertFalse(onboardingState(fresh, null, SetupGoal.DPI).canConnect)
    }
}
