package engine

import (
    "context"
    "encoding/json"
    "errors"
    "io"
    "net/http"
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
    subscriptionLatencyParallel  = 10
)

type preparedProxySchema struct {
    Proxies []map[string]any `yaml:"proxies"`
}

type subscriptionLatencyNode struct {
    Name    string `json:"name"`
    DelayMs int    `json:"delayMs,omitempty"`
}

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

            proxy, parseErr := adapter.ParseProxy(proxyMapping)
            if parseErr != nil {
                return
            }
            defer proxy.Close()

            ctx, cancel := context.WithTimeout(context.Background(), subscriptionLatencyTimeout)
            defer cancel()
            delay, testErr := proxy.URLTest(ctx, subscriptionLatencyTestURL, nil)
            if testErr == nil && delay > 0 {
                results[i].DelayMs = int(delay)
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
    payload, err := json.Marshal(map[string]any{"nodes": compact})
    if err != nil {
        return ""
    }
    return string(payload)
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

    schema := &preparedProxySchema{}
    yamlErr := mihomoYaml.Unmarshal(body, schema)
    if yamlErr != nil || len(schema.Proxies) == 0 {
        proxies, convertErr := convert.ConvertsV2Ray(body)
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
