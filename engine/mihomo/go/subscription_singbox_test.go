package engine

import "testing"

const singBoxFixture = `{
  "outbounds": [
    {
      "type": "selector",
      "tag": "ignored-selector",
      "outbounds": ["vless-reality"]
    },
    {
      "type": "vless",
      "tag": "vless-reality",
      "server": "203.0.113.10",
      "server_port": 443,
      "uuid": "11111111-1111-4111-8111-111111111111",
      "flow": "xtls-rprx-vision",
      "packet_encoding": "xudp",
      "tls": {
        "enabled": true,
        "server_name": "github.io",
        "utls": {"enabled": true, "fingerprint": "chrome"},
        "reality": {
          "enabled": true,
          "public_key": "ppQ9FwLrLIa0AOrp1WvcyiaQ37vg2WSy_CD4bIdiTUw",
          "short_id": "6ba85179f3a2b4c5"
        }
      },
      "transport": {
        "type": "ws",
        "path": "/edge",
        "headers": {"Host": "cdn.example.com"},
        "max_early_data": 2048,
        "early_data_header_name": "Sec-WebSocket-Protocol"
      }
    },
    {
      "type": "vmess",
      "tag": "vmess-grpc",
      "server": "203.0.113.11",
      "server_port": 8443,
      "uuid": "22222222-2222-4222-8222-222222222222",
      "security": "auto",
      "alter_id": 0,
      "network": "tcp",
      "transport": {"type": "grpc", "service_name": "TunService"}
    },
    {
      "type": "trojan",
      "tag": "trojan-upgrade",
      "server": "203.0.113.12",
      "server_port": 443,
      "password": "test-password",
      "tls": {"enabled": true, "server_name": "trojan.example.com", "alpn": ["http/1.1"]},
      "transport": {
        "type": "httpupgrade",
        "host": "upgrade.example.com",
        "path": "/upgrade",
        "headers": {"X-Test": "1"}
      }
    },
    {
      "type": "shadowsocks",
      "tag": "ss-node",
      "server": "203.0.113.13",
      "server_port": 8388,
      "method": "aes-128-gcm",
      "password": "test-password"
    }
  ]
}`

func TestParseSingBoxSubscriptionConvertsSupportedOutbounds(t *testing.T) {
	proxies, recognized := parseSingBoxSubscription([]byte(singBoxFixture))
	if !recognized {
		t.Fatal("sing-box config was not recognized")
	}
	if len(proxies) != 4 {
		t.Fatalf("got %d proxies, want 4", len(proxies))
	}

	vless := proxies[0]
	if vless["name"] != "vless-reality" || vless["type"] != "vless" || vless["network"] != "ws" {
		t.Fatalf("unexpected VLESS mapping: %#v", vless)
	}
	if vless["servername"] != "github.io" || vless["client-fingerprint"] != "chrome" {
		t.Fatalf("VLESS TLS mapping = %#v", vless)
	}
	reality, ok := vless["reality-opts"].(map[string]any)
	if !ok || reality["public-key"] != "ppQ9FwLrLIa0AOrp1WvcyiaQ37vg2WSy_CD4bIdiTUw" || reality["short-id"] != "6ba85179f3a2b4c5" {
		t.Fatalf("VLESS Reality mapping = %#v", vless["reality-opts"])
	}
	ws, ok := vless["ws-opts"].(map[string]any)
	if !ok || ws["path"] != "/edge" || ws["max-early-data"] != 2048 {
		t.Fatalf("VLESS WebSocket mapping = %#v", vless["ws-opts"])
	}

	vmess := proxies[1]
	if vmess["type"] != "vmess" || vmess["network"] != "grpc" || vmess["udp"] != false {
		t.Fatalf("unexpected VMess mapping: %#v", vmess)
	}
	grpc, ok := vmess["grpc-opts"].(map[string]any)
	if !ok || grpc["grpc-service-name"] != "TunService" {
		t.Fatalf("VMess gRPC mapping = %#v", vmess["grpc-opts"])
	}

	trojan := proxies[2]
	if trojan["type"] != "trojan" || trojan["sni"] != "trojan.example.com" || trojan["network"] != "ws" {
		t.Fatalf("unexpected Trojan mapping: %#v", trojan)
	}
	trojanWS, ok := trojan["ws-opts"].(map[string]any)
	if !ok || trojanWS["v2ray-http-upgrade"] != true || trojanWS["path"] != "/upgrade" {
		t.Fatalf("Trojan HTTPUpgrade mapping = %#v", trojan["ws-opts"])
	}

	ss := proxies[3]
	if ss["type"] != "ss" || ss["cipher"] != "aes-128-gcm" || ss["udp"] != true {
		t.Fatalf("unexpected Shadowsocks mapping: %#v", ss)
	}
}

func TestPreparedSubscriptionAcceptsValidatedSingBoxOutbounds(t *testing.T) {
	proxies, err := parsePreparedSubscriptionProxies([]byte(singBoxFixture))
	if err != nil {
		t.Fatalf("valid sing-box subscription was rejected: %v", err)
	}
	if len(proxies) != 4 {
		t.Fatalf("got %d validated proxies, want 4", len(proxies))
	}
	wantTypes := []string{"vless", "vmess", "trojan", "ss"}
	for index, want := range wantTypes {
		if got := proxies[index]["type"]; got != want {
			t.Fatalf("proxy[%d] type = %v, want %s", index, got, want)
		}
	}
}

func TestSingBoxSubscriptionSkipsOutboundsWithUnsupportedSemantics(t *testing.T) {
	const body = `{
      "outbounds": [
        {
          "type": "vless",
          "tag": "dialer-chain",
          "server": "203.0.113.20",
          "server_port": 443,
          "uuid": "33333333-3333-4333-8333-333333333333",
          "detour": "upstream"
        },
        {
          "type": "vmess",
          "tag": "udp-only",
          "server": "203.0.113.21",
          "server_port": 443,
          "uuid": "44444444-4444-4444-8444-444444444444",
          "network": "udp"
        },
        {
          "type": "vless",
          "tag": "unsupported-quic",
          "server": "203.0.113.22",
          "server_port": 443,
          "uuid": "55555555-5555-4555-8555-555555555555",
          "transport": {"type": "quic"}
        },
        {
          "type": "trojan",
          "tag": "plaintext-trojan",
          "server": "203.0.113.23",
          "server_port": 443,
          "password": "test-password"
        },
        {
          "type": "shadowsocks",
          "tag": "plugin-ss",
          "server": "203.0.113.24",
          "server_port": 8388,
          "method": "aes-128-gcm",
          "password": "test-password",
          "plugin": "v2ray-plugin",
          "plugin_opts": "tls;host=example.com"
        }
      ]
    }`

	proxies, recognized := parseSingBoxSubscription([]byte(body))
	if !recognized {
		t.Fatal("sing-box config was not recognized")
	}
	if len(proxies) != 0 {
		t.Fatalf("unsafe sing-box semantics were retained: %#v", proxies)
	}
}

func TestRecognizedSingBoxDoesNotFallBackToShareLinkConverter(t *testing.T) {
	const body = `{"outbounds":[{"type":"selector","tag":"proxy","outbounds":["a"]}]}`
	if _, err := parsePreparedSubscriptionProxies([]byte(body)); err == nil {
		t.Fatal("sing-box config without supported remote outbounds was accepted")
	}
}

func TestNonSingBoxJSONIsNotRecognized(t *testing.T) {
	if proxies, recognized := parseSingBoxSubscription([]byte(`{"nodes":[]}`)); recognized || proxies != nil {
		t.Fatalf("unrelated JSON recognized as sing-box: recognized=%v proxies=%#v", recognized, proxies)
	}
}
