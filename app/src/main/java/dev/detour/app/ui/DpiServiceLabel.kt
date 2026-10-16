package dev.detour.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.detour.app.R
import dev.detour.app.core.DpiProxyTestCatalog

@Composable
internal fun dpiServiceLabel(id: String): String = when (id) {
    "general" -> stringResource(R.string.dpi_service_general)
    "social" -> stringResource(R.string.dpi_service_social)
    "turkiye" -> stringResource(R.string.dpi_service_turkiye)
    "googlevideo" -> stringResource(R.string.dpi_service_googlevideo)
    else -> DpiProxyTestCatalog.domainLists.firstOrNull { it.id == id }?.displayName.orEmpty()
}
