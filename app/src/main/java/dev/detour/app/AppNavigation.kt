package dev.detour.app

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.detour.app.data.AppInventory
import dev.detour.app.data.RoutesStore
import dev.detour.app.ui.AppsScreen
import dev.detour.app.ui.AppsViewModel
import dev.detour.app.ui.BackupScreen
import dev.detour.app.ui.BackupViewModel
import dev.detour.app.ui.DnsScreen
import dev.detour.app.ui.DnsViewModel
import dev.detour.app.ui.DpiScreen
import dev.detour.app.ui.DpiViewModel
import dev.detour.app.ui.HomeScreen
import dev.detour.app.ui.HomeViewModel
import dev.detour.app.ui.Motion
import dev.detour.app.ui.ProfileDeleteConfirmationDialog
import dev.detour.app.ui.ProfilesViewModel
import dev.detour.app.ui.OnboardingViewModel
import dev.detour.app.ui.OnboardingScreen
import dev.detour.app.ui.SetupGoal
import dev.detour.app.ui.shouldOfferOnboarding
import dev.detour.app.ui.setupProfileReady
import dev.detour.app.ui.rememberManualVpnActions
import dev.detour.app.ui.SettingsMenuScreen
import dev.detour.app.ui.SettingsSection
import dev.detour.app.ui.SettingsMenuViewModel
import dev.detour.app.ui.ThemeScreen
import dev.detour.app.ui.ThemeViewModel
import dev.detour.app.ui.VlessKeyScreen
import dev.detour.app.ui.detourColors
import dev.detour.app.ui.detourHighRefresh
import dev.detour.app.vpn.AutoConnectCoordinator
import dev.detour.app.vpn.VpnController
import dev.detour.app.vpn.VpnState
import dev.detour.app.vpn.resolveEffectiveRoutes
import dev.detour.engine.engine.Engine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
internal sealed interface AppDestination : NavKey {
    @Serializable
    data object Home : AppDestination

    @Serializable
    data object Setup : AppDestination

    @Serializable
    data class SetupProfiles(val goal: SetupGoal) : AppDestination

    @Serializable
    data class SetupRoutes(val goal: SetupGoal) : AppDestination

    @Serializable
    data object Settings : AppDestination

    @Serializable
    data object Routes : AppDestination

    @Serializable
    data class AppRouteDetail(val packageName: String) : AppDestination

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
    AppDestination.Routes, is AppDestination.AppRouteDetail -> SettingsSection.ROUTES
    AppDestination.Vless -> SettingsSection.PROFILES
    AppDestination.Dpi -> SettingsSection.DPI
    AppDestination.Dns -> SettingsSection.DNS
    AppDestination.Backup -> SettingsSection.BACKUP
    AppDestination.Theme -> SettingsSection.APPEARANCE
    AppDestination.Setup,
    is AppDestination.SetupProfiles,
    is AppDestination.SetupRoutes,
    AppDestination.Home,
    AppDestination.Settings,
    -> null
}

private fun AppDestination.isSettingsDetail(): Boolean = when (this) {
    AppDestination.Routes,
    is AppDestination.AppRouteDetail,
    AppDestination.Vless,
    AppDestination.Dpi,
    AppDestination.Theme,
    AppDestination.Dns,
    AppDestination.Backup,
    -> true
    AppDestination.Setup,
    is AppDestination.SetupProfiles,
    is AppDestination.SetupRoutes,
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
    val settings by store.settings.collectAsStateWithLifecycle()
    val snapshot = settings
    if (snapshot == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val setupViewModel = viewModel<OnboardingViewModel>(factory = OnboardingViewModel.factory(store) { routes ->
        withContext(Dispatchers.IO) { resolveEffectiveRoutes(appContext.packageManager, routes) }
    })
    val setupCompletion by setupViewModel.completion.collectAsStateWithLifecycle()
    val autoConnectLaunchViewModel = viewModel<AutoConnectLaunchViewModel>()
    val initialDestination = remember {
        if (shouldOfferOnboarding(snapshot)) AppDestination.Setup else AppDestination.Home
    }
    val backStack = rememberNavBackStack(initialDestination)
    LaunchedEffect(setupViewModel, initialDestination) {
        if (initialDestination == AppDestination.Setup && !snapshot.onboardingStarted) {
            setupViewModel.beginAutomaticSetup()
        }
    }
    LaunchedEffect(setupCompletion.finished) {
        if (setupCompletion.finished) {
            backStack.clear()
            backStack.add(AppDestination.Home)
            setupViewModel.acknowledgeCompletion()
        }
    }
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

    LaunchedEffect(autoConnectLaunchViewModel) {
        autoConnectLaunchViewModel.launchOnce {
            AutoConnectCoordinator(
                loadSettings = store::snapshot,
                resolveRoutes = { routes ->
                    withContext(Dispatchers.IO) {
                        resolveEffectiveRoutes(appContext.packageManager, routes)
                    }
                },
                vpnPermissionGranted = { VpnService.prepare(appContext) == null },
                currentVpnState = { VpnController.state.value },
                startVpn = { VpnController.startNow(appContext) },
            ).runOnce()
        }
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

    fun openFromHome(destination: AppDestination) {
        require(destination.isSettingsDetail())
        if (backStack.lastOrNull() == destination) return
        while (backStack.size > 1) backStack.removeLastOrNull()
        backStack.add(destination)
    }

    val popBack: () -> Unit = { backStack.removeLastOrNull() }

    NavDisplay(
        backStack = backStack,
        modifier = modifier
            .semantics { testTagsAsResourceId = true }
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
            entry<AppDestination.Setup> {
                val setupState by setupViewModel.uiState.collectAsStateWithLifecycle()
                val vpnState by VpnController.state.collectAsStateWithLifecycle()
                val snackbar = remember { SnackbarHostState() }
                val actions = rememberManualVpnActions(vpnState, snackbar)
                androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    setupViewModel.refreshRoutes()
                }
                OnboardingScreen(
                    state = setupState,
                    completion = setupCompletion,
                    vpnState = vpnState,
                    snackbar = snackbar,
                    onChooseGoal = setupViewModel::chooseGoal,
                    onOpenProfiles = { backStack.add(AppDestination.SetupProfiles(setupState.goal ?: SetupGoal.DPI)) },
                    onOpenRoutes = { backStack.add(AppDestination.SetupRoutes(setupState.goal ?: SetupGoal.DPI)) },
                    onOpenDpi = { backStack.add(AppDestination.Dpi) },
                    onConnect = actions.start,
                    onCancelConnect = actions.stop,
                    onFinish = setupViewModel::complete,
                    onSkip = setupViewModel::complete,
                )
            }
            entry<AppDestination.SetupProfiles> { key ->
                ProfilesDestination(
                    store, appContext, popBack, setupGoal = key.goal,
                    canContinue = snapshot.activeVpnConfigured && setupProfileReady(snapshot, key.goal),
                    onContinue = popBack,
                )
            }
            entry<AppDestination.SetupRoutes> { key ->
                val setupState by setupViewModel.uiState.collectAsStateWithLifecycle()
                RoutesDestination(
                    store, appContext, popBack, setupRoute = key.goal.route,
                    canContinue = setupState.routedCount > 0 && !setupState.routeConflict,
                    onContinue = popBack,
                    onOpenApp = { backStack.add(AppDestination.AppRouteDetail(it)) },
                )
            }

            entry<AppDestination.Home> {
                val homeViewModel = viewModel<HomeViewModel>(
                    factory = HomeViewModel.factory(
                        store = store,
                        resolveRoutes = { routes ->
                            withContext(Dispatchers.IO) {
                                resolveEffectiveRoutes(appContext.packageManager, routes)
                            }
                        },
                        readSubscriptionNode = {
                            withContext(Dispatchers.IO) {
                                Engine.subscriptionSelectedNode(appContext.cacheDir.absolutePath)
                                    .trim()
                                    .takeIf { it.isNotBlank() }
                            }
                        },
                    ),
                )
                HomeScreen(
                    viewModel = homeViewModel,
                    onOpenSettings = { backStack.add(AppDestination.Settings) },
                    onOpenProfiles = { openFromHome(AppDestination.Vless) },
                    onOpenDns = { openFromHome(AppDestination.Dns) },
                    onOpenRoutes = { openFromHome(AppDestination.Routes) },
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
                    onOpenSetup = {
                        setupViewModel.chooseGoal(null)
                        backStack.add(AppDestination.Setup)
                    },
                )
            }

            entry<AppDestination.Routes>(metadata = ListDetailSceneStrategy.detailPane()) {
                RoutesDestination(store, appContext, popBack,
                    onOpenApp = { backStack.add(AppDestination.AppRouteDetail(it)) })
            }

            entry<AppDestination.AppRouteDetail>(metadata = ListDetailSceneStrategy.detailPane()) { key ->
                dev.detour.app.ui.AppRouteDetailDestination(
                    key.packageName, store, appContext, popBack,
                    onOpenProfiles = { backStack.add(AppDestination.Vless) },
                    onOpenDpi = { backStack.add(AppDestination.Dpi) },
                    onOpenConnection = { backStack.add(AppDestination.Home) },
                )
            }

            entry<AppDestination.Vless>(metadata = ListDetailSceneStrategy.detailPane()) {
                ProfilesDestination(store, appContext, popBack)
            }

            entry<AppDestination.Dpi>(metadata = ListDetailSceneStrategy.detailPane()) {
                val dpiViewModel = viewModel<DpiViewModel>(
                    factory = DpiViewModel.factory(
                        store = store,
                        appContext = appContext,
                        restartTunnel = { VpnController.restartIfActive(appContext) },
                    ),
                )
                DpiScreen(dpiViewModel, onBack = popBack)
            }

            entry<AppDestination.Theme>(metadata = ListDetailSceneStrategy.detailPane()) {
                val themeViewModel = viewModel<ThemeViewModel>(
                    factory = ThemeViewModel.factory(store),
                )
                ThemeScreen(themeViewModel, onBack = popBack)
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
                        readBackupDocument = { uri ->
                            withContext(Dispatchers.IO) {
                                readBackupDocument(appContext, uri)
                            }
                        },
                        writeBackupDocument = { uri, json ->
                            withContext(Dispatchers.IO) {
                                writeBackupDocument(appContext, uri, json)
                            }
                        },
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
    dev.detour.app.ui.SettingsDetailEmptyState()
}

@Composable
private fun RoutesDestination(
    store: RoutesStore, appContext: Context, popBack: () -> Unit,
    setupRoute: dev.detour.app.core.AppRoute? = null,
    canContinue: Boolean = false, onContinue: (() -> Unit)? = null,
    onOpenApp: (String) -> Unit = {},
) {
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
    AppsScreen(appsViewModel, onBack = popBack, setupRoute = setupRoute,
        canContinue = canContinue, onContinue = onContinue, onOpenApp = onOpenApp)
}

@Composable
private fun ProfilesDestination(
    store: RoutesStore, appContext: Context, popBack: () -> Unit,
    setupGoal: SetupGoal? = null,
    canContinue: Boolean = false, onContinue: (() -> Unit)? = null,
) {
    val profilesViewModel = viewModel<ProfilesViewModel>(
        factory = ProfilesViewModel.factory(
            store = store,
            loadWarpConfig = { uri ->
                withContext(Dispatchers.IO) {
                    readWarpDocument(appContext, uri)
                }
            },
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
    val pendingDelete by profilesViewModel.pendingDelete.collectAsStateWithLifecycle()
    VlessKeyScreen(profilesViewModel, onBack = popBack, setupGoal = setupGoal,
        canContinue = canContinue, onContinue = onContinue)
    pendingDelete?.let { request ->
        ProfileDeleteConfirmationDialog(
            request = request,
            connected = VpnController.state.collectAsStateWithLifecycle().value.let {
                it == VpnState.Active || it == VpnState.Starting
            },
            onConfirm = profilesViewModel::confirmDelete,
            onDismiss = profilesViewModel::dismissDelete,
        )
    }
}
