package dev.triplet.app.core

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class VpnProfileKind {
    VLESS,
    WARP;

    companion object {
        fun fromStored(value: String?): VpnProfileKind =
            entries.firstOrNull { it.name == value } ?: VLESS
    }
}

data class AmneziaWgOptions(
    val jc: Int? = null,
    val jmin: Int? = null,
    val jmax: Int? = null,
    val s1: Int? = null,
    val s2: Int? = null,
    val h1: Int? = null,
    val h2: Int? = null,
    val h3: Int? = null,
    val h4: Int? = null,
    val i1: String? = null,
    val i2: String? = null,
    val i3: String? = null,
    val i4: String? = null,
    val i5: String? = null,
)

data class WarpProxy(
    val name: String,
    val server: String,
    val port: Int,
    val ip: String,
    val ipv6: String? = null,
    val privateKey: String,
    val publicKey: String,
    val reserved: List<Int>,
    val allowedIps: List<String>,
    val udp: Boolean = true,
    val mtu: Int = 1280,
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
            val id = root.getString("id")
            val name = root.getString("name")
            val array = root.getJSONArray("proxies")
            val proxies = (0 until array.length()).map { i ->
                WarpProxy.fromJson(array.getJSONObject(i))
            }
            require(id.isNotBlank() && name.isNotBlank() && proxies.isNotEmpty())
            return WarpProfile(id, name, proxies)
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
    put("reserved", JSONArray(reserved))
    put("allowedIps", JSONArray(allowedIps))
    put("udp", udp)
    put("mtu", mtu)
    put("remoteDnsResolve", remoteDnsResolve)
    put("dns", JSONArray(dns))
    put("amnezia", JSONObject().apply {
        fun value(key: String, value: Any?) { if (value != null) put(key, value) }
        value("jc", amnezia.jc)
        value("jmin", amnezia.jmin)
        value("jmax", amnezia.jmax)
        value("s1", amnezia.s1)
        value("s2", amnezia.s2)
        value("h1", amnezia.h1)
        value("h2", amnezia.h2)
        value("h3", amnezia.h3)
        value("h4", amnezia.h4)
        value("i1", amnezia.i1)
        value("i2", amnezia.i2)
        value("i3", amnezia.i3)
        value("i4", amnezia.i4)
        value("i5", amnezia.i5)
    })
}

private fun WarpProxy.Companion_fromJsonPlaceholder() = Unit

private fun WarpProxy.Companion.fromJson(obj: JSONObject): WarpProxy = error("unreachable")

private object WarpProxyJson {
    fun fromJson(obj: JSONObject): WarpProxy {
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
        val proxy = WarpProxy(
            name = obj.getString("name"),
            server = obj.getString("server"),
            port = obj.getInt("port"),
            ip = obj.getString("ip"),
            ipv6 = obj.optString("ipv6").takeIf { it.isNotBlank() && it != "null" },
            privateKey = obj.getString("privateKey"),
            publicKey = obj.getString("publicKey"),
            reserved = reserved,
            allowedIps = allowed,
            udp = obj.optBoolean("udp", true),
            mtu = obj.optInt("mtu", 1280),
            remoteDnsResolve = obj.optBoolean("remoteDnsResolve", true),
            dns = dns,
            amnezia = AmneziaWgOptions(
                jc = amz.optIntOrNull("jc"),
                jmin = amz.optIntOrNull("jmin"),
                jmax = amz.optIntOrNull("jmax"),
                s1 = amz.optIntOrNull("s1"),
                s2 = amz.optIntOrNull("s2"),
                h1 = amz.optIntOrNull("h1"),
                h2 = amz.optIntOrNull("h2"),
                h3 = amz.optIntOrNull("h3"),
                h4 = amz.optIntOrNull("h4"),
                i1 = amz.optStringOrNull("i1"),
                i2 = amz.optStringOrNull("i2"),
                i3 = amz.optStringOrNull("i3"),
                i4 = amz.optStringOrNull("i4"),
                i5 = amz.optStringOrNull("i5"),
            ),
        )
        validateWarpProxy(proxy)
        return proxy
    }
}

private fun JSONObject.optIntOrNull(key: String): Int? =
    if (has(key) && !isNull(key)) getInt(key) else null

private fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) getString(key).takeIf { it.isNotBlank() } else null

fun validateWarpProxy(proxy: WarpProxy) {
    require(proxy.name.isNotBlank())
    require(proxy.server.isNotBlank())
    require(proxy.port in 1..65535)
    require(proxy.ip.isNotBlank())
    require(proxy.privateKey.isNotBlank() && proxy.publicKey.isNotBlank())
    require(proxy.reserved.all { it in 0..255 })
    require(proxy.allowedIps.isNotEmpty() && proxy.allowedIps.all { it.isNotBlank() })
    require(proxy.mtu in 576..9000)
    require(proxy.dns.all { it.isNotBlank() })
}

private fun WarpProxy.Companion.fromJsonCompat(obj: JSONObject): WarpProxy = WarpProxyJson.fromJson(obj)
