package dev.detour.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import dev.detour.app.R
import dev.detour.app.core.AppRoute
import dev.detour.app.vpn.VpnState

internal fun setupGoalTitle(goal: SetupGoal): Int = when (goal) {
    SetupGoal.DPI -> R.string.setup_goal_dpi
    SetupGoal.VLESS -> R.string.setup_goal_vless
    SetupGoal.SUBSCRIPTION -> R.string.setup_goal_subscription
    SetupGoal.WARP -> R.string.setup_goal_warp
}

private fun setupGoalHint(goal: SetupGoal): Int = when (goal) {
    SetupGoal.DPI -> R.string.setup_goal_dpi_hint
    SetupGoal.VLESS -> R.string.setup_goal_vless_hint
    SetupGoal.SUBSCRIPTION -> R.string.setup_goal_subscription_hint
    SetupGoal.WARP -> R.string.setup_goal_warp_hint
}

@Composable
internal fun OnboardingScreen(
    state: OnboardingState,
    completion: SetupCompletion,
    vpnState: VpnState,
    snackbar: SnackbarHostState,
    onChooseGoal: (SetupGoal?) -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenRoutes: () -> Unit,
    onOpenDpi: () -> Unit,
    onConnect: () -> Unit,
    onCancelConnect: () -> Unit,
    onFinish: () -> Unit,
    onSkip: () -> Unit,
) {
    val c = detourColors
    val goal = state.goal
    val scroll = rememberScrollState()
    val starting = vpnState == VpnState.Starting
    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("onboarding-screen"),
        containerColor = c.background,
        topBar = {
            DetourBrandedHeader(
                stringResource(R.string.setup_title),
                onBack = { if (goal == null) onSkip() else onChooseGoal(null) },
                modifier = Modifier.statusBarsPadding(),
                hideBackInListDetail = false,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            DetourContentColumn(Modifier.navigationBarsPadding()) {
                Column(Modifier.padding(horizontal = Spacing.space16, vertical = Spacing.space8)) {
                    if (goal != null) {
                        DetourButton(
                            text = stringResource(when {
                                completion.saving -> R.string.setup_saving
                                starting -> R.string.setup_cancel_connect
                                vpnState == VpnState.Active -> R.string.setup_done
                                else -> R.string.btn_connect
                            }),
                            enabled = !completion.saving && (starting || state.canConnect),
                            onClick = when {
                                starting -> onCancelConnect
                                vpnState == VpnState.Active -> onFinish
                                else -> onConnect
                            },
                            modifier = Modifier.testTag("setup-connect"),
                        )
                    }
                    if (vpnState != VpnState.Active || goal == null) TextButton(
                        onClick = onSkip,
                        enabled = !completion.saving && !starting,
                        modifier = Modifier.fillMaxWidth().testTag("setup-skip"),
                    ) { Text(stringResource(R.string.setup_skip)) }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(scroll).detourHighRefresh(scroll.isScrollInProgress)) {
            DetourContentColumn {
                Column(Modifier.padding(horizontal = Spacing.space20, vertical = Spacing.space12)) {
                    Text(
                        stringResource(if (goal == null) R.string.setup_question else setupGoalTitle(goal)),
                        style = MaterialTheme.typography.headlineSmall,
                        color = c.textPrimary,
                    )
                    Spacer(Modifier.height(Spacing.space8))
                    Text(
                        stringResource(if (goal == null) R.string.setup_intro else setupGoalHint(goal)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textSecondary,
                    )
                }
                DetourCard(Modifier.padding(horizontal = Spacing.space16)) {
                    if (goal == null) {
                        SetupGoal.entries.forEachIndexed { index, choice ->
                            DetourNavigationRow(
                                title = stringResource(setupGoalTitle(choice)),
                                subtitle = stringResource(setupGoalHint(choice)),
                                iconRes = if (choice == SetupGoal.DPI) R.drawable.ic_dpi else R.drawable.ic_profile,
                                titleMaxLines = Int.MAX_VALUE,
                                subtitleMaxLines = Int.MAX_VALUE,
                                onClick = { onChooseGoal(choice) },
                                modifier = Modifier.testTag("setup-goal-${choice.name}"),
                            )
                            if (index < SetupGoal.entries.lastIndex) GroupDivider(startInset = NavigationRowDividerInset)
                        }
                    } else {
                        if (goal != SetupGoal.DPI || state.missingVpnProfile) {
                            DetourNavigationRow(
                                title = stringResource(R.string.setup_profile),
                                subtitle = stringResource(if (state.profileReady && !state.missingVpnProfile)
                                    R.string.setup_profile_ready else R.string.setup_profile_hint),
                                iconRes = R.drawable.ic_profile,
                                titleMaxLines = Int.MAX_VALUE,
                                subtitleMaxLines = Int.MAX_VALUE,
                                onClick = onOpenProfiles,
                                modifier = Modifier.testTag("setup-profile"),
                            )
                            GroupDivider(startInset = NavigationRowDividerInset)
                        }
                        DetourNavigationRow(
                            title = stringResource(R.string.setup_apps),
                            subtitle = if (state.routedCount > 0) stringResource(R.string.setup_apps_ready, state.routedCount)
                                else stringResource(if (goal.route == AppRoute.DPI) R.string.setup_apps_dpi else R.string.setup_apps_vpn),
                            iconRes = R.drawable.ic_routes,
                            titleMaxLines = Int.MAX_VALUE,
                            subtitleMaxLines = Int.MAX_VALUE,
                            onClick = onOpenRoutes,
                            modifier = Modifier.testTag("setup-apps"),
                        )
                    }
                }
                if (goal != null) {
                    Column(Modifier.padding(horizontal = Spacing.space20, vertical = Spacing.space12)) {
                        if (state.routeConflict) SetupError(stringResource(R.string.err_shared_uid))
                        if (state.missingVpnProfile) SetupError(stringResource(R.string.setup_existing_vpn))
                        if (vpnState is VpnState.Failed) SetupError(vpnState.reason)
                        if (vpnState == VpnState.Active && state.canConnect) {
                            Text(stringResource(R.string.setup_connected), color = c.activeStrong, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(Spacing.space8))
                        }
                        if (vpnState != VpnState.Active) {
                            Text(stringResource(R.string.setup_permission_hint), color = c.textSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        if (goal == SetupGoal.DPI && vpnState != VpnState.Active && !starting) {
                            TextButton(onClick = onOpenDpi, modifier = Modifier.testTag("setup-dpi-test")) {
                                Text(stringResource(R.string.setup_dpi_test))
                            }
                        }
                    }
                }
                if (completion.error) {
                    Column(Modifier.padding(Spacing.space20)) { SetupError(stringResource(R.string.setup_save_error)) }
                }
            }
        }
    }
}

@Composable
private fun SetupError(text: String) {
    Text(text, color = detourColors.error, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(Spacing.space8))
}
