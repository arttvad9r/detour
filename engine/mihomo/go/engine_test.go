package engine

import (
	"errors"
	"net/url"
	"strings"
	"sync"
	"testing"

	"github.com/metacubex/mihomo/common/utils"
	"github.com/metacubex/mihomo/component/profile"
	"github.com/metacubex/mihomo/config"
	C "github.com/metacubex/mihomo/constant"
	P "github.com/metacubex/mihomo/constant/provider"
	"github.com/metacubex/mihomo/tunnel"
)

func TestSameSubscriptionOrigin(t *testing.T) {
	for _, tc := range []struct {
		name string
		a    string
		b    string
		want bool
	}{
		{"same default port", "https://a.example/token", "https://a.example/next", true},
		{"explicit default port", "https://a.example/token", "https://a.example:443/next", true},
		{"different host", "https://a.example/token", "https://b.example/next", false},
		{"different scheme", "https://a.example/token", "http://a.example/next", false},
	} {
		t.Run(tc.name, func(t *testing.T) {
			a, err := url.Parse(tc.a)
			if err != nil {
				t.Fatal(err)
			}
			b, err := url.Parse(tc.b)
			if err != nil {
				t.Fatal(err)
			}
			if got := sameSubscriptionOrigin(a, b); got != tc.want {
				t.Fatalf("sameSubscriptionOrigin() = %v, want %v", got, tc.want)
			}
		})
	}
}

func TestReadyIsFalseBeforeStart(t *testing.T) {
	if Ready() {
		t.Fatal("engine must not report readiness before Start")
	}
}

func TestStartDoesNotReportReadyWhenTunCreationFails(t *testing.T) {
	Stop()
	oldHomeDir := C.Path.HomeDir()
	t.Cleanup(func() {
		Stop()
		C.SetHomeDir(oldHomeDir)
	})
	config := `
mode: rule
log-level: silent
proxies: []
tun:
  enable: true
  file-descriptor: 2147483647
rules:
  - MATCH,DIRECT
`

	if err := Start(config, t.TempDir()+"/engine.log"); err == nil {
		t.Fatal("Start must report failed TUN creation")
	}
	if Ready() {
		t.Fatal("failed TUN creation must not publish readiness")
	}
}

func TestFailedReplacementLeavesRuntimeStopped(t *testing.T) {
	Stop()
	valid := "mode: rule\nlog-level: silent\nproxies: []\nrules:\n  - MATCH,DIRECT\n"
	if err := Start(valid, ""); err != nil {
		t.Fatalf("start valid runtime: %v", err)
	}
	failed := "mode: rule\nlog-level: silent\nproxies: []\ntun:\n  enable: true\n  file-descriptor: 2147483647\nrules:\n  - MATCH,DIRECT\n"
	if err := Start(failed, ""); err == nil {
		t.Fatal("failed replacement must return an error")
	}
	if Ready() {
		t.Fatal("failed replacement must not leave a partially active runtime")
	}
}

func TestConcurrentStartAndStopLeaveEngineStopped(t *testing.T) {
	Stop()
	config := "mode: rule\nlog-level: silent\nproxies: []\nrules:\n  - MATCH,DIRECT\n"
	startDone := make(chan error, 1)
	startFinished := make(chan struct{})
	stopFinished := make(chan struct{})
	acquired := make(chan string, 3)
	releaseStart := make(chan struct{})
	var releaseOnce sync.Once
	release := func() { releaseOnce.Do(func() { close(releaseStart) }) }
	runtimeMuAcquiredHook = func(operation string) {
		acquired <- operation
		if operation == "Start" {
			<-releaseStart
		}
	}
	t.Cleanup(func() {
		release()
		<-startFinished
		<-stopFinished
		runtimeMuAcquiredHook = nil
	})
	go func() {
		defer close(startFinished)
		startDone <- Start(config, "")
	}()
	if operation := <-acquired; operation != "Start" {
		t.Fatalf("first runtime mutex owner = %q, want Start", operation)
	}
	go func() {
		defer close(stopFinished)
		Stop()
	}()
	release()
	if operation := <-acquired; operation != "Stop" {
		t.Fatalf("second runtime mutex owner = %q, want Stop", operation)
	}
	if err := <-startDone; err != nil {
		t.Fatalf("concurrent Start failed: %v", err)
	}
	Stop()
	if operation := <-acquired; operation != "Stop" {
		t.Fatalf("third runtime mutex owner = %q, want Stop", operation)
	}
	if Ready() {
		t.Fatal("engine must be stopped after concurrent Start and Stop")
	}
}

func TestSubscriptionSelectionIsScopedToProfile(t *testing.T) {
	Stop()
	oldStoreSelected := profile.StoreSelected.Load()
	oldHomeDir := C.Path.HomeDir()
	t.Cleanup(func() {
		Stop()
		profile.StoreSelected.Store(oldStoreSelected)
		C.SetHomeDir(oldHomeDir)
	})
	profile.StoreSelected.Store(true)
	profileA := t.TempDir()
	profileB := t.TempDir()
	if err := SelectSubscriptionNode("node-a", profileA); err != nil {
		t.Fatalf("select profile A node: %v", err)
	}
	if err := SelectSubscriptionNode("node-b", profileB); err != nil {
		t.Fatalf("select profile B node: %v", err)
	}
	if got := SubscriptionSelectedNode(profileA); got != "node-a" {
		t.Fatalf("profile A selection = %q, want node-a", got)
	}
	if got := SubscriptionSelectedNode(profileB); got != "node-b" {
		t.Fatalf("profile B selection = %q, want node-b", got)
	}
}

func TestSubscriptionSelectionUsesLegacyKeyAsMigrationFallback(t *testing.T) {
	profile := "/profiles/new"
	selected := map[string]string{subscriptionGroupName: "legacy-node"}
	if got := resolveSubscriptionCache(selected, profile); got != "legacy-node" {
		t.Fatalf("legacy selection = %q, want legacy-node", got)
	}
}

type testProviderCloser struct{ closed bool }

func (p *testProviderCloser) Close() error {
	p.closed = true
	return nil
}

func TestProviderCleanupCallsPinnedCloseHook(t *testing.T) {
	provider := &testProviderCloser{}
	closeProvider(provider)
	if !provider.closed {
		t.Fatal("provider cleanup must call the pinned Close hook")
	}
}

type traversedProvider struct{ closed bool }

func (p *traversedProvider) Name() string                                                          { return "test" }
func (p *traversedProvider) VehicleType() P.VehicleType                                            { return P.Compatible }
func (p *traversedProvider) Type() P.ProviderType                                                  { return P.Proxy }
func (p *traversedProvider) Initial() error                                                        { return nil }
func (p *traversedProvider) Update() error                                                         { return nil }
func (p *traversedProvider) Proxies() []C.Proxy                                                    { return nil }
func (p *traversedProvider) Count() int                                                            { return 0 }
func (p *traversedProvider) Touch()                                                                {}
func (p *traversedProvider) HealthCheck()                                                          {}
func (p *traversedProvider) Version() uint32                                                       { return 0 }
func (p *traversedProvider) RegisterHealthCheckTask(string, utils.IntRanges[uint16], string, uint) {}
func (p *traversedProvider) HealthCheckURL() string                                                { return "" }
func (p *traversedProvider) Close() error                                                          { p.closed = true; return nil }

func TestStopTraversesAndClosesProviders(t *testing.T) {
	provider := &traversedProvider{}
	oldProviders := tunnel.Providers()
	tunnel.UpdateProxies(nil, map[string]P.ProxyProvider{"test": provider})
	t.Cleanup(func() { tunnel.UpdateProxies(nil, oldProviders) })
	Stop()
	if !provider.closed {
		t.Fatal("Stop must close providers from the active runtime")
	}
}

func TestSubscriptionSelectionKeyHasStableProfileIdentity(t *testing.T) {
	if got := subscriptionSelectionKey("/profiles/one"); got == subscriptionSelectionKey("/profiles/two") {
		t.Fatal("different profiles must not share a selection key")
	}
	if got := subscriptionSelectionKey(""); got != subscriptionGroupName {
		t.Fatalf("empty profile key = %q, want %q", got, subscriptionGroupName)
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
