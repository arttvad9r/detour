package dev.detour.app.core

/**
 * Pure settings -> mihomo YAML compiler. Unit-tested without a device.
 */
object ConfigGenerator {

    const val MTU = 1500
    const val INET4 = "172.19.0.1/30"
    const val INET6 = "fdfe:dcba:9876::1/126"

    val IPV4_LOCAL_PREFIXES = listOf(
        "0.0.0.0/8", "10.0.0.0/8", "100.64.0.0/10", "127.0.0.0/8",
        "169.254.0.0/16", "172.16.0.0/12", "192.0.0.0/24", "192.0.2.0/24",
        "192.168.0.0/16", "198.18.0.0/15", "224.0.0.0/3",
    )
    val IPV6_LOCAL_PREFIXES = listOf(
        "::/128", "::1/128", "fc00::/7", "fe80::/10", "ff00::/8",
    )
    val LAN_PREFIXES = IPV4_LOCAL_PREFIXES + IPV6_LOCAL_PREFIXES

    val ANDROID_EXCLUDED_PREFIXES = LAN_PREFIXES.filterNot {
        it == "127.0.0.0/8" || it == "::1/128"
    }

    private val ROUTE_ADDRESS = listOf("0.0.0.0/1", "128.0.0.0/1", "::/0")
    private const val WARP_GROUP = "WARP"
    private const val ENTRY_VLESS = "ENTRY_VLESS"
    private const val ENTRY_WARP_GROUP = "ENTRY_WARP"
    private const val SUBSCRIPTION_GROUP = "SUBSCRIPTION"
    private const val SUBSCRIPTION_PROVIDER = "DETOUR_SUBSCRIPTION"
    private const val SUBSCRIPTION_USER_AGENT = "mihomo/1.19.30"
    private const val SUBSCRIPTION_AUTO_TEST_URL = "https://cp.cloudflare.com/generate_204"
    private const val SUBSCRIPTION_AUTO_INTERVAL_SECONDS = 900
    private const val SUBSCRIPTION_AUTO_TIMEOUT_MS = 3000
    private const val SUBSCRIPTION_AUTO_MAX_FAILED_TIMES = 2
    private const val SUBSCRIPTION_AUTO_TOLERANCE_MS = 100
    private const val MAX_WARP_PROXIES = 128

    fun build(input: RoutingInput): String {
        require(input.vpnUids.keys.containsAll(input.vpnApps + input.dpiApps)) {
            "missing uid resolution for routed packages"
        }
        DestinationRules.validate(input.destinationRules)
        require(input.chainEntry == null || input.vpn != null) { "multi-hop requires an exit VPN" }
        require(input.chainEntry !is VpnOutbound.Subscription) {
            "subscription cannot be used as a multi-hop entry"
        }
        require(!(input.chainEntry is VpnOutbound.Warp && input.vpn is VpnOutbound.Warp)) {
            "WARP cannot be both multi-hop entry and exit"
        }

        val subscription = input.vpn as? VpnOutbound.Subscription
        val subscriptionUrl = subscription?.url
        val subscriptionProviderPath = subscriptionUrl?.let(SubscriptionProviderMaterializer::localPath)
        val vpnTag = when (input.vpn) {
            is VpnOutbound.Vless -> "VLESS"
            is VpnOutbound.Subscription -> SUBSCRIPTION_GROUP
            is VpnOutbound.Warp -> WARP_GROUP
            null -> null
        }
        val entryTag = when (input.chainEntry) {
            is VpnOutbound.Vless -> ENTRY_VLESS
            is VpnOutbound.Warp -> ENTRY_WARP_GROUP
            is VpnOutbound.Subscription -> error("subscription cannot be used as a multi-hop entry")
            null -> null
        }
        val orderedDestinationRules = DestinationRules.orderedForCompilation(input.destinationRules)
        val usesVpn = input.vpnApps.isNotEmpty() || orderedDestinationRules.any { it.route == AppRoute.VPN }
        val usesDpi = input.dpiApps.isNotEmpty() || orderedDestinationRules.any { it.route == AppRoute.DPI }
        if (usesVpn) requireNotNull(vpnTag) { "destination rule requires VPN profile" }

        val warpProxies = (input.vpn as? VpnOutbound.Warp)?.profile?.let(::selectedWarpProxies).orEmpty()
        val entryWarpProxies = (input.chainEntry as? VpnOutbound.Warp)?.profile?.let(::selectedWarpProxies).orEmpty()
        val loopbackUser = yamlScalar(input.probeCredentials.username)
        val loopbackPassword = yamlScalar(input.probeCredentials.password)

        // Приложения атрибутируются по UID (резолвится host-side через VpnService).
        val attr = { pkg: String -> "UID,${input.vpnUids[pkg]}" }
        val rules = buildList {
            // Before API 33 VpnService has no excludeRoute(). Local/private
            // destinations therefore enter the TUN and must be rejected before
            // user overrides; otherwise a broad DIRECT/VPN rule could proxy or
            // bypass traffic that should remain device/local-network scoped.
            if (input.apiLevel < 33) {
                LAN_PREFIXES.forEach { prefix ->
                    val matcher = if (':' in prefix) "IP-CIDR6" else "IP-CIDR"
                    add("- $matcher,$prefix,REJECT,no-resolve")
                }
            }
            orderedDestinationRules.forEach { rule ->
                addAll(renderDestinationRule(rule, vpnTag))
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
            when (val entry = input.chainEntry) {
                is VpnOutbound.Vless -> add(renderVless(entry.profile, name = ENTRY_VLESS))
                is VpnOutbound.Warp -> entryWarpProxies.forEachIndexed { index, proxy ->
                    add(renderWarp(proxy, index, namePrefix = ENTRY_WARP_GROUP))
                }
                is VpnOutbound.Subscription -> error("subscription cannot be used as a multi-hop entry")
                null -> Unit
            }
            when (val vpn = input.vpn) {
                is VpnOutbound.Vless -> add(renderVless(vpn.profile, dialerProxy = entryTag))
                is VpnOutbound.Subscription -> Unit
                is VpnOutbound.Warp -> warpProxies.forEachIndexed { index, proxy ->
                    add(renderWarp(proxy, index, dialerProxy = entryTag))
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
            "\nproxy-providers:\n" + renderSubscriptionProvider(url, subscriptionProviderPath, entryTag)
        }.orEmpty()
        val groups = buildList {
            if (input.chainEntry is VpnOutbound.Warp) {
                add(renderWarpGroup(entryWarpProxies.size, ENTRY_WARP_GROUP, ENTRY_WARP_GROUP))
            }
            when (val vpn = input.vpn) {
                is VpnOutbound.Warp -> add(renderWarpGroup(warpProxies.size))
                is VpnOutbound.Subscription -> add(renderSubscriptionGroup(vpn))
                else -> Unit
            }
        }
        val proxyGroups = if (groups.isEmpty()) "" else "\nproxy-groups:\n" + groups.joinToString("\n")

        val probes = buildList {
            if (usesVpn) {
                val name = when (input.vpn) {
                    is VpnOutbound.Subscription -> "PROBE_SUBSCRIPTION"
                    is VpnOutbound.Vless -> "PROBE_VLESS"
                    is VpnOutbound.Warp -> "PROBE_WARP"
                    null -> null
                }
                if (name != null) {
                    add("""- name: $name
  type: mixed
  listen: 127.0.0.1
  port: 10810
  proxy: $vpnTag
  users:
    - username: $loopbackUser
      password: $loopbackPassword""")
                }
            }
            if (usesDpi) {
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
ipv6: true
unified-delay: true
find-process-mode: strict
profile:
  store-selected: false
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
  inet6-address:
    - $INET6
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

    private fun selectedWarpProxies(profile: WarpProfile): List<WarpProxy> {
        val recommended = profile.proxies.filter { it.name.contains("⭐") }
        return recommended.ifEmpty { profile.proxies }.take(MAX_WARP_PROXIES)
    }

    private fun renderDestinationRule(rule: DestinationRule, vpnTag: String?): List<String> {
        val matcher = when (rule.type) {
            DestinationRuleType.DOMAIN -> "DOMAIN,${rule.value}"
            DestinationRuleType.DOMAIN_SUFFIX -> "DOMAIN-SUFFIX,${rule.value}"
            DestinationRuleType.IP_CIDR -> {
                val type = if (':' in rule.value.substringBefore('/')) "IP-CIDR6" else "IP-CIDR"
                "$type,${rule.value}"
            }
        }
        val target = when (rule.route) {
            AppRoute.DIRECT -> "DIRECT"
            AppRoute.VPN -> requireNotNull(vpnTag) { "destination rule requires VPN profile" }
            AppRoute.DPI -> "DPI"
        }
        return if (rule.route == AppRoute.DPI) {
            listOf(
                "- AND,(($matcher),(NETWORK,UDP),(DST-PORT,443)),REJECT",
                "- $matcher,$target",
            )
        } else {
            listOf("- $matcher,$target")
        }
    }

    private fun renderVless(
        p: VlessProfile,
        name: String = "VLESS",
        dialerProxy: String? = null,
    ): String {
        require(!p.isSubscription) { "subscription cannot be rendered as a VLESS proxy" }
        val fields = mutableListOf(
            "- name: ${yamlScalar(name)}",
            "  type: vless",
            "  server: ${yamlScalar(p.server)}",
            "  port: ${p.port}",
            "  uuid: ${yamlScalar(p.uuid)}",
            "  network: tcp",
            "  udp: true",
            "  tls: true",
            "  flow: ${yamlScalar(p.flow)}",
            "  client-fingerprint: ${yamlScalar(p.fingerprint)}",
            "  servername: ${yamlScalar(p.sni)}",
            "  reality-opts:",
            "    public-key: ${yamlScalar(p.publicKey)}",
            "    short-id: ${yamlScalar(p.shortId)}",
        )
        dialerProxy?.let { fields += "  dialer-proxy: ${yamlScalar(it)}" }
        return fields.joinToString("\n")
    }

    private fun renderSubscriptionProvider(
        url: String,
        localPath: String?,
        dialerProxy: String? = null,
    ): String = buildString {
        val parsed = VlessKeyParser.parse(url) as? ParseResult.Ok
        require(parsed?.profile?.isSubscription == true) { "invalid subscription URL" }
        append("  $SUBSCRIPTION_PROVIDER:\n")
        if (SubscriptionProviderMaterializer.isInstalled()) {
            require(!localPath.isNullOrBlank()) { "subscription could not be materialized" }
            append("    type: file\n")
            append("    path: ${yamlScalar(localPath)}")
        } else {
            // Unit tests and non-Android callers keep the legacy HTTP source.
            // Production installs SubscriptionProviderMaterializer in DetourApp,
            // normalizing URI/base64 subscriptions before mihomo sees them.
            append("    type: http\n")
            append("    url: ${yamlScalar(url)}\n")
            append("    interval: 3600\n")
            append("    size-limit: 4194304\n")
            // Provider downloads are INNER connections inside mihomo. Force them
            // through DIRECT so Detour's fail-closed MATCH,REJECT does not reject
            // the subscription fetch before UID-attributed app traffic exists.
            append("    proxy: DIRECT\n")
            append("    header:\n")
            append("      User-Agent:\n")
            append("        - $SUBSCRIPTION_USER_AGENT")
        }
        dialerProxy?.let {
            append("\n    override:\n")
            append("      dialer-proxy: ${yamlScalar(it)}")
        }
    }

    private fun renderSubscriptionGroup(subscription: VpnOutbound.Subscription): String = buildString {
        append("- name: $SUBSCRIPTION_GROUP\n")
        when (subscription.selectionMode) {
            SubscriptionSelectionMode.MANUAL -> {
                append("  type: select\n")
                subscription.selectedNode?.takeIf { it.isNotBlank() }?.let {
                    append("  default-selected: ${yamlScalar(it)}\n")
                }
            }
            SubscriptionSelectionMode.AUTO -> {
                // Mihomo's url-test group keeps its current fast node until another
                // node beats it by tolerance, and triggers provider health checks
                // after repeated dial failures. This provides sticky failover without
                // an Android-side polling or reconnection loop.
                append("  type: url-test\n")
                append("  url: $SUBSCRIPTION_AUTO_TEST_URL\n")
                append("  interval: $SUBSCRIPTION_AUTO_INTERVAL_SECONDS\n")
                append("  lazy: true\n")
                append("  timeout: $SUBSCRIPTION_AUTO_TIMEOUT_MS\n")
                append("  max-failed-times: $SUBSCRIPTION_AUTO_MAX_FAILED_TIMES\n")
                append("  expected-status: 204\n")
                append("  tolerance: $SUBSCRIPTION_AUTO_TOLERANCE_MS\n")
            }
        }
        append("  use:\n")
        append("    - $SUBSCRIPTION_PROVIDER")
    }

    private fun renderWarp(
        p: WarpProxy,
        index: Int,
        namePrefix: String = WARP_GROUP,
        dialerProxy: String? = null,
    ): String {
        val fields = mutableListOf(
            "- name: ${namePrefix}_$index",
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
        dialerProxy?.let { fields += "  dialer-proxy: ${yamlScalar(it)}" }
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

    private fun renderWarpGroup(
        count: Int,
        groupName: String = WARP_GROUP,
        proxyPrefix: String = WARP_GROUP,
    ): String = buildString {
        require(count > 0)
        append("- name: $groupName\n")
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
        repeat(count) { append("    - ${proxyPrefix}_$it\n") }
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
