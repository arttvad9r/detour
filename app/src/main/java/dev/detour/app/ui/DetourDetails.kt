package dev.detour.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import dev.detour.app.R

@Composable
internal fun DetourDetails(title: String, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val state = stringResource(if (expanded) R.string.details_expanded else R.string.details_collapsed)
    Column {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.semantics { stateDescription = state }) {
            Text(stringResource(if (expanded) R.string.details_hide else R.string.details_show, title))
        }
        if (expanded) content()
    }
}
