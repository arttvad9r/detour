package dev.detour.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import dev.detour.app.R

@Composable
internal fun HelpScreen(onBack: () -> Unit, onOpenSetup: () -> Unit, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    Column(
        modifier.fillMaxSize()
            .background(detourColors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(scroll)
            .detourHighRefresh(scroll.isScrollInProgress),
    ) {
        DetourBrandedHeader(stringResource(R.string.help_title), onBack)
        DetourContentColumn {
            Spacer(Modifier.height(Spacing.space8))
            DetourCard(Modifier.padding(horizontal = Spacing.space16)) {
                DetourNavigationRow(
                    title = stringResource(R.string.setup_restart),
                    subtitle = stringResource(R.string.setup_restart_hint),
                    iconRes = R.drawable.ic_routes,
                    onClick = onOpenSetup,
                    modifier = Modifier.testTag("open-setup"),
                )
            }
        }
    }
}
