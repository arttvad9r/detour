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

    private val ROUTE_ADDRESS = listOf("0.0.0.0/1", "128.0.0.0/1", "::/0")

    fun build(input: RoutingInput): String {
        require(input.vpnUids.keys.containsAll(input.vpnApps + input.dpiApps)) {
            "missing uid resolution for routed packages"
        }
        // Приложения атрибутируются по UID (резолвится host-side через VpnService).
        val attr = { pkg: String -> "UID,${input.vpnUids[pkg]}" }
        val rules = buildList {
            add("- IP-CIDR6,::/0,REJECT,no-resolve")
            input.profile?.let { input.vpnApps.forEach { pkg -> add("- ${attr(pkg)},VLESS") } }
            input.dpiApps.forEach { pkg ->
                add("- AND,((${attr(pkg)}),(NETWORK,UDP),(DST-PORT,443)),REJECT")
            }
            input.dpiApps.forEach { pkg -> add("- ${attr(pkg)},DPI") }
            if (input.apiLevel < 33) {
                LAN_PREFIXES.forEach { add("- IP-CIDR,$it,REJECT,no-resolve") }
            }
            // Unknown UID ownership must fail closed rather than bypassing the VPN.
            add("- MATCH,REJECT")
        }.joinToString("\n")

        // mihomo требует единый список proxies; оба исходящих объявляются в одном блоке.
        val proxies = buildList {
            input.profile?.let { p ->
                add(
                    """
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
                )
            }
            add(
                """
                - name: DPI
                  type: socks5
                  server: 127.0.0.1
                  port: ${input.dpiPort}
                  udp: false
                """.trimIndent()
            )
        }.joinToString("\n")
        val probes = buildList {
            if (input.vpnApps.isNotEmpty() && input.profile != null) {
                add("""- name: PROBE_VLESS
  type: mixed
  listen: 127.0.0.1
  port: 10810
  proxy: VLESS""")
            }
            if (input.dpiApps.isNotEmpty()) {
                add("""- name: PROBE_DPI
  type: mixed
  listen: 127.0.0.1
  port: 10811
  proxy: DPI""")
            }
        }.joinToString("\n")

        // Шаблон flush-left; отступы фрагментов задают сами хелперы,
        // trimIndent не используется (смешанные отступы ломали YAML).
        val excludeLan = if (input.apiLevel >= 33) items(LAN_PREFIXES) else " []"

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
  route-address:${items(ROUTE_ADDRESS)}
  route-exclude-address:$excludeLan
  dns-hijack:
    - any:53
dns:
  enable: true
  enhanced-mode: redir-host
  nameserver:
    - ${yamlScalar(input.nameserver)}
proxies:
$proxies
listeners:
$probes
rules:
$rules""".trim()
    }

    // Элементы последовательности под ключом с отступом 2: элементы на 4 пробела.
    private fun items(items: List<String>) =
        "\n" + items.joinToString("\n") { "    - $it" }

    private fun yamlScalar(value: String): String {
        require(value.none { it.code < 0x20 || it.code == 0x7f }) { "control character in YAML value" }
        val safe = value.matches(Regex("[A-Za-z0-9._/@+-]+"))
        if (safe) return value
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    }
}
