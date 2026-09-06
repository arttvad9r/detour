package dev.detour.app.vpn

import dev.detour.app.core.MultiHopEntryRef
import dev.detour.app.core.ParseResult
import dev.detour.app.core.VlessKeyParser
import dev.detour.app.core.VpnOutbound
import dev.detour.app.core.VpnProfileKind
import dev.detour.app.core.conflictsWithExit
import dev.detour.app.data.TriSettings

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
