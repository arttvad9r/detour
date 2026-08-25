package dev.triplet.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.triplet.app.R
import dev.triplet.app.data.RoutesStore
import kotlinx.coroutines.launch

@Composable
fun ThemeScreen(store: RoutesStore, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val settings by store.settings.collectAsState(initial = null)
    // Пустой themeId означает дефолтную Лаванду (как AppTheme.byId).
    val current = settings?.themeId?.ifBlank { null } ?: AppTheme.LAVENDA.id

    Column(modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.theme_title), onBack)
        Spacer(Modifier.height(6.dp))

        AppTheme.entries.forEach { t ->
            val selected = current == t.id
            Row(
                Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 5.dp)
                    .background(t.scheme.background, RoundedCornerShape(20.dp))
                    .border(
                        1.5.dp,
                        if (selected) t.scheme.primary else hairline(),
                        RoundedCornerShape(20.dp),
                    )
                    .clickable { scope.launch { store.setTheme(t.id) } }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row {
                    listOf(t.scheme.primary, t.statusOn.second, t.statusIdle.second).forEach { c ->
                        Box(Modifier.padding(horizontal = 3.dp).size(16.dp).background(c, CircleShape))
                    }
                }
                Text(
                    t.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    color = t.scheme.onBackground,
                    modifier = Modifier.padding(start = 14.dp).weight(1f),
                )
                RadioButton(
                    selected = selected,
                    onClick = { scope.launch { store.setTheme(t.id) } },
                    colors = RadioButtonDefaults.colors(selectedColor = t.scheme.primary),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.theme_hint),
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
    }
}
