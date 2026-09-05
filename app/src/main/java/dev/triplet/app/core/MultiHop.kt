package dev.triplet.app.core

/**
 * Persisted reference to the first hop of a two-hop VPN chain.
 *
 * Only a saved single VLESS profile or the imported WARP profile can be used as
 * an entry hop. Subscription providers are intentionally excluded because
 * Detour currently owns one subscription provider/cache namespace.
 */
sealed interface MultiHopEntryRef {
    data class Vless(val keyId: String) : MultiHopEntryRef {
        init {
            require(isValidKeyId(keyId)) { "invalid multi-hop VLESS key id" }
        }
    }

    data object Warp : MultiHopEntryRef

    /** Non-empty persisted data that Detour cannot safely interpret. */
    data object Invalid : MultiHopEntryRef

    companion object {
        private const val WARP = "warp"
        private const val VLESS_PREFIX = "vless:"
        private const val MAX_KEY_ID_LENGTH = 128

        fun fromStored(raw: String?): MultiHopEntryRef? {
            val value = raw?.trim().orEmpty()
            if (value.isEmpty()) return null
            if (value == WARP) return Warp
            if (value.startsWith(VLESS_PREFIX)) {
                val id = value.removePrefix(VLESS_PREFIX)
                return if (isValidKeyId(id)) Vless(id) else Invalid
            }
            return Invalid
        }

        fun toStored(ref: MultiHopEntryRef?): String = when (ref) {
            null -> ""
            is Vless -> VLESS_PREFIX + ref.keyId
            Warp -> WARP
            Invalid -> throw IllegalArgumentException("invalid multi-hop entry cannot be persisted")
        }

        private fun isValidKeyId(value: String): Boolean =
            value.isNotBlank() &&
                value.length <= MAX_KEY_ID_LENGTH &&
                value.none { it.code < 0x20 || it.code == 0x7f || it == ':' }
    }
}

internal fun MultiHopEntryRef.conflictsWithExit(
    activeVpn: VpnProfileKind,
    activeVlessId: String?,
): Boolean = when (this) {
    is MultiHopEntryRef.Vless -> activeVpn == VpnProfileKind.VLESS && keyId == activeVlessId
    MultiHopEntryRef.Warp -> activeVpn == VpnProfileKind.WARP
    MultiHopEntryRef.Invalid -> true
}
