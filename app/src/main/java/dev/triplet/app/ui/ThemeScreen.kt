package dev.triplet.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.triplet.app.R
import dev.triplet.app.data.RoutesStore
import kotlinx.coroutines.launch

@Composable
fun ThemeScreen(store: RoutesStore, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val c = detourColors
    val settings by store.settings.collectAsStateWithLifecycle()
    val current = AppTheme.byId(settings?.themeId ?: "").id
    val scrollState = rememberScrollState()

    Column(
        modifier.fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(scrollState)
            .detourHighRefresh(scrollState.isScrollInProgress),
    ) {
        ScreenHeader(stringResource(R.string.theme_title), onBack)

        DetourContentColumn {
            Spacer(Modifier.height(Spacing.space8))
            DetourCard(Modifier.padding(horizontal = Spacing.space16).selectableGroup()) {
                AppTheme.entries.forEachIndexed { i, t ->
                    val selected = current == t.id
                    RadioRow(
                        title = t.label,
                        selected = selected,
                        onClick = { scope.launch { store.setTheme(t.id) } },
                        trailing = { ThemeSwatches(t) },
                    )
                    if (i < AppTheme.entries.lastIndex) GroupDivider(startInset = 46)
                }
            }

            Spacer(Modifier.height(Spacing.space12))
            Text(
                stringResource(R.string.theme_hint),
                style = MaterialTheme.typography.bodySmall,
                color = c.textMuted,
                modifier = Modifier.padding(horizontal = Spacing.space16),
            )
            Spacer(Modifier.height(Spacing.space24))
        }
    }
}

@Composable
private fun ThemeSwatches(t: AppTheme) {
    Row(Modifier.padding(end = Spacing.space12)) {
        listOf(t.colors.background, t.colors.accent, t.colors.textPrimary).forEach { color ->
            Box(
                Modifier
                    .padding(start = Spacing.space4)
                    .size(14.dp)
                    .background(color, CircleShape)
                    .border(1.dp, detourColors.border, CircleShape),
            )
        }
    }
}
