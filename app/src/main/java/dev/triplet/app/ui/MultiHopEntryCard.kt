package dev.triplet.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.triplet.app.R
import dev.triplet.app.core.MultiHopEntryRef
import dev.triplet.app.core.VlessKey
import dev.triplet.app.core.VpnProfileKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiHopEntryCard(
    activeVpn: VpnProfileKind,
    activeVlessId: String?,
    vlessEntries: List<VlessKey>,
    warpAvailable: Boolean,
    selectedEntry: MultiHopEntryRef?,
    onSelectEntry: (MultiHopEntryRef?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val availableVless = vlessEntries.filter { key ->
        activeVpn != VpnProfileKind.VLESS || key.id != activeVlessId
    }
    val selectedLabel = when (selectedEntry) {
        null -> null
        is MultiHopEntryRef.Vless -> if (
            activeVpn != VpnProfileKind.VLESS || selectedEntry.keyId != activeVlessId
        ) {
            vlessEntries.firstOrNull { it.id == selectedEntry.keyId }?.name?.let {
                stringResource(R.string.multi_hop_vless_label, it)
            }
        } else null
        MultiHopEntryRef.Warp -> if (warpAvailable && activeVpn != VpnProfileKind.WARP) {
            stringResource(R.string.multi_hop_warp_label)
        } else null
        MultiHopEntryRef.Invalid -> null
    }
    val subtitle = when {
        selectedEntry == null -> stringResource(R.string.multi_hop_summary_off)
        selectedLabel == null -> stringResource(R.string.multi_hop_unavailable)
        else -> stringResource(R.string.multi_hop_summary_on, selectedLabel)
    }

    DetourCard(modifier) {
        DetourNavigationRow(
            title = stringResource(R.string.multi_hop_title),
            subtitle = subtitle,
            iconRes = R.drawable.ic_routes,
            onClick = { showPicker = true },
        )
    }

    if (showPicker) {
        ModalBottomSheet(
            onDismissRequest = { showPicker = false },
            containerColor = detourColors.background,
            contentColor = detourColors.textPrimary,
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.multi_hop_entry_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = detourColors.textPrimary,
                    modifier = Modifier.padding(
                        start = Spacing.space20,
                        end = Spacing.space20,
                        bottom = Spacing.space12,
                    ),
                )
                DetourCard(Modifier.padding(horizontal = Spacing.space16)) {
                    ChoiceRow(
                        title = stringResource(R.string.multi_hop_off),
                        subtitle = stringResource(R.string.multi_hop_summary_off),
                        selected = selectedEntry == null,
                        onClick = {
                            showPicker = false
                            onSelectEntry(null)
                        },
                    )
                    if (availableVless.isNotEmpty() || (warpAvailable && activeVpn != VpnProfileKind.WARP)) {
                        GroupDivider(startInset = ChoiceRowDividerInset)
                    }
                    availableVless.forEachIndexed { index, key ->
                        val ref = MultiHopEntryRef.Vless(key.id)
                        ChoiceRow(
                            title = stringResource(R.string.multi_hop_vless_label, key.name),
                            selected = selectedEntry == ref,
                            onClick = {
                                showPicker = false
                                onSelectEntry(ref)
                            },
                        )
                        val hasWarp = warpAvailable && activeVpn != VpnProfileKind.WARP
                        if (index < availableVless.lastIndex || hasWarp) {
                            GroupDivider(startInset = ChoiceRowDividerInset)
                        }
                    }
                    if (warpAvailable && activeVpn != VpnProfileKind.WARP) {
                        ChoiceRow(
                            title = stringResource(R.string.multi_hop_warp_label),
                            selected = selectedEntry == MultiHopEntryRef.Warp,
                            onClick = {
                                showPicker = false
                                onSelectEntry(MultiHopEntryRef.Warp)
                            },
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.space24))
            }
        }
    }
}
