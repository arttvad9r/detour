package engine

import (
	"context"
	"fmt"
	"sync/atomic"
	"testing"
	"time"
)

func TestSupportedSubscriptionProxyTypeAllowList(t *testing.T) {
	allowed := []string{
		"vless", "VMESS", " trojan ", "ss", "ssr", "hysteria", "hysteria2", "tuic", "anytls", "mieru",
	}
	for _, value := range allowed {
		if _, ok := canonicalSubscriptionProxyType(value); !ok {
			t.Fatalf("supported proxy type %q was rejected", value)
		}
	}

	blocked := []string{"socks5", "http", "direct", "reject", "dns", "wireguard", "openvpn", ""}
	for _, value := range blocked {
		if canonical, ok := canonicalSubscriptionProxyType(value); ok {
			t.Fatalf("blocked proxy type %q was accepted as %q", value, canonical)
		}
	}
}

func TestPreparedSubscriptionFallsBackToV2RayConversion(t *testing.T) {
	const body = "vless://a1b2c3d4-eacc-4433-981b-7e5f9a8b1234@142.98.76.54:34888?encryption=none&security=reality&type=tcp&sni=github.io&fp=chrome&pbk=ppQ9FwLrLIa0AOrp1WvcyiaQ37vg2WSy_CD4bIdiTUw&sid=6ba85179f3a2b4c5&flow=xtls-rprx-vision#My-VLESS-Reality-Vision"

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
	if got := proxies[0]["flow"]; got != "xtls-rprx-vision" {
		t.Fatalf("proxy flow = %v, want xtls-rprx-vision", got)
	}
}

func TestPreparedSubscriptionNormalizesHTTPUpgradeTransport(t *testing.T) {
	const body = "vless://11111111-1111-4111-8111-111111111111@203.0.113.10:443?security=tls&type=httpupgrade&path=%2Fedge%2F&host=cdn.example.com&sni=cdn.example.com&fp=chrome#HTTPUpgrade"

	proxies, err := parsePreparedSubscriptionProxies([]byte(body))
	if err != nil {
		t.Fatalf("HTTPUpgrade VLESS subscription body was rejected: %v", err)
	}
	if len(proxies) != 1 {
		t.Fatalf("got %d proxies, want 1", len(proxies))
	}
	proxy := proxies[0]
	if got := proxy["network"]; got != "ws" {
		t.Fatalf("network = %v, want ws", got)
	}
	wsOpts, ok := proxy["ws-opts"].(map[string]any)
	if !ok {
		t.Fatalf("ws-opts = %#v, want map", proxy["ws-opts"])
	}
	if enabled, _ := wsOpts["v2ray-http-upgrade"].(bool); !enabled {
		t.Fatalf("v2ray-http-upgrade = %#v, want true", wsOpts["v2ray-http-upgrade"])
	}
	if got := wsOpts["path"]; got != "/edge/" {
		t.Fatalf("ws path = %v, want /edge/", got)
	}
	headers, ok := wsOpts["headers"].(map[string]any)
	if !ok || headers["Host"] != "cdn.example.com" {
		t.Fatalf("ws headers = %#v, want Host=cdn.example.com", wsOpts["headers"])
	}
}

func TestPreparedSubscriptionKeepsUniqueAllowedRemoteProxyNodes(t *testing.T) {
	const body = `
proxies:
  - name: Finland - 1
    type: vless
    server: one.example.com
    port: 443
    uuid: 11111111-1111-4111-8111-111111111111
  - name: Trojan - 1
    type: TROJAN
    server: trojan.example.com
    port: 443
    password: secret
  - name: Ignore SOCKS
    type: socks5
    server: socks.example.com
    port: 1080
  - name: Finland - 1
    type: vless
    server: duplicate.example.com
    port: 443
    uuid: 22222222-2222-4222-8222-222222222222
  - name: Russia - 2
    type: VLESS
    server: two.example.com
    port: 443
    uuid: 33333333-3333-4333-8333-333333333333
`

	proxies, err := parsePreparedSubscriptionProxies([]byte(body))
	if err != nil {
		t.Fatalf("valid YAML provider was rejected: %v", err)
	}
	if len(proxies) != 3 {
		t.Fatalf("got %d proxies, want 3 unique allowed nodes", len(proxies))
	}
	if proxies[0]["name"] != "Finland - 1" || proxies[1]["name"] != "Trojan - 1" || proxies[2]["name"] != "Russia - 2" {
		t.Fatalf("unexpected retained nodes: %v, %v, %v", proxies[0]["name"], proxies[1]["name"], proxies[2]["name"])
	}
	if got := proxies[1]["type"]; got != "trojan" {
		t.Fatalf("Trojan type = %v, want canonical trojan", got)
	}
}

func TestPreparedSubscriptionSkipsInvalidVlessInsteadOfRejectingProvider(t *testing.T) {
	const body = `
proxies:
  - name: Broken
    type: vless
    server: broken.example.com
    port: not-a-port
    uuid: 44444444-4444-4444-8444-444444444444
  - name: Working
    type: vless
    server: working.example.com
    port: 443
    uuid: 55555555-5555-4555-8555-555555555555
`

	proxies, err := parsePreparedSubscriptionProxies([]byte(body))
	if err != nil {
		t.Fatalf("provider with one valid node was rejected: %v", err)
	}
	if len(proxies) != 1 || proxies[0]["name"] != "Working" {
		t.Fatalf("retained proxies = %v, want only Working", proxies)
	}
}

func TestSubscriptionLatencyDefaultsMatchMobileServerListTesting(t *testing.T) {
	if subscriptionLatencyTestURL != "https://www.gstatic.com/generate_204" {
		t.Fatalf("latency URL = %q, want stable generate_204 endpoint", subscriptionLatencyTestURL)
	}
	if subscriptionLatencyParallel != 5 {
		t.Fatalf("latency parallelism = %d, want 5", subscriptionLatencyParallel)
	}
}

func TestOfflineLatencyPreResolvesEndpointHostname(t *testing.T) {
	original := map[string]any{
		"name":    "Domain endpoint",
		"type":    "vless",
		"server":  "edge.example.com",
		"port":    443,
		"uuid":    "66666666-6666-4666-8666-666666666666",
		"tls":     true,
		"network": "tcp",
	}
	prepared, err := prepareOfflineLatencyProxyMapping(
		context.Background(),
		original,
		func(_ context.Context, host string) ([]string, error) {
			if host != "edge.example.com" {
				t.Fatalf("resolver host = %q, want edge.example.com", host)
			}
			return []string{"203.0.113.42"}, nil
		},
	)
	if err != nil {
		t.Fatalf("pre-resolve failed: %v", err)
	}
	if got := original["server"]; got != "edge.example.com" {
		t.Fatalf("original mapping mutated: server=%v", got)
	}
	if got := prepared["server"]; got != "203.0.113.42" {
		t.Fatalf("prepared server = %v, want 203.0.113.42", got)
	}
	if got := prepared["servername"]; got != "edge.example.com" {
		t.Fatalf("prepared servername = %v, want original hostname", got)
	}
}

func TestOfflineLatencyPreservesTrojanSNIWhenEndpointIsPreResolved(t *testing.T) {
	original := map[string]any{
		"name":     "Trojan endpoint",
		"type":     "trojan",
		"server":   "trojan.example.com",
		"port":     443,
		"password": "secret",
	}
	prepared, err := prepareOfflineLatencyProxyMapping(
		context.Background(),
		original,
		func(context.Context, string) ([]string, error) {
			return []string{"203.0.113.44"}, nil
		},
	)
	if err != nil {
		t.Fatalf("pre-resolve failed: %v", err)
	}
	if got := prepared["server"]; got != "203.0.113.44" {
		t.Fatalf("prepared server = %v, want 203.0.113.44", got)
	}
	if got := prepared["sni"]; got != "trojan.example.com" {
		t.Fatalf("prepared sni = %v, want original hostname", got)
	}
}

func TestOfflineLatencyPreservesWebSocketHostAsImplicitSNI(t *testing.T) {
	original := map[string]any{
		"name":    "WS endpoint",
		"type":    "vless",
		"server":  "edge.example.com",
		"port":    443,
		"uuid":    "77777777-7777-4777-8777-777777777777",
		"tls":     true,
		"network": "ws",
		"ws-opts": map[string]any{
			"headers": map[string]any{"Host": "cdn.example.com"},
		},
	}
	prepared, err := prepareOfflineLatencyProxyMapping(
		context.Background(),
		original,
		func(context.Context, string) ([]string, error) {
			return []string{"203.0.113.43"}, nil
		},
	)
	if err != nil {
		t.Fatalf("pre-resolve failed: %v", err)
	}
	if got := prepared["servername"]; got != "cdn.example.com" {
		t.Fatalf("prepared servername = %v, want websocket Host", got)
	}
}

func TestSubscriptionLatencyTestsCoverEveryNodeBeyondParallelLimit(t *testing.T) {
	const nodeCount = 25
	proxies := make([]map[string]any, 0, nodeCount)
	for index := 0; index < nodeCount; index++ {
		proxies = append(proxies, map[string]any{
			"name": fmt.Sprintf("Server %02d", index+1),
			"type": "vless",
		})
	}

	var calls atomic.Int32
	var active atomic.Int32
	var maxActive atomic.Int32
	results := runSubscriptionLatencyTests(
		proxies,
		func(_ context.Context, _ map[string]any) (int, error) {
			current := active.Add(1)
			for {
				previous := maxActive.Load()
				if current <= previous || maxActive.CompareAndSwap(previous, current) {
					break
				}
			}
			defer active.Add(-1)
			time.Sleep(20 * time.Millisecond)
			calls.Add(1)
			return 42, nil
		},
	)

	if got := int(calls.Load()); got != nodeCount {
		t.Fatalf("latency probe calls = %d, want %d", got, nodeCount)
	}
	if got := int(maxActive.Load()); got != subscriptionLatencyParallel {
		t.Fatalf("max concurrent latency probes = %d, want %d", got, subscriptionLatencyParallel)
	}
	if len(results) != nodeCount {
		t.Fatalf("latency results = %d, want %d", len(results), nodeCount)
	}
	for index, result := range results {
		wantName := fmt.Sprintf("Server %02d", index+1)
		if result.Name != wantName || result.DelayMs != 42 {
			t.Fatalf("result[%d] = %+v, want name=%q delay=42", index, result, wantName)
		}
	}
}
