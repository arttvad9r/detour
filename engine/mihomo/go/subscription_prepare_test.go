package engine

import "testing"

func TestPreparedSubscriptionFallsBackToV2RayConversion(t *testing.T) {
	const body = "vless://a1b2c3d4-eacc-4433-981b-7e5f9a8b@142.98.76.54:34888?encryption=none&security=reality&type=tcp&sni=github.io&fp=chrome&pbk=ppQ9FwLrLIa0AOrp1WvcyiaQ37vg2WSy_CD4bIdiTUw&sid=6ba85179f3a2b4c5&flow=xtls-rprx-vision#My-VLESS-Reality-Vision"

	proxies, err := parsePreparedSubscriptionProxies([]byte(body))
	if err != nil {
		t.Fatalf("valid VLESS subscription body was rejected: %v", err)
	}
	if len(proxies) != 1 {
		t.Fatalf("got %d proxies, want 1", len(proxies))
	}
	if got := proxies[0]["name"]; got != "My-VLESS-Reality-Vision" {
		t.Fatalf("proxy name = %v, want My-VLESS-Reality-Vision", got)
	}
	if got := proxies[0]["type"]; got != "vless" {
		t.Fatalf("proxy type = %v, want vless", got)
	}
}

func TestPreparedSubscriptionKeepsOnlyUniqueVlessNodes(t *testing.T) {
	const body = `
proxies:
  - name: Finland - 1
    type: vless
    server: one.example.com
    port: 443
  - name: Ignore SOCKS
    type: socks5
    server: socks.example.com
    port: 1080
  - name: Finland - 1
    type: vless
    server: duplicate.example.com
    port: 443
  - name: Russia - 2
    type: VLESS
    server: two.example.com
    port: 443
`

	proxies, err := parsePreparedSubscriptionProxies([]byte(body))
	if err != nil {
		t.Fatalf("valid YAML provider was rejected: %v", err)
	}
	if len(proxies) != 2 {
		t.Fatalf("got %d proxies, want 2 unique VLESS nodes", len(proxies))
	}
	if proxies[0]["name"] != "Finland - 1" || proxies[1]["name"] != "Russia - 2" {
		t.Fatalf("unexpected retained nodes: %v, %v", proxies[0]["name"], proxies[1]["name"])
	}
}
