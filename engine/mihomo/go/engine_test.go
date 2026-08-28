package engine

import (
	"testing"

	"github.com/metacubex/mihomo/config"
)

func TestReadyIsFalseBeforeStart(t *testing.T) {
	if Ready() {
		t.Fatal("engine must not report readiness before Start")
	}
}

func TestMihomoAcceptsAmneziaWarpConfig(t *testing.T) {
	const yaml = `
mode: rule
ipv6: false
proxies:
- name: WARP_0
  type: wireguard
  server: warp.example.net
  port: 4500
  ip: 172.16.0.2
  private-key: "TJV14OLWSD/3QxSOiUEEyf/tJLfg38/eYoqsiRY65rc="
  public-key: "QH+99BoTQpVd+x+SpMwAQGm47sGJ7JqTap2hEXXxfMI="
  reserved: [1, 2, 3]
  allowed-ips: ["0.0.0.0/0"]
  udp: true
  mtu: 1280
  remote-dns-resolve: true
  dns: [1.1.1.1]
  amnezia-wg-option:
    jc: 4
    jmin: 40
    jmax: 70
    s1: 0
    s2: 0
    h1: 1
    h2: 2
    h3: 3
    h4: 4
    i1: "<b 0x1234>"
proxy-groups:
- name: WARP
  type: url-test
  url: http://speed.cloudflare.com/
  interval: 300
  tolerance: 50
  lazy: true
  proxies:
    - WARP_0
rules:
- MATCH,WARP
`
	if _, err := config.Parse([]byte(yaml)); err != nil {
		t.Fatalf("mihomo rejected WARP/AmneziaWG schema: %v", err)
	}
}
