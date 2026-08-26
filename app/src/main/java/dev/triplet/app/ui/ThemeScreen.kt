package dev.triplet.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.triplet.app.R
import dev.triplet.app.data.RoutesStore
import kotlinx.coroutines.launch

@Composable
fun ThemeScreen(store: RoutesStore, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val c = detourColors
    val settings by store.settings.collectAsState(initial = null)
    // Пустой themeId означает дефолтную Лаванду (как AppTheme.byId).
    val current = settings?.themeId?.ifBlank { null } ?: AppTheme.LAVENDA.id

    Column(
        modifier.fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(stringResource(R.string.theme_title), onBack)
        Spacer(Modifier.height(Spacing.space8))

        // Компактный селектор: три свотча + название + радио, одна группа.
        DetourCard(Modifier.padding(horizontal = Spacing.space16)) {
            AppTheme.entries.forEachIndexed { i, t ->
                val selected = current == t.id
                RadioRow(
                    title = stringResource(themeLabel(t)),
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
            modifier = Modifier.padding(horizontal = Spacing.space20),
        )
        Spacer(Modifier.height(Spacing.space24))
    }
}

/** Три маленьких свотча темы: фон, акцент, активный зелёный. */
@Composable
private fun ThemeSwatches(t: AppTheme) {
    Row(Modifier.padding(end = Spacing.space12)) {
        listOf(t.colors.background, t.colors.accent, t.colors.active).forEach { color ->
            Box(
                Modifier
                    .padding(start = 4.dp)
                    .size(14.dp)
                    .background(color, CircleShape)
                    .border(1.dp, detourColors.border, CircleShape),
            )
        }
    }
}
