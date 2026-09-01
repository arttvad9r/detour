package dev.triplet.app.core

/**
 * Pure settings -> mihomo YAML compiler. Unit-tested without a device.
 */
object ConfigGenerator {

    const val MTU = 1500
    const val INET4 = "172.19.0.1/30"

    val LAN_PREFIXES = listOf(
        "0.0.0.0/8", "10.0.0.0/8", "100.64.0.0/10", "127.0.0.0/8",
        "169.254.0.0/16", "172.16.0.0/12", "192.0.0.0/24", "192.0.2.0/24",
        "192.168.0.0/16", "198.18.0.0/15", "224.0.0.0/3",
    )

    val ANDROID_EXCLUDED_PREFIXES = LAN_PREFIXES - "127.0.0.0/8"

    // The Android TUN is intentionally IPv4-only. Passing an IPv6 route to
    // gVisor while ipv6=false makes Engine.start fail with EFAULT on some
    // Android kernels.
    private val ROUTE_ADDRESS = listOf("0.0.0.0/1", "128.0.0.0/1")
    private const val WARP_GROUP = "WARP"
    private const val SUBSCRIPTION_GROUP = "SUBSCRIPTION"
    private const val SUBSCRIPTION_PROVIDER = "DETOUR_SUBSCRIPTION"
    private const val MAX_WARP_PROXIES = 128

    fun build(input: RoutingInput): String {
        require(input.vpnUids.keys.containsAll(input.vpnApps + input.dpiApps)) {
            "missing uid resolution for routed packages"
        }
        val subscriptionUrl = (input.vpn as? VpnOutbound.Subscription)?.url
        val vpnTag = when (input.vpn) {
            is VpnOutbound.Vless -> "VLESS"
            is VpnOutbound.Subscription -> SUBSCRIPTION_GROUP
            is VpnOutbound.Warp -> WARP_GROUP
            null -> null
        }
        val warpProxies = (input.vpn as? VpnOutbound.Warp)?.profile?.proxies?.let { all ->
            val recommended = all.filter { it.name.contains("⭐") }
            recommended.ifEmpty { all }.take(MAX_WARP_PROXIES)
        }.orEmpty()
        val loopbackUser = yamlScalar(input.probeCredentials.username)
        val loopbackPassword = yamlScalar(input.probeCredentials.password)

        // Приложения атрибутируются по UID (резолвится host-side через VpnService).
        val attr = { pkg: String -> "UID,${input.vpnUids[pkg]}" }
        val rules = buildList {
            add("- IP-CIDR6,::/0,REJECT,no-resolve")
            // Before API 33 VpnService has no excludeRoute(). LAN destinations
            // therefore enter the TUN and must be rejected before per-UID routes;
            // otherwise the UID rule wins first and proxies local traffic.
            if (input.apiLevel < 33) {
                LAN_PREFIXES.forEach { add("- IP-CIDR,$it,REJECT,no-resolve") }
            }
            vpnTag?.let { tag -> input.vpnApps.forEach { pkg -> add("- ${attr(pkg)},$tag") } }
            input.dpiApps.forEach { pkg ->
                add("- AND,((${attr(pkg)}),(NETWORK,UDP),(DST-PORT,443)),REJECT")
            }
            input.dpiApps.forEach { pkg -> add("- ${attr(pkg)},DPI") }
            // Unknown UID ownership must fail closed rather than bypassing the VPN.
            add("- MATCH,REJECT")
        }.joinToString("\n")

        // mihomo требует единый список proxies; subscription provider подключается
        // отдельно через proxy-providers и не создаёт фиктивный VLESS outbound.
        val proxies = buildList {
            when (val vpn = input.vpn) {
                is VpnOutbound.Vless -> add(renderVless(vpn.profile))
                is VpnOutbound.Subscription -> Unit
                is VpnOutbound.Warp -> warpProxies.forEachIndexed { index, proxy ->
                    add(renderWarp(proxy, index))
                }
                null -> Unit
            }
            add(
                """
                - name: DPI
                  type: socks5
                  server: 127.0.0.1
                  port: ${input.dpiPort}
                  username: $loopbackUser
                  password: $loopbackPassword
                  udp: false
                """.trimIndent()
            )
        }.joinToString("\n")

        val proxyProviders = subscriptionUrl?.let { url ->
            "\nproxy-providers:\n" + renderSubscriptionProvider(url)
        }.orEmpty()
        val proxyGroups = when (input.vpn) {
            is VpnOutbound.Warp -> "\nproxy-groups:\n" + renderWarpGroup(warpProxies.size)
            is VpnOutbound.Subscription -> "\nproxy-groups:\n" + renderSubscriptionGroup()
            else -> ""
        }

        val probes = buildList {
            if (input.vpnApps.isNotEmpty() && input.vpn != null) {
                val name = when (input.vpn) {
                    is VpnOutbound.Subscription -> "PROBE_SUBSCRIPTION"
                    is VpnOutbound.Vless -> "PROBE_VLESS"
                    is VpnOutbound.Warp -> "PROBE_WARP"
                    null -> error("unreachable")
                }
                add("""- name: $name
  type: mixed
  listen: 127.0.0.1
  port: 10810
  proxy: $vpnTag
  users:
    - username: $loopbackUser
      password: $loopbackPassword""")
            }
            if (input.dpiApps.isNotEmpty()) {
                add("""- name: PROBE_DPI
  type: mixed
  listen: 127.0.0.1
  port: 10811
  proxy: DPI
  users:
    - username: $loopbackUser
      password: $loopbackPassword""")
            }
        }.joinToString("\n")

        // Шаблон flush-left; отступы фрагментов задают сами хелперы,
        // trimIndent не используется (смешанные отступы ломали YAML).
        val excludeLan = if (input.apiLevel >= 33) items(LAN_PREFIXES) else " []"
        val dnsBootstrap = DnsOptions.bootstrapServer(input.nameserver)?.let {
            "\n  default-nameserver:\n    - ${yamlScalar(it)}"
        } ?: ""

        return """
mode: rule
log-level: info
ipv6: false
find-process-mode: strict
tun:
  enable: true
  stack: gvisor
  file-descriptor: ${input.tunFd}
  auto-route: false
  auto-detect-interface: false
  strict-route: false
  mtu: $MTU
  inet4-address:
    - $INET4
  inet6-address: []
  route-address:${items(ROUTE_ADDRESS)}
  route-exclude-address:$excludeLan
  dns-hijack:
    - any:53
dns:
  enable: true
  enhanced-mode: redir-host$dnsBootstrap
  nameserver:
    - ${yamlScalar(input.nameserver)}
proxies:
$proxies$proxyProviders$proxyGroups
listeners:
$probes
rules:
$rules""".trim()
    }

    private fun renderVless(p: VlessProfile): String {
        require(!p.isSubscription) { "subscription cannot be rendered as a VLESS proxy" }
        return """
        - name: VLESS
          type: vless
          server: ${yamlScalar(p.server)}
          port: ${p.port}
          uuid: ${yamlScalar(p.uuid)}
          network: tcp
          udp: true
          tls: true
          flow: ${yamlScalar(p.flow)}
          client-fingerprint: ${yamlScalar(p.fingerprint)}
          servername: ${yamlScalar(p.sni)}
          reality-opts:
            public-key: ${yamlScalar(p.publicKey)}
            short-id: ${yamlScalar(p.shortId)}
        """.trimIndent()
    }

    private fun renderSubscriptionProvider(url: String): String = buildString {
        val parsed = VlessKeyParser.parse(url) as? ParseResult.Ok
        require(parsed?.profile?.isSubscription == true) { "invalid subscription URL" }
        append("  $SUBSCRIPTION_PROVIDER:\n")
        append("    type: http\n")
        append("    url: ${yamlScalar(url)}\n")
        append("    interval: 3600\n")
        append("    size-limit: 4194304\n")
        append("    health-check:\n")
        append("      enable: true\n")
        append("      url: https://www.gstatic.com/generate_204\n")
        append("      interval: 300\n")
        append("      timeout: 5000\n")
        append("      lazy: false\n")
        append("      expected-status: 204")
    }

    private fun renderSubscriptionGroup(): String =
        """
        - name: $SUBSCRIPTION_GROUP
          type: fallback
          url: https://www.gstatic.com/generate_204
          interval: 300
          lazy: false
          timeout: 5000
          max-failed-times: 2
          expected-status: 204
          use:
            - $SUBSCRIPTION_PROVIDER
        """.trimIndent()

    private fun renderWarp(p: WarpProxy, index: Int): String {
        val fields = mutableListOf(
            "- name: WARP_$index",
            "  type: wireguard",
            "  server: ${yamlScalar(p.server)}",
            "  port: ${p.port}",
            "  ip: ${yamlScalar(p.ip)}",
        )
        p.ipv6?.let { fields += "  ipv6: ${yamlScalar(it)}" }
        fields += "  private-key: ${yamlScalar(p.privateKey)}"
        fields += "  public-key: ${yamlScalar(p.publicKey)}"
        if (p.reserved.isNotEmpty()) fields += "  reserved: [${p.reserved.joinToString(", ")}]"
        fields += "  allowed-ips: ${flowStrings(p.allowedIps)}"
        p.persistentKeepalive?.let { fields += "  persistent-keepalive: $it" }
        fields += "  udp: ${p.udp}"
        fields += "  mtu: ${p.mtu}"
        // DNS is a Detour setting. Imported WARP profile DNS must not silently
        // override the resolver selected in Settings -> DNS.
        fields += "  remote-dns-resolve: false"
        fields += "  amnezia-wg-option:"
        val a = p.amnezia
        fun int(name: String, value: Int?) { if (value != null) fields += "    $name: $value" }
        fun str(name: String, value: String?) { if (!value.isNullOrBlank()) fields += "    $name: ${yamlScalar(value)}" }
        int("jc", a.jc)
        int("jmin", a.jmin)
        int("jmax", a.jmax)
        int("s1", a.s1)
        int("s2", a.s2)
        int("h1", a.h1)
        int("h2", a.h2)
        int("h3", a.h3)
        int("h4", a.h4)
        str("i1", a.i1)
        str("i2", a.i2)
        str("i3", a.i3)
        str("i4", a.i4)
        str("i5", a.i5)
        return fields.joinToString("\n")
    }

    private fun renderWarpGroup(count: Int): String = buildString {
        require(count > 0)
        append("- name: $WARP_GROUP\n")
        // Do not continuously chase the lowest latency: changing the WireGuard
        // endpoint under long-lived UDP/QUIC sessions can stall video streams.
        // Fallback keeps the current node until it becomes unavailable.
        append("  type: fallback\n")
        append("  url: https://cp.cloudflare.com/generate_204\n")
        append("  interval: 300\n")
        append("  lazy: false\n")
        append("  timeout: 3000\n")
        append("  max-failed-times: 2\n")
        append("  expected-status: 204\n")
        append("  proxies:\n")
        repeat(count) { append("    - WARP_$it\n") }
    }.trimEnd()

    // Элементы последовательности под ключом с отступом 2: элементы на 4 пробела.
    private fun items(items: List<String>) =
        "\n" + items.joinToString("\n") { "    - $it" }

    private fun flowStrings(values: List<String>) =
        "[" + values.joinToString(", ") { yamlScalar(it) } + "]"

    private fun yamlScalar(value: String): String {
        require(value.none { it.code < 0x20 || it.code == 0x7f }) { "control character in YAML value" }
        val safe = value.matches(Regex("[A-Za-z0-9._/@+-]+"))
        if (safe) return value
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    }
}
