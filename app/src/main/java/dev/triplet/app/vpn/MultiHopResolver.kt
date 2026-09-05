package dev.triplet.app.vpn

import dev.triplet.app.core.MultiHopEntryRef
import dev.triplet.app.core.ParseResult
import dev.triplet.app.core.VlessKeyParser
import dev.triplet.app.core.VpnOutbound
import dev.triplet.app.core.VpnProfileKind
import dev.triplet.app.core.conflictsWithExit
import dev.triplet.app.data.TriSettings

internal fun resolveMultiHopEntry(settings: TriSettings): VpnOutbound? {
    val ref = settings.multiHopEntry ?: return null
    require(ref !is MultiHopEntryRef.Invalid) { "invalid multi-hop entry" }
    require(!ref.conflictsWithExit(settings.activeVpn, settings.vlessKeys.activeId)) {
        "multi-hop entry conflicts with exit"
    }

    return when (ref) {
        is MultiHopEntryRef.Vless -> {
            val key = settings.vlessKeys.items.singleOrNull { it.id == ref.keyId }
                ?: throw IllegalArgumentException("multi-hop VLESS entry is unavailable")
            val parsed = VlessKeyParser.parse(key.uri) as? ParseResult.Ok
                ?: throw IllegalArgumentException("multi-hop VLESS entry is invalid")
            require(!parsed.profile.isSubscription) { "subscription cannot be a multi-hop entry" }
            VpnOutbound.Vless(parsed.profile)
        }
        MultiHopEntryRef.Warp -> {
            val profile = settings.warpProfile
                ?: throw IllegalArgumentException("multi-hop WARP entry is unavailable")
            VpnOutbound.Warp(profile)
        }
        MultiHopEntryRef.Invalid -> error("validated above")
    }
}
