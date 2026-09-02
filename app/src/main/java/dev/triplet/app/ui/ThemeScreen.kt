package dev.triplet.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.triplet.app.R

@Composable
fun ThemeScreen(viewModel: ThemeViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val c = detourColors
    val scrollState = rememberScrollState()
    val officialThemes = listOf(AppTheme.DETOUR_LIGHT, AppTheme.DETOUR_DARK)
    val communityThemes = AppTheme.entries.filterNot { it in officialThemes }

    Column(
        modifier.fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(scrollState)
            .detourHighRefresh(scrollState.isScrollInProgress),
    ) {
        DetourBrandedHeader(stringResource(R.string.theme_title), onBack)

        DetourContentColumn {
            Spacer(Modifier.height(Spacing.space8))
            ThemeSectionLabel(R.string.theme_section_detour)
            Spacer(Modifier.height(Spacing.space8))
            ThemeChoiceCard(
                themes = officialThemes,
                selectedThemeId = state.selectedThemeId,
                onSelect = viewModel::selectTheme,
            )

            Spacer(Modifier.height(Spacing.space12))
            Text(
                stringResource(R.string.theme_hint),
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = c.textMuted,
                modifier = Modifier.padding(horizontal = Spacing.space20),
            )

            Spacer(Modifier.height(Spacing.space16))
            ThemeSectionLabel(R.string.theme_section_community)
            Spacer(Modifier.height(Spacing.space8))
            ThemeChoiceCard(
                themes = communityThemes,
                selectedThemeId = state.selectedThemeId,
                onSelect = viewModel::selectTheme,
            )
            Spacer(Modifier.height(Spacing.space24))
        }
    }
}

@Composable
private fun ThemeSectionLabel(titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
        color = detourColors.textSecondary,
        modifier = Modifier.padding(horizontal = Spacing.space20),
    )
}

@Composable
private fun ThemeChoiceCard(
    themes: List<AppTheme>,
    selectedThemeId: String,
    onSelect: (String) -> Unit,
) {
    if (themes.isEmpty()) return
    DetourCard(
        Modifier
            .padding(horizontal = Spacing.space16)
            .selectableGroup(),
    ) {
        themes.forEachIndexed { index, theme ->
            ChoiceRow(
                title = stringResource(themeLabel(theme)),
                selected = selectedThemeId == theme.id,
                onClick = { onSelect(theme.id) },
                trailing = { ThemePalettePreview(theme) },
            )
            if (index < themes.lastIndex) GroupDivider(startInset = 56)
        }
    }
}

@Composable
private fun ThemePalettePreview(theme: AppTheme) {
    val preview = theme.colors

    Box(
        modifier = Modifier
            .padding(start = Spacing.space8, end = Spacing.space4)
            .size(width = 96.dp, height = 48.dp)
            .background(preview.background, AppShapes.extraSmall)
            .border(1.dp, preview.border, AppShapes.extraSmall)
            .padding(5.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(preview.surface, AppShapes.extraSmall)
                .padding(horizontal = 7.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(
                    Modifier
                        .size(width = 34.dp, height = 5.dp)
                        .background(preview.textPrimary, PillShape),
                )
                Box(
                    Modifier
                        .size(width = 24.dp, height = 4.dp)
                        .background(preview.textSecondary, PillShape),
                )
            }
            Box(
                Modifier
                    .size(width = 23.dp, height = 18.dp)
                    .background(preview.accent, AppShapes.extraSmall),
            )
        }
    }
}
