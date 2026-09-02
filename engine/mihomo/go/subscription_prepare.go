package engine

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	neturl "net/url"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"github.com/metacubex/mihomo/adapter"
	"github.com/metacubex/mihomo/common/convert"
	mihomoYaml "github.com/metacubex/mihomo/common/yaml"
	"github.com/metacubex/mihomo/tunnel"
)

const (
	subscriptionProviderFileName = "detour-subscription.yaml"
	subscriptionLatencyTestURL   = "https://www.gstatic.com/generate_204"
	subscriptionLatencyTimeout   = 5 * time.Second
	// Mihomo's own provider HealthCheck uses the same concurrency limit. This is
	// only a parallelism cap; every prepared node must still be tested.
	subscriptionLatencyParallel = 10
)

type preparedProxySchema struct {
	Proxies []map[string]any `yaml:"proxies"`
}

type subscriptionLatencyNode struct {
	Name    string `json:"name"`
	DelayMs int    `json:"delayMs,omitempty"`
}

type subscriptionLatencyProbe func(context.Context, map[string]any) (int, error)

// PrepareSubscriptionProvider downloads an HTTPS V2Ray subscription, converts
// URI/base64 bodies to mihomo YAML and stores only VLESS nodes in app-private
// cache. The returned absolute path is safe to feed to a file proxy-provider.
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

// HealthCheckSubscriptionProvider runs one explicit URL test for every node in
// the currently active provider. It is retained for runtime diagnostics; the UI
// uses TestSubscriptionCatalogLatency so latency can also be measured offline.
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

// TestSubscriptionCatalogLatency downloads and normalizes the subscription,
// builds each VLESS adapter independently and runs Mihomo URLTest directly.
// No TUN/runtime is created and the selected subscription node is not changed,
// so this works before connecting just like a server-list latency test.
// The returned JSON contains all safe node names; delayMs is omitted on failure.
func TestSubscriptionCatalogLatency(subscriptionURL string) string {
	proxies, err := fetchPreparedSubscriptionProxies(subscriptionURL)
	if err != nil || len(proxies) == 0 {
		return ""
	}

	results := runSubscriptionLatencyTests(proxies, mihomoSubscriptionLatencyProbe)
	payload, err := json.Marshal(map[string]any{"nodes": results})
	if err != nil {
		return ""
	}
	return string(payload)
}

func runSubscriptionLatencyTests(proxies []map[string]any, probe subscriptionLatencyProbe) []subscriptionLatencyNode {
	results := make([]subscriptionLatencyNode, len(proxies))
	semaphore := make(chan struct{}, subscriptionLatencyParallel)
	var wg sync.WaitGroup

	for index, mapping := range proxies {
		name, ok := safeCatalogLabel(mapping["name"], 256)
		if !ok {
			continue
		}
		results[index].Name = name
		wg.Add(1)
		go func(i int, proxyMapping map[string]any) {
			defer wg.Done()
			semaphore <- struct{}{}
			defer func() { <-semaphore }()

			ctx, cancel := context.WithTimeout(context.Background(), subscriptionLatencyTimeout)
			defer cancel()
			delay, testErr := probe(ctx, proxyMapping)
			if testErr == nil && delay > 0 {
				results[i].DelayMs = delay
			}
		}(index, mapping)
	}
	wg.Wait()

	compact := results[:0]
	for _, result := range results {
		if result.Name != "" {
			compact = append(compact, result)
		}
	}
	return compact
}

func mihomoSubscriptionLatencyProbe(ctx context.Context, proxyMapping map[string]any) (int, error) {
	proxy, err := adapter.ParseProxy(proxyMapping)
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

// parsePreparedSubscriptionProxies is intentionally separate from network I/O.
// In particular, a URI/base64 subscription may be accepted by the YAML decoder
// without yielding a proxies collection; that must still fall back to V2Ray
// conversion instead of producing the provider error that originally broke
// otherwise valid subscriptions in Detour.
func parsePreparedSubscriptionProxies(body []byte) ([]map[string]any, error) {
	schema := &preparedProxySchema{}
	yamlErr := mihomoYaml.Unmarshal(body, schema)
	if yamlErr != nil || len(schema.Proxies) == 0 {
		proxies, convertErr := convert.ConvertsV2Ray(body)
		if convertErr != nil || len(proxies) == 0 {
			return nil, errors.New("unsupported subscription format")
		}
		// Mihomo v1.19.30's generic V2Ray share-link converter does not copy the
		// VLESS `flow` query parameter even though the VLESS outbound supports it.
		// Restore only the field present in the original link so Reality/Vision
		// subscriptions are equivalent to the same standalone VLESS profile.
		restoreVlessShareLinkFlow(body, proxies)
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
		seen[name] = struct{}{}
		prepared = append(prepared, proxy)
		if len(prepared) >= maxSubscriptionNodes {
			break
		}
	}
	if len(prepared) == 0 {
		return nil, errors.New("subscription has no VLESS nodes")
	}
	return prepared, nil
}

func restoreVlessShareLinkFlow(body []byte, proxies []map[string]any) {
	decoded := string(convert.DecodeBase64(body))
	flows := make(map[string]string)
	for _, line := range strings.FieldsFunc(decoded, func(r rune) bool { return r == '\n' || r == '\r' }) {
		line = strings.TrimSpace(line)
		if len(line) < len("vless://") || !strings.EqualFold(line[:len("vless://")], "vless://") {
			continue
		}
		parsed, err := neturl.Parse(line)
		if err != nil || parsed.User == nil {
			continue
		}
		flow := strings.TrimSpace(parsed.Query().Get("flow"))
		if flow == "" {
			continue
		}
		key := vlessEndpointKey(parsed.User.Username(), parsed.Hostname(), parsed.Port())
		if key != "" {
			flows[key] = flow
		}
	}
	if len(flows) == 0 {
		return
	}

	for _, proxy := range proxies {
		proxyType, _ := proxy["type"].(string)
		if !strings.EqualFold(strings.TrimSpace(proxyType), "vless") {
			continue
		}
		if existing, _ := proxy["flow"].(string); strings.TrimSpace(existing) != "" {
			continue
		}
		uuid, _ := proxy["uuid"].(string)
		server, _ := proxy["server"].(string)
		port := strings.TrimSpace(fmt.Sprint(proxy["port"]))
		if flow := flows[vlessEndpointKey(uuid, server, port)]; flow != "" {
			proxy["flow"] = flow
		}
	}
}

func vlessEndpointKey(uuid string, server string, port string) string {
	uuid = strings.ToLower(strings.TrimSpace(uuid))
	server = strings.ToLower(strings.TrimSpace(server))
	port = strings.TrimSpace(port)
	if uuid == "" || server == "" || port == "" {
		return ""
	}
	return uuid + "\x00" + server + "\x00" + port
}
