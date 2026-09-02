// Package engine exposes a minimal Android embedding for mihomo.
//
// The TUN file descriptor is supplied by the host through the generated
// configuration (tun.file-descriptor). The host excludes its own UID from
// the VpnService, so engine sockets never re-enter the TUN.
package engine

import (
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/netip"
	"net/url"
	"os"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/metacubex/mihomo/adapter/outboundgroup"
	"github.com/metacubex/mihomo/common/convert"
	mihomoYaml "github.com/metacubex/mihomo/common/yaml"
	"github.com/metacubex/mihomo/common/observable"
	"github.com/metacubex/mihomo/component/process"
	"github.com/metacubex/mihomo/component/profile/cachefile"
	"github.com/metacubex/mihomo/config"
	C "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/hub/executor"
	"github.com/metacubex/mihomo/listener"
	LC "github.com/metacubex/mihomo/listener/config"
	"github.com/metacubex/mihomo/log"
	"github.com/metacubex/mihomo/tunnel"
)

const (
	subscriptionProviderName = "DETOUR_SUBSCRIPTION"
	subscriptionGroupName    = "SUBSCRIPTION"
	subscriptionUserAgent    = "mihomo/1.19.30"
	maxSubscriptionBodyBytes = 4 * 1024 * 1024
	maxSubscriptionNodes     = 256
)

var sensitiveURLPattern = regexp.MustCompile(`https?://[^\t\r\n "'<>]+`)

// ProcessResolver resolves the owning app of a TUN connection host-side.
// Return "" if unknown. Non-empty response MUST be "<uid> <packageName>".
type ProcessResolver interface {
	Resolve(network string, srcIP string, srcPort int64, dstIP string, dstPort int64) string
}

var hostResolver ProcessResolver
var readyMu sync.RWMutex
var ready bool

// SetProcessResolver registers the host-side resolver (call once from Android).
func SetProcessResolver(r ProcessResolver) { hostResolver = r }

func init() {
	process.TripletHostFinder = func(network string, srcIP netip.Addr, srcPort int, dstIP netip.Addr, dstPort int) (uint32, string, bool) {
		if hostResolver == nil {
			return 0, "", false
		}
		resp := hostResolver.Resolve(network, srcIP.String(), int64(srcPort), dstIP.String(), int64(dstPort))
		if resp == "" {
			return 0, "", false
		}
		fields := strings.Fields(resp)
		if len(fields) < 2 {
			return 0, "", false
		}
		uid, err := strconv.ParseUint(fields[0], 10, 32)
		if err != nil {
			return 0, "", false
		}
		return uint32(uid), fields[1], true
	}
}

// Start parses configYAML and applies it. Logs are mirrored to logPath when non-empty.
func Start(configYAML string, logPath string) (err error) {
	readyMu.Lock()
	ready = false
	readyMu.Unlock()
	defer func() {
		if err != nil {
			err = redactError(err)
			unsubscribeLogs()
		}
	}()
	if logPath != "" {
		// Embedded mihomo has no CLI -d flag. Use the app-private log directory as
		// HomeDir so HTTP proxy-providers can persist their cache inside Android's
		// sandbox and satisfy mihomo's safe-path checks.
		homeDir := filepath.Dir(logPath)
		if mkdirErr := os.MkdirAll(homeDir, 0o700); mkdirErr != nil {
			return mkdirErr
		}
		C.SetHomeDir(homeDir)
		log.SetLevel(log.DEBUG)
		subscribeLogs(logPath)
	}
	cfg, err := config.Parse([]byte(configYAML))
	if err != nil {
		return err
	}
	executor.ApplyConfig(cfg, true)
	readyMu.Lock()
	ready = true
	readyMu.Unlock()
	return nil
}

// Ready reports that the most recent configuration was applied and the
// embedded runtime may accept traffic.
func Ready() bool {
	readyMu.RLock()
	defer readyMu.RUnlock()
	return ready
}

// SubscriptionProviderState returns mihomo's API-safe provider JSON for the
// Detour subscription provider. The provider serializer intentionally contains
// node/runtime metadata but not the secret subscription URL.
// An empty string means the subscription provider is not active.
func SubscriptionProviderState() string {
	if !Ready() {
		return ""
	}
	provider, ok := tunnel.Providers()[subscriptionProviderName]
	if !ok {
		return ""
	}
	payload, err := json.Marshal(provider)
	if err != nil {
		return ""
	}
	return string(payload)
}

// RefreshSubscriptionProvider forces a remote provider update, then refreshes
// its health state. Callers must run this away from Android's main thread.
func RefreshSubscriptionProvider() error {
	if !Ready() {
		return errors.New("engine is not ready")
	}
	provider, ok := tunnel.Providers()[subscriptionProviderName]
	if !ok {
		return errors.New("subscription provider is not active")
	}
	if err := provider.Update(); err != nil {
		return redactError(err)
	}
	provider.HealthCheck()
	return nil
}

// FetchSubscriptionCatalog downloads and parses an HTTPS subscription without
// requiring the VPN engine to be running. Parsing intentionally follows the
// same YAML/V2Ray conversion path as mihomo's HTTP proxy-provider so the list
// shown by Android matches the nodes the runtime can later consume.
func FetchSubscriptionCatalog(subscriptionURL string) string {
	parsed, err := parseSubscriptionURL(subscriptionURL)
	if err != nil {
		return ""
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
		return ""
	}
	req.Header.Set("User-Agent", subscriptionUserAgent)
	resp, err := client.Do(req)
	if err != nil {
		return ""
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return ""
	}
	body, err := io.ReadAll(io.LimitReader(resp.Body, maxSubscriptionBodyBytes+1))
	if err != nil || len(body) == 0 || len(body) > maxSubscriptionBodyBytes {
		return ""
	}

	type proxySchema struct {
		Proxies []map[string]any `yaml:"proxies"`
	}
	schema := &proxySchema{}
	yamlErr := mihomoYaml.Unmarshal(body, schema)
	// URI/base64 subscription bodies can be valid YAML scalars, so a successful
	// YAML parse does not imply that a `proxies:` collection was present.
	// Fall back whenever YAML yielded no nodes, not only when it returned an error.
	if yamlErr != nil || len(schema.Proxies) == 0 {
		proxies, convertErr := convert.ConvertsV2Ray(body)
		if convertErr != nil || len(proxies) == 0 {
			return ""
		}
		schema.Proxies = proxies
	}

	type catalogNode struct {
		Name string `json:"name"`
		Type string `json:"type"`
	}
	nodes := make([]catalogNode, 0, min(len(schema.Proxies), maxSubscriptionNodes))
	seen := make(map[string]struct{}, len(schema.Proxies))
	for _, proxy := range schema.Proxies {
		name, ok := safeCatalogLabel(proxy["name"], 256)
		if !ok {
			continue
		}
		if _, duplicate := seen[name]; duplicate {
			continue
		}
		proxyType, ok := safeCatalogLabel(proxy["type"], 64)
		if !ok {
			proxyType = "unknown"
		}
		seen[name] = struct{}{}
		nodes = append(nodes, catalogNode{Name: name, Type: proxyType})
		if len(nodes) >= maxSubscriptionNodes {
			break
		}
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

// SelectSubscriptionNode selects a node in the live selector when connected,
// and always writes the choice into mihomo's selected-group cache. Writing the
// cache while disconnected makes the selection effective on the next Start.
func SelectSubscriptionNode(name string, homeDir string) error {
	name = strings.TrimSpace(name)
	if name == "" || len(name) > 256 || strings.IndexFunc(name, func(r rune) bool { return r < 0x20 || r == 0x7f }) >= 0 {
		return errors.New("invalid subscription node")
	}
	if homeDir != "" {
		if err := os.MkdirAll(homeDir, 0o700); err != nil {
			return err
		}
		C.SetHomeDir(homeDir)
	}
	if Ready() {
		proxy, ok := tunnel.Proxies()[subscriptionGroupName]
		if !ok {
			return errors.New("subscription selector is not active")
		}
		selector, ok := proxy.Adapter().(outboundgroup.SelectAble)
		if !ok {
			return errors.New("subscription group is not selectable")
		}
		if err := selector.Set(name); err != nil {
			return redactError(err)
		}
	}
	cachefile.Cache().SetSelected(subscriptionGroupName, name)
	return nil
}

// SubscriptionSelectedNode returns the live selected node, or the cached node
// while disconnected. homeDir must be the same app-private directory used by Start.
func SubscriptionSelectedNode(homeDir string) string {
	if homeDir != "" {
		if err := os.MkdirAll(homeDir, 0o700); err == nil {
			C.SetHomeDir(homeDir)
		}
	}
	if Ready() {
		if proxy, ok := tunnel.Proxies()[subscriptionGroupName]; ok {
			if selector, ok := proxy.Adapter().(*outboundgroup.Selector); ok {
				return selector.Now()
			}
		}
	}
	if selected := cachefile.Cache().SelectedMap(); selected != nil {
		return selected[subscriptionGroupName]
	}
	return ""
}

func parseSubscriptionURL(value string) (*url.URL, error) {
	if len(value) == 0 || len(value) > 8*1024 {
		return nil, errors.New("invalid subscription URL")
	}
	parsed, err := url.Parse(value)
	if err != nil || parsed.Scheme != "https" || parsed.Host == "" || parsed.User != nil {
		return nil, errors.New("invalid subscription URL")
	}
	return parsed, nil
}

func safeCatalogLabel(value any, maxChars int) (string, bool) {
	text, ok := value.(string)
	if !ok {
		return "", false
	}
	text = strings.TrimSpace(text)
	if text == "" || len([]rune(text)) > maxChars {
		return "", false
	}
	if strings.IndexFunc(text, func(r rune) bool { return r < 0x20 || r == 0x7f }) >= 0 {
		return "", false
	}
	return text, true
}

// Stop shuts down the mihomo runtime.
func Stop() {
	readyMu.Lock()
	ready = false
	readyMu.Unlock()
	executor.Shutdown()
	// Shutdown closes the TUN listener but leaves LastTunConf behind. The next
	// Start typically gets an equal conf (the OS reuses the fd number), and
	// ReCreateTun then silently skips creation while tunLister is nil — leaving
	// a live engine with no TUN reader. Reset so it always rebuilds.
	listener.LastTunConf = LC.Tun{}
	unsubscribeLogs()
}

var (
	logMu  sync.Mutex
	logSub observable.Subscription[log.Event]
)

func redactSensitiveURLs(value string) string {
	return sensitiveURLPattern.ReplaceAllString(value, "[redacted-url]")
}

func redactError(err error) error {
	if err == nil {
		return nil
	}
	return errors.New(redactSensitiveURLs(err.Error()))
}

func subscribeLogs(path string) {
	logMu.Lock()
	defer logMu.Unlock()
	unsubscribeLogsLocked()
	sub := log.Subscribe()
	f, err := os.Create(path)
	if err != nil {
		log.UnSubscribe(sub)
		return
	}
	logSub = sub
	go func() {
		defer f.Close()
		for e := range sub {
			_, _ = fmt.Fprintf(f, "%s %s\n", e.Type(), redactSensitiveURLs(e.Payload))
		}
	}()
}

func unsubscribeLogs() {
	logMu.Lock()
	defer logMu.Unlock()
	unsubscribeLogsLocked()
}

// caller must hold logMu; UnSubscribe closes the subscription channel, which
// ends the writer goroutine and closes its file — one writer per Start.
func unsubscribeLogsLocked() {
	if logSub != nil {
		log.UnSubscribe(logSub)
		logSub = nil
	}
}
