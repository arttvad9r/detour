package dev.detour.app.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.detour.app.core.AppRoute
import dev.detour.app.core.DpiPreset
import dev.detour.app.core.VpnProfileKind
import dev.detour.app.data.RoutesStore
import dev.detour.app.data.TriSettings
import dev.detour.app.vpn.EffectiveRoutes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Only intent is saved in navigation state; credentials stay in the existing profile editors. */
@kotlinx.serialization.Serializable
enum class SetupGoal(val route: AppRoute) {
    DPI(AppRoute.DPI), VLESS(AppRoute.VPN), SUBSCRIPTION(AppRoute.VPN), WARP(AppRoute.VPN),
}

internal fun shouldOfferOnboarding(settings: TriSettings): Boolean {
    if (settings.onboardingCompleted) return false
    if (settings.onboardingStarted) return true
    return settings.vlessKeys.items.isEmpty() && settings.wireGuardProfiles.items.isEmpty() &&
        settings.routes.isEmpty() && !settings.autoConnect && settings.preset == DpiPreset.RECOMMENDED &&
        settings.dpiCustomArgs.isBlank() && settings.themeId.isBlank() && settings.dnsId.isBlank() &&
        settings.dnsCustom.isBlank() && !settings.showSystemApps && settings.sessionStartedAt == null
}

internal fun setupProfileReady(settings: TriSettings, goal: SetupGoal): Boolean = when (goal) {
    SetupGoal.DPI -> true
    SetupGoal.VLESS -> settings.activeVpn == VpnProfileKind.VLESS && settings.activeVpnConfigured
    SetupGoal.SUBSCRIPTION -> settings.activeVpn == VpnProfileKind.SUBSCRIPTION && settings.activeVpnConfigured
    // Amnezia invitations may contain either a VLESS profile or AmneziaWG outbounds.
    SetupGoal.WARP -> settings.activeVpnConfigured
}

data class OnboardingState(
    val goal: SetupGoal? = null,
    val profileReady: Boolean = false,
    val routedCount: Int = 0,
    val routeConflict: Boolean = false,
    val missingVpnProfile: Boolean = false,
    val canConnect: Boolean = false,
)

internal fun onboardingState(settings: TriSettings?, routes: EffectiveRoutes?, goal: SetupGoal?): OnboardingState {
    if (settings == null || routes == null || goal == null) return OnboardingState(goal = goal)
    val count = if (goal.route == AppRoute.DPI) routes.dpiPackages.size else routes.vpnPackages.size
    val conflict = routes.sharedUidConflict.isNotEmpty()
    val missingProfile = routes.vpnPackages.isNotEmpty() && !settings.activeVpnConfigured
    val profileReady = setupProfileReady(settings, goal)
    return OnboardingState(goal, profileReady, count, conflict, missingProfile,
        canConnect = profileReady && count > 0 && !conflict && !missingProfile)
}

data class SetupCompletion(val saving: Boolean = false, val error: Boolean = false, val finished: Boolean = false)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class OnboardingViewModel(
    private val settings: StateFlow<TriSettings?>,
    private val resolveRoutes: suspend (Map<String, AppRoute>) -> EffectiveRoutes,
    private val saveCompletion: suspend () -> Unit,
    private val savedStateHandle: SavedStateHandle,
    private val saveStarted: suspend () -> Unit = {},
) : ViewModel() {
    private val goal = savedStateHandle.getStateFlow<String?>("setup_goal", null)
    private val revision = MutableStateFlow(0)
    private val _completion = MutableStateFlow(SetupCompletion())
    val completion: StateFlow<SetupCompletion> = _completion

    private val resolved = combine(settings, revision) { value, _ -> value }
        .mapLatest { value -> value to value?.let { resolveRoutes(it.routes) } }

    val uiState = combine(resolved, goal) { (settings, routes), selected ->
        onboardingState(settings, routes, SetupGoal.entries.firstOrNull { it.name == selected })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OnboardingState(
        goal = SetupGoal.entries.firstOrNull { it.name == goal.value },
    ))

    fun beginAutomaticSetup() {
        viewModelScope.launch {
            try {
                saveStarted()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _completion.value = SetupCompletion(error = true)
            }
        }
    }

    fun chooseGoal(value: SetupGoal?) { savedStateHandle["setup_goal"] = value?.name }
    fun refreshRoutes() { revision.value++ }

    fun complete() {
        if (_completion.value.saving) return
        _completion.value = SetupCompletion(saving = true)
        viewModelScope.launch {
            try {
                saveCompletion()
                settings.first { it?.onboardingCompleted == true }
                _completion.value = SetupCompletion(finished = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _completion.value = SetupCompletion(error = true)
            }
        }
    }
    fun acknowledgeCompletion() { _completion.value = SetupCompletion() }

    companion object {
        fun factory(store: RoutesStore, resolveRoutes: suspend (Map<String, AppRoute>) -> EffectiveRoutes): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    OnboardingViewModel(
                        store.settings, resolveRoutes, { store.setOnboardingCompleted() }, createSavedStateHandle(),
                        saveStarted = { store.setOnboardingStarted() },
                    )
                }
            }
    }
}
