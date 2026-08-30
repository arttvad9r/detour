package dev.triplet.app

import android.content.Context
import android.net.VpnService
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.triplet.app.data.AppInventory
import dev.triplet.app.data.RoutesStore
import dev.triplet.app.ui.AppsScreen
import dev.triplet.app.ui.AppsViewModel
import dev.triplet.app.ui.BackupScreen
import dev.triplet.app.ui.BackupViewModel
import dev.triplet.app.ui.DnsScreen
import dev.triplet.app.ui.DnsViewModel
import dev.triplet.app.ui.DpiScreen
import dev.triplet.app.ui.DpiViewModel
import dev.triplet.app.ui.HomeScreen
import dev.triplet.app.ui.HomeViewModel
import dev.triplet.app.ui.Motion
import dev.triplet.app.ui.ProfilesViewModel
import dev.triplet.app.ui.SettingsMenuScreen
import dev.triplet.app.ui.SettingsSection
import dev.triplet.app.ui.SettingsMenuViewModel
import dev.triplet.app.ui.ThemeScreen
import dev.triplet.app.ui.VlessKeyScreen
import dev.triplet.app.ui.detourColors
import dev.triplet.app.ui.detourHighRefresh
import dev.triplet.app.vpn.AutoConnectCoordinator
import dev.triplet.app.vpn.VpnController
import dev.triplet.app.vpn.VpnState
import dev.triplet.app.vpn.resolveEffectiveRoutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
internal sealed interface AppDestination : NavKey {
    @Serializable
    data object Home : AppDestination

    @Serializable
    data object Settings : AppDestination

    @Serializable
    data object Routes : AppDestination

    @Serializable
    data object Vless : AppDestination

    @Serializable
    data object Dpi : AppDestination

    @Serializable
    data object Theme : AppDestination

    @Serializable
    data object Dns : AppDestination

    @Serializable
    data object Backup : AppDestination
}

private fun AppDestination.settingsSectionOrNull(): SettingsSection? = when (this) {
    AppDestination.Routes -> SettingsSection.ROUTES
    AppDestination.Vless -> SettingsSection.PROFILES
    AppDestination.Dpi -> SettingsSection.DPI
    AppDestination.Dns -> SettingsSection.DNS
    AppDestination.Backup -> SettingsSection.BACKUP
    AppDestination.Theme -> SettingsSection.APPEARANCE
    AppDestination.Home,
    AppDestination.Settings,
    -> null
}

private fun AppDestination.isSettingsDetail(): Boolean = when (this) {
    AppDestination.Routes,
    AppDestination.Vless,
    AppDestination.Dpi,
    AppDestination.Theme,
    AppDestination.Dns,
    AppDestination.Backup,
    -> true
    AppDestination.Home,
    AppDestination.Settings,
    -> false
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun DetourNavigation(
    store: RoutesStore,
    appContext: Context,
    modifier: Modifier = Modifier,
) {
    val activityContext = LocalContext.current
    val backStack = rememberNavBackStack(AppDestination.Home)
    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>()
    val currentDestination = backStack.lastOrNull()
    var previousDestination by remember { mutableStateOf<NavKey?>(null) }
    var navMotionActive by remember { mutableStateOf(false) }

    LaunchedEffect(currentDestination) {
        val changed = previousDestination != null && previousDestination != currentDestination
        previousDestination = currentDestination
        if (changed) {
            navMotionActive = true
            delay(Motion.NAV_REFRESH_BOOST_MS)
            navMotionActive = false
        }
    }

    LaunchedEffect(Unit) {
        AutoConnectCoordinator(
            loadSettings = store::snapshot,
            resolveRoutes = { routes ->
                withContext(Dispatchers.IO) {
                    resolveEffectiveRoutes(appContext.packageManager, routes)
                }
            },
            vpnPermissionGranted = { VpnService.prepare(activityContext) == null },
            currentVpnState = { VpnController.state.value },
            startVpn = { VpnController.startNow(activityContext) },
        ).runOnce()
    }

    fun openSettingsDetail(destination: AppDestination) {
        require(destination.isSettingsDetail())
        if (backStack.lastOrNull() == destination) return

        val settingsIndex = backStack.indexOfLast { it == AppDestination.Settings }
        if (settingsIndex < 0) {
            backStack.add(AppDestination.Settings)
            backStack.add(destination)
            return
        }
        while (backStack.lastIndex > settingsIndex) {
            backStack.removeLastOrNull()
        }
        backStack.add(destination)
    }

    val popBack: () -> Unit = { backStack.removeLastOrNull() }

    NavDisplay(
        backStack = backStack,
        modifier = modifier
            .fillMaxSize()
            .detourHighRefresh(navMotionActive),
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        sceneStrategies = listOf(listDetailStrategy),
        transitionSpec = {
            slideInHorizontally(
                animationSpec = tween(
                    Motion.NAV_ENTER_MS,
                    easing = Motion.STANDARD_EASING,
                ),
                initialOffsetX = { it },
            ) togetherWith ExitTransition.None
        },
        popTransitionSpec = {
            EnterTransition.None togetherWith slideOutHorizontally(
                animationSpec = tween(
                    Motion.NAV_EXIT_MS,
                    easing = Motion.STANDARD_EASING,
                ),
                targetOffsetX = { it },
            )
        },
        predictivePopTransitionSpec = { _ ->
            EnterTransition.None togetherWith slideOutHorizontally(
                animationSpec = tween(
                    Motion.NAV_EXIT_MS,
                    easing = Motion.STANDARD_EASING,
                ),
                targetOffsetX = { it },
            )
        },
        entryProvider = entryProvider {
            entry<AppDestination.Home> {
                val homeViewModel = viewModel<HomeViewModel>(
                    factory = HomeViewModel.factory(
                        store = store,
                        resolveRoutes = { routes ->
                            withContext(Dispatchers.IO) {
                                resolveEffectiveRoutes(appContext.packageManager, routes)
                            }
                        },
                    ),
                )
                HomeScreen(
                    homeViewModel,
                    onOpenSettings = { backStack.add(AppDestination.Settings) },
                )
            }

            entry<AppDestination.Settings>(
                metadata = ListDetailSceneStrategy.listPane(
                    detailPlaceholder = { SettingsDetailPlaceholder() },
                ),
            ) {
                val settingsViewModel = viewModel<SettingsMenuViewModel>(
                    factory = SettingsMenuViewModel.factory(
                        store = store,
                        resolveRoutes = { routes ->
                            withContext(Dispatchers.IO) {
                                resolveEffectiveRoutes(appContext.packageManager, routes)
                            }
                        },
                    ),
                )
                SettingsMenuScreen(
                    viewModel = settingsViewModel,
                    selectedSection = (currentDestination as? AppDestination)?.settingsSectionOrNull(),
                    onOpenRoutes = { openSettingsDetail(AppDestination.Routes) },
                    onOpenVless = { openSettingsDetail(AppDestination.Vless) },
                    onOpenDpi = { openSettingsDetail(AppDestination.Dpi) },
                    onOpenTheme = { openSettingsDetail(AppDestination.Theme) },
                    onOpenDns = { openSettingsDetail(AppDestination.Dns) },
                    onOpenBackup = { openSettingsDetail(AppDestination.Backup) },
                    onBack = popBack,
                )
            }

            entry<AppDestination.Routes>(metadata = ListDetailSceneStrategy.detailPane()) {
                val appsViewModel = viewModel<AppsViewModel>(
                    factory = AppsViewModel.factory(
                        store = store,
                        initialApps = AppInventory.peek(),
                        loadApps = {
                            withContext(Dispatchers.IO) {
                                AppInventory.load(appContext)
                            }
                        },
                        restartTunnel = { VpnController.restartIfActive(appContext) },
                    ),
                )
                AppsScreen(appsViewModel, onBack = popBack)
            }

            entry<AppDestination.Vless>(metadata = ListDetailSceneStrategy.detailPane()) {
                val profilesViewModel = viewModel<ProfilesViewModel>(
                    factory = ProfilesViewModel.factory(
                        store = store,
                        restartTunnel = { VpnController.restartIfActive(appContext) },
                        stopTunnelIfRunning = {
                            if (
                                VpnController.state.value == VpnState.Active ||
                                VpnController.state.value == VpnState.Starting
                            ) {
                                VpnController.stop(appContext)
                            }
                        },
                    ),
                )
                VlessKeyScreen(profilesViewModel, onBack = popBack)
            }

            entry<AppDestination.Dpi>(metadata = ListDetailSceneStrategy.detailPane()) {
                val dpiViewModel = viewModel<DpiViewModel>(
                    factory = DpiViewModel.factory(
                        store = store,
                        restartTunnel = { VpnController.restartIfActive(appContext) },
                    ),
                )
                DpiScreen(dpiViewModel, onBack = popBack)
            }

            entry<AppDestination.Theme>(metadata = ListDetailSceneStrategy.detailPane()) {
                ThemeScreen(store, onBack = popBack)
            }

            entry<AppDestination.Dns>(metadata = ListDetailSceneStrategy.detailPane()) {
                val dnsViewModel = viewModel<DnsViewModel>(
                    factory = DnsViewModel.factory(
                        store = store,
                        restartTunnel = { VpnController.restartIfActive(appContext) },
                    ),
                )
                DnsScreen(dnsViewModel, onBack = popBack)
            }

            entry<AppDestination.Backup>(metadata = ListDetailSceneStrategy.detailPane()) {
                val backupViewModel = viewModel<BackupViewModel>(
                    factory = BackupViewModel.factory(
                        store = store,
                        stopTunnelIfRunning = {
                            if (
                                VpnController.state.value == VpnState.Active ||
                                VpnController.state.value == VpnState.Starting
                            ) {
                                VpnController.stop(appContext)
                            }
                        },
                    ),
                )
                BackupScreen(backupViewModel, onBack = popBack)
            }
        },
    )
}

@Composable
private fun SettingsDetailPlaceholder() {
    Box(
        Modifier
            .fillMaxSize()
            .background(detourColors.background)
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.settings_select_section),
            style = MaterialTheme.typography.bodyLarge,
            color = detourColors.textMuted,
        )
    }
}
