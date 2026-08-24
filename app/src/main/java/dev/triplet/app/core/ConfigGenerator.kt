package dev.triplet.app.core

/**
 * Pure settings -> mihomo YAML compiler. Unit-tested without a device.
 */
object ConfigGenerator {

    const val MTU = 1500
    const val INET4 = "172.19.0.1/30"
    const val INET6 = "fdfe:dcba:9876::1/126"

    val LAN_PREFIXES = listOf(
        "0.0.0.0/8", "10.0.0.0/8", "100.64.0.0/10", "127.0.0.0/8",
        "169.254.0.0/16", "172.16.0.0/12", "192.0.0.0/24", "192.0.2.0/24",
        "192.168.0.0/16", "198.18.0.0/15", "224.0.0.0/3",
        "fc00::/7", "fe80::/10",
    )

    private val ROUTE_ADDRESS = listOf("0.0.0.0/1", "128.0.0.0/1", "::/1", "8000::/1")

    fun build(input: RoutingInput): String {
        val attr = { pkg: String ->
            when (input.attribution) {
                Attribution.PROCESS_NAME -> "PROCESS-NAME,$pkg"
                Attribution.UID -> "UID,${input.vpnUids[pkg]}"
            }
        }
        val rules = buildList {
            input.profile?.let { input.vpnApps.forEach { pkg -> add("- ${attr(pkg)},VLESS") } }
            input.dpiApps.forEach { pkg ->
                add("- AND,((${attr(pkg)}),(NETWORK,UDP),(DST-PORT,443)),REJECT")
            }
            input.dpiApps.forEach { pkg -> add("- ${attr(pkg)},DPI") }
            if (input.apiLevel < 33) {
                LAN_PREFIXES.forEach { add("- IP-CIDR,$it,REJECT,no-resolve") }
            }
            add("- MATCH,DIRECT")
        }.joinToString("\n")

        // mihomo требует единый список proxies; оба исходящих (VLESS + SOCKS5 DPI)
        // объявляются в одном блоке.
        val proxies = buildList {
            input.profile?.let { p ->
                add(
                    """
                    - name: VLESS
                      type: vless
                      server: ${p.server}
                      port: ${p.port}
                      uuid: ${p.uuid}
                      network: tcp
                      udp: true
                      tls: true
                      flow: ${p.flow}
                      client-fingerprint: ${p.fingerprint}
                      servername: ${p.sni}
                      reality-opts:
                        public-key: ${p.publicKey}
                        short-id: ${p.shortId}
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

        val excludeLan = if (input.apiLevel >= 33) {
            "\n" + LAN_PREFIXES.joinToString("\n") { "      - $it" }
        } else " []"

        return """
        mode: rule
        log-level: info
        ipv6: true
        find-process-mode: strict
        mixed-port: ${input.mixedPort}
        bind-address: 127.0.0.1
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
          route-address:${yamlItems(ROUTE_ADDRESS)}
          route-exclude-address:$excludeLan
          dns-hijack:
            - any:53
        dns:
          enable: true
          enhanced-mode: fake-ip
          nameserver:
            - https://1.1.1.1/dns-query
        proxies:
        $proxies
        rules:
        $rules
        """.trimIndent()
    }

    private fun yamlItems(items: List<String>) =
        "\n" + items.joinToString("\n") { "      - $it" }
}
