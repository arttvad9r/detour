package dev.detour.app.core

import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress
import java.util.UUID

enum class VpnProfileKind {
    VLESS,
    SUBSCRIPTION,
    WARP;

    companion object {
        fun fromStored(value: String?): VpnProfileKind =
            entries.firstOrNull { it.name == value } ?: VLESS
    }
}

data class AmneziaWgOptions(
    val version: Int? = null,
    val jc: Int? = null,
    val jmin: Int? = null,
    val jmax: Int? = null,
    val s1: Int? = null,
    val s2: Int? = null,
    val s3: Int? = null,
    val s4: Int? = null,
    val h1: String? = null,
    val h2: String? = null,
    val h3: String? = null,
    val h4: String? = null,
    val i1: String? = null,
    val i2: String? = null,
    val i3: String? = null,
    val i4: String? = null,
    val i5: String? = null,
    val headerProtectionKey: String? = null,
    val contentPaddingAddition: String? = null,
    val rekeyAfterTime: String? = null,
    val rekeyTimeout: String? = null,
    val rejectAfterTime: String? = null,
    val keepaliveTimeout: String? = null,
    val maxHandshakeAttempts: String? = null,
    val randomTrailers: Boolean? = null,
    val disableCookies: Boolean? = null,
)

data class WarpProxy(
    val name: String,
    val server: String,
    val port: Int,
    val ip: String,
    val ipv6: String? = null,
    val privateKey: String,
    val publicKey: String,
    val preSharedKey: String? = null,
    val reserved: List<Int>,
    val allowedIps: List<String>,
    val udp: Boolean = true,
    val mtu: Int = 1280,
    val persistentKeepalive: Int? = null,
    val remoteDnsResolve: Boolean = true,
    val dns: List<String> = emptyList(),
    val amnezia: AmneziaWgOptions,
)

data class WarpProfile(
    val id: String,
    val name: String,
    val proxies: List<WarpProxy>,
) {
    init {
        require(id.isNotBlank())
        require(name.isNotBlank())
        require(proxies.isNotEmpty())
        proxies.forEach(::validateWarpProxy)
    }

    fun toJson(): String = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("proxies", JSONArray().apply {
            proxies.forEach { proxy -> put(proxy.toJson()) }
        })
    }.toString()

    companion object {
        fun create(name: String = "Cloudflare WARP", proxies: List<WarpProxy>) =
            WarpProfile(UUID.randomUUID().toString(), name, proxies)

        fun fromStored(json: String): WarpProfile? {
            if (json.isBlank()) return null
            return runCatching { fromJson(json) }.getOrNull()
        }

        fun fromJson(json: String): WarpProfile {
            val root = try { JSONObject(json) } catch (e: Exception) {
                throw IllegalArgumentException("invalid WARP profile JSON", e)
            }
            val array = root.getJSONArray("proxies")
            return WarpProfile(
                id = root.getString("id"),
                name = root.getString("name"),
                proxies = (0 until array.length()).map { i ->
                    warpProxyFromJson(array.getJSONObject(i))
                },
            )
        }
    }
}

private fun WarpProxy.toJson() = JSONObject().apply {
    put("name", name)
    put("server", server)
    put("port", port)
    put("ip", ip)
    put("ipv6", ipv6 ?: JSONObject.NULL)
    put("privateKey", privateKey)
    put("publicKey", publicKey)
    preSharedKey?.let { put("preSharedKey", it) }
    put("reserved", JSONArray(reserved))
    put("allowedIps", JSONArray(allowedIps))
    put("udp", udp)
    put("mtu", mtu)
    persistentKeepalive?.let { put("persistentKeepalive", it) }
    put("remoteDnsResolve", remoteDnsResolve)
    put("dns", JSONArray(dns))
    put("amnezia", JSONObject().apply {
        fun value(key: String, value: Any?) { if (value != null) put(key, value) }
        value("version", amnezia.version)
        value("jc", amnezia.jc)
        value("jmin", amnezia.jmin)
        value("jmax", amnezia.jmax)
        value("s1", amnezia.s1)
        value("s2", amnezia.s2)
        value("s3", amnezia.s3)
        value("s4", amnezia.s4)
        value("h1", amnezia.h1)
        value("h2", amnezia.h2)
        value("h3", amnezia.h3)
        value("h4", amnezia.h4)
        value("i1", amnezia.i1)
        value("i2", amnezia.i2)
        value("i3", amnezia.i3)
        value("i4", amnezia.i4)
        value("i5", amnezia.i5)
        value("headerProtectionKey", amnezia.headerProtectionKey)
        value("contentPaddingAddition", amnezia.contentPaddingAddition)
        value("rekeyAfterTime", amnezia.rekeyAfterTime)
        value("rekeyTimeout", amnezia.rekeyTimeout)
        value("rejectAfterTime", amnezia.rejectAfterTime)
        value("keepaliveTimeout", amnezia.keepaliveTimeout)
        value("maxHandshakeAttempts", amnezia.maxHandshakeAttempts)
        value("randomTrailers", amnezia.randomTrailers)
        value("disableCookies", amnezia.disableCookies)
    })
}

private fun warpProxyFromJson(obj: JSONObject): WarpProxy {
    val amz = obj.getJSONObject("amnezia")
    val reserved = obj.getJSONArray("reserved").let { a ->
        (0 until a.length()).map { a.getInt(it) }
    }
    val allowed = obj.getJSONArray("allowedIps").let { a ->
        (0 until a.length()).map { a.getString(it) }
    }
    val dns = obj.optJSONArray("dns")?.let { a ->
        (0 until a.length()).map { a.getString(it) }
    } ?: emptyList()
    return WarpProxy(
        name = obj.getString("name"),
        server = obj.getString("server"),
        port = obj.getInt("port"),
        ip = obj.getString("ip"),
        ipv6 = if (obj.has("ipv6") && !obj.isNull("ipv6")) obj.getString("ipv6") else null,
        privateKey = obj.getString("privateKey"),
        publicKey = obj.getString("publicKey"),
        preSharedKey = obj.optScalarStringOrNull("preSharedKey"),
        reserved = reserved,
        allowedIps = allowed,
        udp = obj.optBoolean("udp", true),
        mtu = obj.optInt("mtu", 1280),
        persistentKeepalive = obj.optIntOrNull("persistentKeepalive"),
        remoteDnsResolve = obj.optBoolean("remoteDnsResolve", true),
        dns = dns,
        amnezia = AmneziaWgOptions(
            version = amz.optIntOrNull("version"),
            jc = amz.optIntOrNull("jc"),
            jmin = amz.optIntOrNull("jmin"),
            jmax = amz.optIntOrNull("jmax"),
            s1 = amz.optIntOrNull("s1"),
            s2 = amz.optIntOrNull("s2"),
            s3 = amz.optIntOrNull("s3"),
            s4 = amz.optIntOrNull("s4"),
            h1 = amz.optScalarStringOrNull("h1"),
            h2 = amz.optScalarStringOrNull("h2"),
            h3 = amz.optScalarStringOrNull("h3"),
            h4 = amz.optScalarStringOrNull("h4"),
            i1 = amz.optScalarStringOrNull("i1"),
            i2 = amz.optScalarStringOrNull("i2"),
            i3 = amz.optScalarStringOrNull("i3"),
            i4 = amz.optScalarStringOrNull("i4"),
            i5 = amz.optScalarStringOrNull("i5"),
            headerProtectionKey = amz.optScalarStringOrNull("headerProtectionKey"),
            contentPaddingAddition = amz.optScalarStringOrNull("contentPaddingAddition"),
            rekeyAfterTime = amz.optScalarStringOrNull("rekeyAfterTime"),
            rekeyTimeout = amz.optScalarStringOrNull("rekeyTimeout"),
            rejectAfterTime = amz.optScalarStringOrNull("rejectAfterTime"),
            keepaliveTimeout = amz.optScalarStringOrNull("keepaliveTimeout"),
            maxHandshakeAttempts = amz.optScalarStringOrNull("maxHandshakeAttempts"),
            randomTrailers = amz.optBooleanOrNull("randomTrailers"),
            disableCookies = amz.optBooleanOrNull("disableCookies"),
        ),
    )
}

private fun JSONObject.optIntOrNull(key: String): Int? {
    if (!has(key) || isNull(key)) return null
    return when (val value = get(key)) {
        is Number -> value.toInt()
        is String -> value.trim().toIntOrNull()
        else -> null
    }
}

private fun JSONObject.optScalarStringOrNull(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return when (val value = get(key)) {
        is String -> value.trim().takeIf { it.isNotEmpty() }
        is Number -> value.toString()
        else -> null
    }
}

private fun JSONObject.optBooleanOrNull(key: String): Boolean? {
    if (!has(key) || isNull(key)) return null
    return when (val value = get(key)) {
        is Boolean -> value
        is String -> when (value.trim().lowercase()) {
            "true", "on", "1" -> true
            "false", "off", "0" -> false
            else -> null
        }
        is Number -> when (value.toInt()) {
            1 -> true
            0 -> false
            else -> null
        }
        else -> null
    }
}

private fun String.hasNoControlCharacters(): Boolean =
    none { it.code < 0x20 || it.code == 0x7f }

private fun requireRenderedScalar(value: String) {
    require(value.isNotBlank() && value.hasNoControlCharacters())
}

private fun requireBoundedScalar(value: String) {
    requireRenderedScalar(value)
    require(value.length <= 4096)
}

private fun isUint32OrRange(value: String): Boolean {
    val parts = value.split('-', limit = 2)
    if (parts.size !in 1..2 || parts.any { it.isBlank() || it.any { c -> !c.isDigit() } }) return false
    val first = parts[0].toLongOrNull() ?: return false
    if (first !in 0..0xffffffffL) return false
    if (parts.size == 1) return true
    val second = parts[1].toLongOrNull() ?: return false
    return second in first..0xffffffffL
}

fun validateWarpProxy(proxy: WarpProxy) {
    require(proxy.name.isNotBlank())
    requireRenderedScalar(proxy.server)
    require(proxy.port in 1..65535)
    requireIpLiteral(proxy.ip)
    proxy.ipv6?.let(::requireIpLiteral)
    requireRenderedScalar(proxy.privateKey)
    requireRenderedScalar(proxy.publicKey)
    proxy.preSharedKey?.let(::requireRenderedScalar)
    require(proxy.reserved.all { it in 0..255 })
    require(proxy.allowedIps.isNotEmpty() && proxy.allowedIps.all(::isValidCidr))
    require(proxy.mtu in 576..9000)
    require(proxy.persistentKeepalive == null || proxy.persistentKeepalive in 0..65535)
    require(proxy.dns.all(::isValidIpLiteral))
    val amz = proxy.amnezia
    require(amz.version == null || amz.version in 1..3)
    require(amz.jc == null || amz.jc in 0..128)
    require(amz.jmin == null || amz.jmin >= 0)
    require(amz.jmax == null || amz.jmax >= 0)
    require(amz.jmin == null || amz.jmax == null || amz.jmin <= amz.jmax)
    listOf(amz.h1, amz.h2, amz.h3, amz.h4).filterNotNull().forEach {
        require(isUint32OrRange(it))
    }
    listOf(
        amz.i1,
        amz.i2,
        amz.i3,
        amz.i4,
        amz.i5,
        amz.headerProtectionKey,
        amz.contentPaddingAddition,
        amz.rekeyAfterTime,
        amz.rekeyTimeout,
        amz.rejectAfterTime,
        amz.keepaliveTimeout,
        amz.maxHandshakeAttempts,
    ).filterNotNull().forEach(::requireBoundedScalar)
    if (listOf(
            amz.headerProtectionKey,
            amz.contentPaddingAddition,
            amz.rekeyAfterTime,
            amz.rekeyTimeout,
            amz.rejectAfterTime,
            amz.keepaliveTimeout,
            amz.maxHandshakeAttempts,
        ).any { it != null } || amz.randomTrailers != null || amz.disableCookies != null
    ) {
        require(amz.version == 3)
    }
}

private fun requireIpLiteral(value: String) {
    requireRenderedScalar(value)
    require(isValidIpLiteral(value))
}

private fun isValidIpLiteral(value: String): Boolean {
    if (value.isBlank() || value != value.trim() || !value.hasNoControlCharacters()) return false
    if (value.contains(':')) {
        // A colon cannot occur in a DNS hostname. Restrict InetAddress to the
        // IPv6-literal branch so validation never performs hostname resolution.
        return runCatching {
            InetAddress.getByName(value).address.size == 16
        }.getOrDefault(false)
    }
    val parts = value.split('.')
    if (parts.size != 4) return false
    return parts.all { part ->
        part.isNotEmpty() && part.all(Char::isDigit) &&
            part.toIntOrNull()?.let { it in 0..255 } == true
    }
}

private fun isValidCidr(value: String): Boolean {
    if (value.any { it.code < 0x20 || it.code == 0x7f }) return false
    val parts = value.split('/', limit = 2)
    if (parts.size != 2 || !isValidIpLiteral(parts[0])) return false
    val max = if (parts[0].contains(':')) 128 else 32
    return parts[1].toIntOrNull()?.let { it in 0..max } == true
}
