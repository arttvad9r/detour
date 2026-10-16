package dev.detour.app

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.detour.app.core.AppRoute
import dev.detour.app.core.SettingsBackup
import dev.detour.app.data.TriSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain

/** Exercise the real first-run back stack, forms and persisted completion. */
class OnboardingNavigationTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = ApplicationProvider.getApplicationContext<Context>() as DetourApp
    private val store get() = context.routesStore
    private lateinit var before: TriSettings

    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() = runBlocking {
            before = store.snapshot()
            store.restoreBackup(SettingsBackup.Backup(themeId = "", dnsId = ""))
            store.setSessionStartedAt(null)
            store.setOnboardingStarted(false)
            store.setOnboardingCompleted(false)
            store.settings.first { it != null && it.routes.isEmpty() && it.themeId.isEmpty() && !it.onboardingCompleted }
            Unit
        }
        override fun after() = runBlocking {
            store.restoreBackup(SettingsBackup.Backup(
                vlessKeys = before.vlessKeys, wireGuardProfiles = before.wireGuardProfiles,
                activeVpn = before.activeVpn, presetId = before.preset.id, dpiCustomArgs = before.dpiCustomArgs,
                themeId = before.themeId, dnsId = before.dnsId, dnsCustom = before.dnsCustom,
                showSystemApps = before.showSystemApps, routes = before.routes.mapValues { it.value.name },
            ))
            store.setOnboardingStarted(before.onboardingStarted)
            store.setOnboardingCompleted(before.onboardingCompleted)
            store.setSessionStartedAt(before.sessionStartedAt)
            store.setAutoConnect(before.autoConnect)
            Unit
        }
    }).around(compose)

    private fun choose(name: String) = compose.onNodeWithTag("setup-goal-$name").performClick()

    @Test fun completedSetupIsReopenedOnlyThroughHelpWithoutResettingSettings() {
        compose.onNodeWithTag("setup-skip").performClick()
        runBlocking { store.setRoute("com.example.saved", AppRoute.VPN) }
        val saved = runBlocking { store.snapshot() }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.cd_settings)).performClick()
        compose.onNodeWithTag("open-setup").assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.help_title)).performScrollTo().performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.setup_restart)).assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("open-setup").assertIsDisplayed().performClick()
        compose.onNodeWithTag("setup-goal-DPI").assertIsDisplayed()
        val reopened = runBlocking { store.snapshot() }
        assertEquals(saved.routes, reopened.routes)
        assertEquals(saved.vlessKeys, reopened.vlessKeys)
        assertEquals(saved.dnsId, reopened.dnsId)
        assertTrue(reopened.onboardingCompleted)
    }

    @Test fun skipPersistsAndRecreationStaysOnHome() {
        compose.onNodeWithTag("setup-skip").performClick()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.cd_settings)).assertIsDisplayed()
        assertTrue(runBlocking { store.snapshot().onboardingCompleted })
        compose.activityRule.scenario.recreate()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.cd_settings)).assertIsDisplayed()
        compose.onNodeWithTag("onboarding-screen").assertDoesNotExist()
    }

    @Test fun dpiChoiceSurvivesRecreationAndRequiresRealAppRoutes() {
        choose("DPI")
        compose.onNodeWithTag("setup-profile").assertDoesNotExist()
        compose.onNodeWithTag("setup-connect").assertIsNotEnabled()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("setup-apps").assertIsDisplayed().performClick()
        compose.onNodeWithTag("setup-routes-continue").assertIsNotEnabled()
        // Assign the installed target app only to verify return/progress; no VPN is started.
        runBlocking { store.setRoute(context.packageName, AppRoute.DPI) }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("setup-routes-continue").assertIsNotEnabled()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.cd_back)).performClick()
        compose.onNodeWithTag("setup-connect").assertIsNotEnabled()
        assertFalse(runBlocking { store.snapshot().onboardingCompleted })
    }

    @Test fun vlessChoiceOpensTheProtectedVlessEditor() {
        choose("VLESS")
        compose.onNodeWithTag("setup-profile").performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.profile_vless_direct_input_label)).assertIsDisplayed()
        assertTrue(compose.activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0)
    }

    @Test fun subscriptionChoiceOpensItsOwnEditor() {
        choose("SUBSCRIPTION")
        compose.onNodeWithTag("setup-profile").performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.profile_subscription_input_label)).assertIsDisplayed()
    }

    @Test fun warpChoiceOffersFileAndInvitationInputs() {
        choose("WARP")
        compose.onNodeWithTag("setup-profile").performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.profile_import_file)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.profile_add_amnezia)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.profile_add_vless_action)).assertDoesNotExist()
    }

    @Test fun appRouteDetailSurvivesRecreationAndRefreshesProfileRequirements() {
        choose("DPI")
        compose.onNodeWithTag("setup-apps").performClick()
        runBlocking { store.setShowSystemApps(true) }
        val app = dev.detour.app.data.AppInventory.load(context).first { candidate ->
            candidate.packageName in dev.detour.app.vpn.resolveEffectiveRoutes(
                context.packageManager, mapOf(candidate.packageName to AppRoute.DPI),
            ).dpiPackages
        }
        compose.onNode(hasSetTextAction()).performTextInput(app.packageName)
        compose.onNodeWithTag("app-detail-${app.packageName}").performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.route_direct)).assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("app-route-detail").assertIsDisplayed()
        runBlocking { store.setRoute(app.packageName, AppRoute.VPN) }
        val chooseProfile = compose.activity.getString(R.string.route_detail_profiles)
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(chooseProfile).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(chooseProfile).performScrollTo().performClick()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.cd_back)).performClick()
        compose.onNodeWithTag("app-route-detail").assertIsDisplayed()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.cd_back)).performClick()
        compose.onNodeWithTag("setup-routes-continue").assertIsNotEnabled()
        compose.onNodeWithTag("app-detail-${app.packageName}").assertIsDisplayed()
    }

}
