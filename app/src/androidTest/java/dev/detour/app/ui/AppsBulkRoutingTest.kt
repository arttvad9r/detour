package dev.detour.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import dev.detour.app.DetourApp
import dev.detour.app.R
import dev.detour.app.core.AppRoute
import dev.detour.app.data.AppInfo
import dev.detour.app.data.TriSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AppsBulkRoutingTest {
    @get:Rule val rule = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var persisted: MutableStateFlow<TriSettings?>
    private lateinit var viewModel: AppsViewModel
    private val writes = mutableListOf<Map<String, AppRoute>>()
    private var restarts = 0
    private var failWrites = false
    private var writeGate: CompletableDeferred<Unit>? = null
    private val apps = listOf(
        AppInfo("dev.detour.fixture.alpha", "Alpha", false),
        AppInfo("dev.detour.fixture.beta", "Beta", false),
        AppInfo("dev.detour.fixture.system", "System", true),
    )

    private fun launch(fontScale: Float = 1f) {
        val initial = runBlocking { (context as DetourApp).routesStore.snapshot() }
            .copy(routes = emptyMap(), showSystemApps = false)
        persisted = MutableStateFlow(initial)
        rule.setContent {
            val theme = AppTheme.CATPPUCCIN_LATTE
            CompositionLocalProvider(LocalDetourTheme provides theme, LocalDetourColors provides theme.colors) {
                MaterialTheme(colorScheme = colorSchemeFor(theme.colors, theme.dark), typography = AppTypography) {
                    viewModel = androidx.compose.runtime.remember {
                        AppsViewModel(
                            settings = persisted,
                            initialApps = apps,
                            loadApps = { apps },
                            setShowSystemApps = { persisted.value = persisted.value!!.copy(showSystemApps = it) },
                            setRoutes = { changes ->
                                writeGate?.await()
                                if (failWrites) error("synthetic persistence failure")
                                writes.add(changes)
                                persisted.value = persisted.value!!.copy(
                                    routes = (persisted.value!!.routes + changes).filterValues { it != AppRoute.DIRECT },
                                )
                            },
                            restartTunnel = { restarts++ },
                            savedStateHandle = SavedStateHandle(),
                        )
                    }
                    DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(fontScale)) {
                        AppsScreen(viewModel, onBack = {})
                    }
                }
            }
        }
    }

    private fun text(id: Int) = context.getString(id)
    private fun selectVisible() {
        rule.onNodeWithText(text(R.string.routes_select)).performClick()
        rule.onNodeWithText(text(R.string.routes_select_visible)).performClick()
    }
    private fun assign(route: AppRoute) {
        rule.onNode(hasText(text(when (route) { AppRoute.DIRECT -> R.string.route_direct_compact; else -> routeLabel(route) })) and
            hasAnyAncestor(androidx.compose.ui.test.hasTestTag("apps-bulk-route-actions")))
            .assertIsDisplayed().performClick()
    }

    @Test fun selectedVisibleAppsPersistInOneBatchAndRestartOnce() {
        launch()
        selectVisible()
        assign(AppRoute.DPI)
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals(listOf(apps[0], apps[1]).associate { it.packageName to AppRoute.DPI }, writes.single())
            assertEquals(1, restarts)
            assertTrue(persisted.value!!.routes[apps[2].packageName] == null)
        }
        rule.onNodeWithText(text(R.string.routes_select)).assertIsDisplayed()
        // Reapplying the same batch does not persist or restart again.
        selectVisible()
        assign(AppRoute.DPI)
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(1, writes.size); assertEquals(1, restarts) }
    }

    @Test fun failedSaveRetainsSelectionAndCanBeRetried() {
        launch()
        selectVisible()
        rule.runOnIdle { failWrites = true }
        assign(AppRoute.VPN)
        rule.onNodeWithText(text(R.string.routes_save_error)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.routes_selected_count, 2)).assertIsDisplayed()
        rule.runOnIdle { assertEquals(0, restarts); assertTrue(writes.isEmpty()); failWrites = false }
        assign(AppRoute.VPN)
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(1, restarts); assertEquals(1, writes.size) }
    }

    @Test fun changingSearchClearsSelectionBeforeBulkAssignment() {
        launch()
        selectVisible()
        rule.runOnIdle { viewModel.setQuery("Alpha") }
        rule.onNodeWithText(context.getString(R.string.routes_selected_count, 0)).assertIsDisplayed()
        rule.onNode(hasText(text(R.string.route_vpn)) and
            hasAnyAncestor(androidx.compose.ui.test.hasTestTag("apps-bulk-route-actions"))).assertIsNotEnabled()
        rule.onNodeWithTag("app-selection-${apps[0].packageName}").performClick()
        assign(AppRoute.VPN)
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(mapOf(apps[0].packageName to AppRoute.VPN), writes.single()) }
    }
    @Test fun changingRouteFilterClearsSelectionAndDirectRemovesOnlyMatchingApps() {
        launch()
        rule.runOnIdle {
            persisted.value = persisted.value!!.copy(routes = mapOf(apps[0].packageName to AppRoute.VPN))
        }
        selectVisible()
        rule.onNode(hasText(text(R.string.route_vpn)) and
            hasAnyAncestor(androidx.compose.ui.test.hasTestTag("apps-route-filter"))).performClick()
        rule.onNodeWithText(context.getString(R.string.routes_selected_count, 0)).assertIsDisplayed()
        rule.onNodeWithTag("app-selection-${apps[1].packageName}").assertDoesNotExist()
        rule.onNodeWithText(text(R.string.routes_select_visible)).performClick()
        assign(AppRoute.DIRECT)
        rule.onNodeWithText(text(R.string.routes_no_results)).assertIsDisplayed()
        rule.runOnIdle {
            assertEquals(mapOf(apps[0].packageName to AppRoute.DIRECT), writes.single())
            assertTrue(persisted.value!!.routes.isEmpty())
        }
    }

    @Test fun bulkControlsRemainUsableWithLargeFonts() {
        launch(fontScale = 2f)
        selectVisible()
        rule.onNodeWithTag("app-selection-${apps[0].packageName}").assertIsDisplayed()
        assign(AppRoute.DPI)
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(1, writes.size); assertEquals(1, restarts) }
    }

    @Test fun newerSingleAppIntentWinsOverAnInFlightBatch() {
        launch()
        val gate = CompletableDeferred<Unit>()
        rule.runOnIdle {
            writeGate = gate
            viewModel.setAppRoutes(setOf(apps[0].packageName, apps[1].packageName), AppRoute.VPN)
        }
        rule.runOnIdle {
            viewModel.setAppRoute(apps[0].packageName, AppRoute.DPI)
            gate.complete(Unit)
        }
        rule.waitUntil { !viewModel.routeWriteState.value.saving }
        rule.runOnIdle {
            assertEquals(mapOf(apps[0].packageName to AppRoute.DPI, apps[1].packageName to AppRoute.VPN),
                persisted.value!!.routes)
            assertEquals(2, restarts)
        }
    }

}
