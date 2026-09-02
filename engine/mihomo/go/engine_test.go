package engine

import (
	"errors"
	"strings"
	"testing"

	"github.com/metacubex/mihomo/config"
)

func TestReadyIsFalseBeforeStart(t *testing.T) {
	if Ready() {
		t.Fatal("engine must not report readiness before Start")
	}
}

func TestSubscriptionProviderRuntimeApiIsSafeBeforeStart(t *testing.T) {
	if got := SubscriptionProviderState(); got != "" {
		t.Fatalf("inactive provider state must be empty, got %q", got)
	}
	if err := RefreshSubscriptionProvider(); err == nil {
		t.Fatal("refresh must fail while engine is not ready")
	}
}

func TestSubscriptionSelectionIgnoresInternalEmptyFallback(t *testing.T) {
	tests := []struct {
		name     string
		live     string
		fallback string
		cached   string
		want     string
	}{
		{name: "live provider node wins", live: "Germany - 3", fallback: "COMPATIBLE", cached: "Russia - 1", want: "Germany - 3"},
		{name: "empty fallback uses cached choice", live: "COMPATIBLE", fallback: "COMPATIBLE", cached: "Germany - 3", want: "Germany - 3"},
		{name: "blank live uses cached choice", live: "", fallback: "COMPATIBLE", cached: "Germany - 3", want: "Germany - 3"},
		{name: "fallback without cache stays blank", live: "COMPATIBLE", fallback: "COMPATIBLE", cached: "", want: ""},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := resolveSubscriptionSelection(tt.live, tt.fallback, tt.cached); got != tt.want {
				t.Fatalf("resolveSubscriptionSelection() = %q, want %q", got, tt.want)
			}
		})
	}
}

func TestSensitiveURLsAreRedactedFromLogsAndErrors(t *testing.T) {
	const secretURL = "https://subscription.example/opaque-token?user=secret"
	message := `Get "` + secretURL + `": dial tcp: network unreachable`
	redacted := redactSensitiveURLs(message)
	if strings.Contains(redacted, secretURL) || strings.Contains(redacted, "opaque-token") {
		t.Fatalf("secret URL leaked after redaction: %s", redacted)
	}
	if !strings.Contains(redacted, "[redacted-url]") {
		t.Fatalf("redaction marker missing: %s", redacted)
	}
	redactedErr := redactError(errors.New(message))
	if redactedErr == nil || strings.Contains(redactedErr.Error(), "opaque-token") {
		t.Fatalf("secret URL leaked through error: %v", redactedErr)
	}
}

func TestMihomoAcceptsSubscriptionProviderConfig(t *testing.T) {
	const yaml = `
mode: rule
ipv6: false
proxies:
- name: DPI
  type: socks5
  server: 127.0.0.1
  port: 10808
proxy-providers:
  DETOUR_SUBSCRIPTION:
    type: http
    url: https://subscription.example/opaque-token
    interval: 3600
    size-limit: 4194304
    health-check:
      enable: true
      url: https://www.gstatic.com/generate_204
      interval: 300
      timeout: 5000
      lazy: false
      expected-status: 204
proxy-groups:
- name: SUBSCRIPTION
  type: select
  use:
    - DETOUR_SUBSCRIPTION
rules:
- MATCH,SUBSCRIPTION
`
	if _, err := config.Parse([]byte(yaml)); err != nil {
		t.Fatalf("mihomo rejected subscription provider schema: %v", err)
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
  persistent-keepalive: 25
  udp: true
  mtu: 1280
  remote-dns-resolve: false
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
  type: fallback
  url: https://cp.cloudflare.com/generate_204
  interval: 300
  lazy: false
  timeout: 3000
  max-failed-times: 2
  expected-status: 204
  proxies:
    - WARP_0
rules:
- MATCH,WARP
`
	if _, err := config.Parse([]byte(yaml)); err != nil {
		t.Fatalf("mihomo rejected WARP/AmneziaWG schema: %v", err)
	}
}

func TestMihomoAcceptsNativeAmneziaConfigWithoutReserved(t *testing.T) {
	const yaml = `
mode: rule
ipv6: false
proxies:
- name: WARP_0
  type: wireguard
  server: 162.159.195.1
  port: 500
  ip: 172.16.0.2
  ipv6: "2606:4700:110::2"
  private-key: "TJV14OLWSD/3QxSOiUEEyf/tJLfg38/eYoqsiRY65rc="
  public-key: "QH+99BoTQpVd+x+SpMwAQGm47sGJ7JqTap2hEXXxfMI="
  allowed-ips: ["0.0.0.0/0", "::/0"]
  udp: true
  mtu: 1280
  remote-dns-resolve: false
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
rules:
- MATCH,WARP_0
`
	if _, err := config.Parse([]byte(yaml)); err != nil {
		t.Fatalf("mihomo rejected native-style AWG schema without reserved: %v", err)
	}
}
