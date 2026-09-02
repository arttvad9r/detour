package engine

import (
	"context"
	"fmt"
	"sync/atomic"
	"testing"
)

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

func TestPreparedSubscriptionKeepsOnlyUniqueSupportedVlessNodes(t *testing.T) {
	const body = `
proxies:
  - name: Finland - 1
    type: vless
    server: one.example.com
    port: 443
    uuid: 11111111-1111-4111-8111-111111111111
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
	if len(proxies) != 2 {
		t.Fatalf("got %d proxies, want 2 unique VLESS nodes", len(proxies))
	}
	if proxies[0]["name"] != "Finland - 1" || proxies[1]["name"] != "Russia - 2" {
		t.Fatalf("unexpected retained nodes: %v, %v", proxies[0]["name"], proxies[1]["name"])
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
	results := runSubscriptionLatencyTests(
		proxies,
		func(_ context.Context, _ map[string]any) (int, error) {
			calls.Add(1)
			return 42, nil
		},
	)

	if got := int(calls.Load()); got != nodeCount {
		t.Fatalf("latency probe calls = %d, want %d", got, nodeCount)
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
