package engine

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"net"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"github.com/metacubex/mihomo/adapter"
	"github.com/metacubex/mihomo/common/convert"
	mihomoYaml "github.com/metacubex/mihomo/common/yaml"
	C "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/log"
	"github.com/metacubex/mihomo/tunnel"
)

const (
	subscriptionProviderFileName = "detour-subscription.yaml"
	// Match NekoBox's default connectivity-test target. HTTP intentionally avoids
	// measuring a second, destination-side TLS handshake on top of VLESS/Reality.
	subscriptionLatencyTestURL = "http://cp.cloudflare.com/"
	subscriptionLatencyTimeout = 5 * time.Second
	// Keep mobile-radio pressure modest. This is only a parallelism cap; every
	// prepared node is still tested before the method returns.
	subscriptionLatencyParallel = 5
)

type preparedProxySchema struct {
	Proxies []map[string]any `yaml:"proxies"`
}

type subscriptionLatencyNode struct {
	Name       string `json:"name"`
	DelayMs    int    `json:"delayMs,omitempty"`
	ErrorClass string `json:"errorClass,omitempty"`
	ErrorText  string `json:"errorText,omitempty"`
}

type subscriptionLatencyProbe func(context.Context, map[string]any) (int, error)
type subscriptionHostResolver func(context.Context, string) ([]string, error)

type subscriptionLatencyTarget struct {
	Name  string
	Probe func(context.Context) (int, error)
}

// PrepareSubscriptionProvider downloads an HTTPS V2Ray subscription, converts
// URI/base64 bodies to mihomo YAML and stores only VLESS nodes that Mihomo can
// actually parse. The returned absolute path is safe to feed to a file provider.
// Empty string means the subscription could not be prepared.
func PrepareSubscriptionProvider(subscriptionURL string, homeDir string) string {
	proxies, err := fetchPreparedSubscriptionProxies(subscriptionURL)
	if err != nil || len(proxies) == 0 || homeDir == "" {
		return ""
	}
	if err := os.MkdirAll(homeDir, 0o700); err != nil {
		return ""
	}

	payload, err := mihomoYaml.Marshal(preparedProxySchema{Proxies: proxies})
	if err != nil || len(payload) == 0 {
		return ""
	}
	finalPath := filepath.Join(homeDir, subscriptionProviderFileName)
	tempPath := finalPath + ".tmp"
	if err := os.WriteFile(tempPath, payload, 0o600); err != nil {
		return ""
	}
	if err := os.Rename(tempPath, finalPath); err != nil {
		_ = os.Remove(tempPath)
		return ""
	}
	return finalPath
}

// FetchPreparedSubscriptionCatalog returns the same validated VLESS set that
// PrepareSubscriptionProvider writes for runtime. Keeping catalog and provider
// on one normalization path prevents the UI from offering a node that would
// later make Mihomo reject the complete file provider.
func FetchPreparedSubscriptionCatalog(subscriptionURL string) string {
	proxies, err := fetchPreparedSubscriptionProxies(subscriptionURL)
	if err != nil || len(proxies) == 0 {
		return ""
	}
	type catalogNode struct {
		Name string `json:"name"`
		Type string `json:"type"`
	}
	nodes := make([]catalogNode, 0, len(proxies))
	for _, proxy := range proxies {
		name, nameOK := safeCatalogLabel(proxy["name"], 256)
		proxyType, typeOK := safeCatalogLabel(proxy["type"], 64)
		if !nameOK || !typeOK {
			continue
		}
		nodes = append(nodes, catalogNode{Name: name, Type: proxyType})
	}
	if len(nodes) == 0 {
		return ""
	}
	payload, err := json.Marshal(map[string]any{"nodes": nodes})
	if err != nil {
		return ""
	}
	return string(payload)
}

// HealthCheckSubscriptionProvider runs one explicit URL test for every node in
// the currently active provider. It is retained for runtime diagnostics; the UI
// uses TestSubscriptionCatalogLatency for the user-triggered latency action.
func HealthCheckSubscriptionProvider() error {
	if !Ready() {
		return errors.New("engine is not ready")
	}
	provider, ok := tunnel.Providers()[subscriptionProviderName]
	if !ok {
		return errors.New("subscription provider is not active")
	}
	provider.HealthCheck()
	return nil
}

// TestSubscriptionCatalogLatency prefers the live provider when the VPN engine
// is running. That path tests the exact proxy objects used for traffic and uses
// the resolver configured by the active Mihomo config. When disconnected it
// falls back to independently parsed proxies and resolves endpoint hostnames via
// Android's system resolver before handing them to Mihomo's URLTest.
func TestSubscriptionCatalogLatency(subscriptionURL string) string {
	if results, ok := activeSubscriptionLatencyResults(); ok {
		return marshalSubscriptionLatencyResults(results)
	}

	proxies, err := fetchPreparedSubscriptionProxies(subscriptionURL)
	if err != nil || len(proxies) == 0 {
		return ""
	}
	return marshalSubscriptionLatencyResults(runSubscriptionLatencyTests(proxies, mihomoSubscriptionLatencyProbe))
}

func marshalSubscriptionLatencyResults(results []subscriptionLatencyNode) string {
	payload, err := json.Marshal(map[string]any{"nodes": results})
	if err != nil {
		return ""
	}
	return string(payload)
}

func activeSubscriptionLatencyResults() ([]subscriptionLatencyNode, bool) {
	if !Ready() {
		return nil, false
	}
	provider, ok := tunnel.Providers()[subscriptionProviderName]
	if !ok {
		return nil, false
	}
	proxies := provider.Proxies()
	if len(proxies) == 0 {
		return nil, false
	}

	targets := make([]subscriptionLatencyTarget, 0, len(proxies))
	for _, proxy := range proxies {
		proxy := proxy
		name, ok := safeCatalogLabel(proxy.Name(), 256)
		if !ok {
			continue
		}
		targets = append(targets, subscriptionLatencyTarget{
			Name: name,
			Probe: func(ctx context.Context) (int, error) {
				delay, err := proxy.URLTest(ctx, subscriptionLatencyTestURL, nil)
				return int(delay), err
			},
		})
	}
	if len(targets) == 0 {
		return nil, false
	}
	return runSubscriptionLatencyTargets(targets), true
}

func runSubscriptionLatencyTests(proxies []map[string]any, probe subscriptionLatencyProbe) []subscriptionLatencyNode {
	targets := make([]subscriptionLatencyTarget, 0, len(proxies))
	for _, mapping := range proxies {
		mapping := mapping
		name, ok := safeCatalogLabel(mapping["name"], 256)
		if !ok {
			continue
		}
		targets = append(targets, subscriptionLatencyTarget{
			Name: name,
			Probe: func(ctx context.Context) (int, error) {
				return probe(ctx, mapping)
			},
		})
	}
	return runSubscriptionLatencyTargets(targets)
}

func runSubscriptionLatencyTargets(targets []subscriptionLatencyTarget) []subscriptionLatencyNode {
	results := make([]subscriptionLatencyNode, len(targets))
	semaphore := make(chan struct{}, subscriptionLatencyParallel)
	var wg sync.WaitGroup

	for index, target := range targets {
		results[index].Name = target.Name
		wg.Add(1)
		go func(i int, probe func(context.Context) (int, error)) {
			defer wg.Done()
			semaphore <- struct{}{}
			defer func() { <-semaphore }()

			ctx, cancel := context.WithTimeout(context.Background(), subscriptionLatencyTimeout)
			defer cancel()
			delay, testErr := probe(ctx)
			if testErr == nil && delay > 0 {
				results[i].DelayMs = delay
				return
			}

			if testErr != nil {
				results[i].ErrorClass = adapter.DetourURLTestErrorClass(testErr)
				results[i].ErrorText = adapter.DetourURLTestErrorText(testErr)
			} else {
				results[i].ErrorClass = "other"
				results[i].ErrorText = "URLTest returned non-positive delay"
			}
			log.Errorln(
				"[DETOUR_SUBSCRIPTION_TEST] node=%q class=%s error=%s",
				results[i].Name,
				results[i].ErrorClass,
				results[i].ErrorText,
			)
		}(index, target.Probe)
	}
	wg.Wait()
	return results
}

func mihomoSubscriptionLatencyProbe(ctx context.Context, proxyMapping map[string]any) (int, error) {
	preparedMapping, err := prepareOfflineLatencyProxyMapping(ctx, proxyMapping, systemSubscriptionHostResolver)
	if err != nil {
		return 0, err
	}
	proxy, err := adapter.ParseProxy(preparedMapping)
	if err != nil {
		return 0, err
	}
	defer proxy.Close()

	delay, err := proxy.URLTest(ctx, subscriptionLatencyTestURL, nil)
	if err != nil {
		return 0, err
	}
	return int(delay), nil
}

func systemSubscriptionHostResolver(ctx context.Context, host string) ([]string, error) {
	ips, err := net.DefaultResolver.LookupIP(ctx, "ip4", host)
	if err != nil {
		return nil, err
	}
	resolved := make([]string, 0, len(ips))
	for _, ip := range ips {
		if ip4 := ip.To4(); ip4 != nil {
			resolved = append(resolved, ip4.String())
		}
	}
	if len(resolved) == 0 {
		return nil, errors.New("subscription endpoint has no IPv4 address")
	}
	return resolved, nil
}

func prepareOfflineLatencyProxyMapping(
	ctx context.Context,
	mapping map[string]any,
	resolve subscriptionHostResolver,
) (map[string]any, error) {
	server, ok := safeCatalogLabel(mapping["server"], 253)
	if !ok || net.ParseIP(server) != nil {
		return mapping, nil
	}
	addresses, err := resolve(ctx, server)
	if err != nil || len(addresses) == 0 {
		if err != nil {
			return nil, err
		}
		return nil, errors.New("subscription endpoint DNS returned no addresses")
	}
	ip := net.ParseIP(addresses[0])
	if ip == nil || ip.To4() == nil {
		return nil, errors.New("subscription endpoint DNS returned invalid IPv4 address")
	}

	prepared := cloneProxyMapping(mapping)
	if tls, _ := prepared["tls"].(bool); tls {
		if serverName, _ := prepared["servername"].(string); strings.TrimSpace(serverName) == "" {
			prepared["servername"] = inferredTLSName(prepared, server)
		}
	}
	prepared["server"] = ip.To4().String()
	return prepared, nil
}

func cloneProxyMapping(mapping map[string]any) map[string]any {
	cloned := make(map[string]any, len(mapping))
	for key, value := range mapping {
		cloned[key] = value
	}
	return cloned
}

func inferredTLSName(mapping map[string]any, fallback string) string {
	network, _ := mapping["network"].(string)
	if strings.EqualFold(strings.TrimSpace(network), "ws") {
		if wsOpts, ok := mapping["ws-opts"].(map[string]any); ok {
			if headers, ok := wsOpts["headers"].(map[string]any); ok {
				if host, ok := headers["Host"].(string); ok && strings.TrimSpace(host) != "" {
					return strings.TrimSpace(host)
				}
			}
		}
	}
	return fallback
}

func fetchPreparedSubscriptionProxies(subscriptionURL string) ([]map[string]any, error) {
	parsed, err := parseSubscriptionURL(subscriptionURL)
	if err != nil {
		return nil, err
	}
	client := &http.Client{
		Timeout: 15 * time.Second,
		CheckRedirect: func(req *http.Request, via []*http.Request) error {
			if len(via) >= 5 {
				return errors.New("too many subscription redirects")
			}
			if req.URL.Scheme != "https" || req.URL.Host == "" || req.URL.User != nil {
				return errors.New("unsafe subscription redirect")
			}
			return nil
		},
	}
	req, err := http.NewRequest(http.MethodGet, parsed.String(), nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("User-Agent", subscriptionUserAgent)
	resp, err := client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return nil, errors.New("subscription request failed")
	}
	body, err := io.ReadAll(io.LimitReader(resp.Body, maxSubscriptionBodyBytes+1))
	if err != nil || len(body) == 0 || len(body) > maxSubscriptionBodyBytes {
		return nil, errors.New("invalid subscription body")
	}
	return parsePreparedSubscriptionProxies(body)
}

// normalizeVlessSubscriptionBody closes a compatibility gap between NekoBox's
// VLESS share-link parser and Mihomo 1.19.30. NekoBox treats `host` as the TLS
// server name when `sni` is absent. Mihomo's converter discards `host` for TCP,
// so Reality/TLS handshakes can be sent with the endpoint IP/hostname instead of
// the intended camouflage name and be closed immediately by the server.
// Normalize the URI before conversion, while the original query still exists.
func normalizeVlessSubscriptionBody(body []byte) []byte {
	data := convert.DecodeBase64(body)
	lines := strings.Split(string(data), "\n")
	for index, rawLine := range lines {
		line := strings.TrimRight(rawLine, " \r")
		if !strings.HasPrefix(strings.ToLower(line), "vless://") {
			continue
		}
		parsed, err := url.Parse(line)
		if err != nil {
			continue
		}
		query := parsed.Query()
		changed := false
		security := strings.ToLower(strings.TrimSpace(query.Get("security")))
		if (security == "reality" || strings.HasSuffix(security, "tls")) &&
			strings.TrimSpace(query.Get("sni")) == "" {
			if host := strings.TrimSpace(query.Get("host")); host != "" {
				query.Set("sni", host)
				changed = true
			}
		}
		if fingerprint := query.Get("fp"); strings.EqualFold(fingerprint, "ios") && fingerprint != "iOS" {
			query.Set("fp", "iOS")
			changed = true
		}
		if changed {
			parsed.RawQuery = query.Encode()
			lines[index] = parsed.String()
		}
	}
	return []byte(strings.Join(lines, "\n"))
}

// parsePreparedSubscriptionProxies is intentionally separate from network I/O.
// URI/base64 subscription bodies may be accepted by the YAML decoder without a
// proxies collection, so they still fall back to Mihomo's V2Ray converter.
// Every retained mapping is then parsed once with Mihomo itself. Its file
// provider parser is all-or-nothing, so dropping an invalid mapping here keeps
// one incompatible node from disabling every otherwise valid server.
func parsePreparedSubscriptionProxies(body []byte) ([]map[string]any, error) {
	schema := &preparedProxySchema{}
	yamlErr := mihomoYaml.Unmarshal(body, schema)
	if yamlErr != nil || len(schema.Proxies) == 0 {
		proxies, convertErr := convert.ConvertsV2Ray(normalizeVlessSubscriptionBody(body))
		if convertErr != nil || len(proxies) == 0 {
			return nil, errors.New("unsupported subscription format")
		}
		schema.Proxies = proxies
	}

	prepared := make([]map[string]any, 0, min(len(schema.Proxies), maxSubscriptionNodes))
	seen := make(map[string]struct{}, len(schema.Proxies))
	for _, proxy := range schema.Proxies {
		proxyType, _ := proxy["type"].(string)
		if !strings.EqualFold(strings.TrimSpace(proxyType), "vless") {
			continue
		}
		name, ok := safeCatalogLabel(proxy["name"], 256)
		if !ok {
			continue
		}
		if _, duplicate := seen[name]; duplicate {
			continue
		}

		// Normalize fields used as Mihomo/provider identifiers before parsing.
		// The UI already compares type case-insensitively and trims labels, while
		// Mihomo expects the canonical outbound type and exact selector names.
		proxy["type"] = "vless"
		proxy["name"] = name
		normalizeVlessHTTPUpgrade(proxy)

		parsedProxy, parseErr := adapter.ParseProxy(proxy)
		if parseErr != nil {
			continue
		}
		_ = parsedProxy.Close()

		seen[name] = struct{}{}
		prepared = append(prepared, proxy)
		if len(prepared) >= maxSubscriptionNodes {
			break
		}
	}
	if len(prepared) == 0 {
		return nil, errors.New("subscription has no supported VLESS nodes")
	}
	return prepared, nil
}

// Mihomo 1.19.30 (and current Meta at the time of this fix) converts VLESS
// share links with type=httpupgrade into network=httpupgrade while its VLESS
// adapter implements HTTP Upgrade through the websocket transport. Without
// v2ray-http-upgrade=true these nodes parse successfully but cannot carry
// traffic or complete URL tests. Normalize both converted URI subscriptions and
// YAML subscriptions before the provider sees them.
func normalizeVlessHTTPUpgrade(proxy map[string]any) {
	network, _ := proxy["network"].(string)
	if !strings.EqualFold(strings.TrimSpace(network), "httpupgrade") {
		return
	}
	proxy["network"] = "ws"
	wsOpts, ok := proxy["ws-opts"].(map[string]any)
	if !ok || wsOpts == nil {
		wsOpts = make(map[string]any)
		proxy["ws-opts"] = wsOpts
	}
	wsOpts["v2ray-http-upgrade"] = true
}

var _ C.Proxy = (C.Proxy)(nil)
