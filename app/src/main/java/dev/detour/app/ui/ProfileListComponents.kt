package dev.detour.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.detour.app.R
import dev.detour.app.core.VlessKey
import dev.detour.app.core.VpnProfileKind
import dev.detour.app.core.WarpProfile
import dev.detour.app.core.WireGuardFamily

@Composable
internal fun ProfileSectionTitle(title: String) {
    val c = detourColors
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = c.textSecondary,
        modifier = Modifier.padding(horizontal = Spacing.space20, vertical = Spacing.space8),
    )
}

@Composable
internal fun ProfileKeyList(
    items: List<VlessKey>,
    kind: VpnProfileKind,
    activeVpn: VpnProfileKind,
    activeVlessId: String?,
    onEdit: (VlessKey) -> Unit,
    onDelete: (String) -> Unit,
    onSelect: (String) -> Unit,
) {
    if (items.isEmpty()) return

    DetourCard(
        Modifier
            .padding(horizontal = Spacing.space16)
            .animateContentSize(Motion.SIZE_SPEC)
            .selectableGroup(),
    ) {
        items.forEachIndexed { index, key ->
            val profile = remember(key.uri) { parsedProfile(key) }
            val selected = activeVpn == kind && key.id == activeVlessId
            // The stored name wins over the link's own label so a rename sticks.
            val title = key.name.ifBlank { profile?.let(::autoProfileName).orEmpty() }
            val subtitle = when {
                profile == null -> "—"
                profile.isSubscription -> stringResource(R.string.profile_subscription_row_subtitle, profile.server)
                else -> stringResource(R.string.profile_vless_row_subtitle, profile.server, profile.port)
            }
            CompactProfileRow(
                title = title,
                subtitle = subtitle,
                selected = selected,
                busy = false,
                editDescription = stringResource(R.string.key_edit),
                deleteDescription = stringResource(R.string.key_delete),
                onEdit = { onEdit(key) },
                onDelete = { onDelete(key.id) },
                onClick = { if (!selected) onSelect(key.id) },
            )
            if (index < items.lastIndex) GroupDivider(startInset = 52)
        }
    }
}

@Composable
internal fun WireGuardProfileList(
    profiles: List<WarpProfile>,
    activeWireGuardId: String?,
    activeVpn: VpnProfileKind,
    importing: Boolean,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClick: (String) -> Unit,
) {
    if (profiles.isEmpty()) return

    DetourCard(
        Modifier
            .padding(horizontal = Spacing.space16)
            .animateContentSize(Motion.SIZE_SPEC)
            .selectableGroup(),
    ) {
        profiles.forEachIndexed { index, profile ->
            val protocol = stringResource(
                when (profile.family) {
                    WireGuardFamily.WARP -> R.string.protocol_warp
                    WireGuardFamily.AMNEZIAWG -> R.string.profile_amneziawg
                    WireGuardFamily.AMNEZIAWG_31 -> R.string.profile_amneziawg_31
                },
            )
            val selected = activeVpn == VpnProfileKind.WARP && activeWireGuardId == profile.id
            CompactProfileRow(
                title = profile.displayName,
                subtitle = stringResource(R.string.profile_wireguard_row_subtitle, protocol, profile.proxies.size),
                selected = selected,
                busy = importing,
                editDescription = stringResource(R.string.key_edit),
                deleteDescription = stringResource(R.string.key_delete),
                onEdit = { onEdit(profile.id) },
                onDelete = { onDelete(profile.id) },
                onClick = { if (!selected) onClick(profile.id) },
            )
            if (index < profiles.lastIndex) GroupDivider(startInset = 52)
        }
    }
}

@Composable
private fun CompactProfileRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    busy: Boolean,
    editDescription: String,
    deleteDescription: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
) {
    val c = detourColors
    Row(
        Modifier
            .fillMaxWidth()
            .detourSelectable(
                selected = selected,
                onClick = onClick,
                idleColor = if (selected) c.surfaceSoft else Color.Transparent,
                pressedColor = c.surfaceSelected,
                pressScale = Motion.PRESS_RADIO,
            )
            .heightIn(min = 64.dp)
            .padding(start = Spacing.space16, top = Spacing.space8, bottom = Spacing.space8, end = Spacing.space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectionMark(selected)
        Column(
            Modifier
                .padding(start = Spacing.space12)
                .weight(1f),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = c.textPrimary,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
                modifier = Modifier.padding(top = Spacing.space2),
            )
        }
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.padding(horizontal = Spacing.space12).size(18.dp),
                strokeWidth = 2.dp,
                color = c.accent,
            )
        } else {
            DetourIconButton(onClick = onEdit, size = 36) {
                Icon(
                    painterResource(R.drawable.ic_edit),
                    contentDescription = "$editDescription: $title",
                    tint = c.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
            DetourIconButton(onClick = onDelete, size = 36) {
                Icon(
                    painterResource(R.drawable.ic_delete),
                    contentDescription = "$deleteDescription: $title",
                    tint = c.error,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
internal fun EmptyProfilesCard() {
    val c = detourColors
    DetourCard(Modifier.padding(horizontal = Spacing.space16)) {
        Text(
            stringResource(R.string.profile_empty),
            style = MaterialTheme.typography.bodySmall,
            color = c.textSecondary,
            modifier = Modifier.padding(Spacing.space16),
        )
    }
}

@Composable
internal fun ProfileOperationNotice(status: WarpImportStatus) {
    val c = detourColors
    val importing = status == WarpImportStatus.IMPORTING
    val error = status == WarpImportStatus.NO_COMPATIBLE_PROXIES || status == WarpImportStatus.ERROR
    if (!importing && !error) return

    Row(
        modifier = Modifier
            .padding(horizontal = Spacing.space16)
            .fillMaxWidth()
            .background(if (error) c.errorSoft else c.surfaceSoft, AppShapes.small)
            .border(1.dp, if (error) c.error.copy(alpha = 0.30f) else c.border, AppShapes.small)
            .padding(Spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (importing) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = c.accent,
            )
        } else {
            Icon(
                painterResource(R.drawable.ic_warning),
                contentDescription = null,
                tint = c.error,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = when (status) {
                WarpImportStatus.IMPORTING -> stringResource(R.string.profile_importing)
                WarpImportStatus.NO_COMPATIBLE_PROXIES -> stringResource(R.string.profile_import_invalid)
                WarpImportStatus.ERROR -> stringResource(R.string.profile_import_error)
                WarpImportStatus.IDLE -> ""
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (error) c.textPrimary else c.textSecondary,
            modifier = Modifier
                .padding(start = Spacing.space12)
                .weight(1f),
        )
    }
}

@Composable
internal fun ProfileImportErrorNotice(message: String = stringResource(R.string.profile_import_error)) {
    val c = detourColors
    Row(
        modifier = Modifier
            .padding(horizontal = Spacing.space16)
            .fillMaxWidth()
            .background(c.errorSoft, AppShapes.small)
            .border(1.dp, c.error.copy(alpha = 0.30f), AppShapes.small)
            .padding(Spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_warning),
            contentDescription = null,
            tint = c.error,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = c.textPrimary,
            modifier = Modifier.padding(start = Spacing.space12),
        )
    }
}
