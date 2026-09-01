// Package engine exposes a minimal Android embedding for mihomo.
//
// The TUN file descriptor is supplied by the host through the generated
// configuration (tun.file-descriptor). The host excludes its own UID from
// the VpnService, so engine sockets never re-enter the TUN.
package engine

import (
	"encoding/json"
	"fmt"
	"net/netip"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"sync"

	"github.com/metacubex/mihomo/common/observable"
	"github.com/metacubex/mihomo/component/process"
	"github.com/metacubex/mihomo/config"
	C "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/hub/executor"
	"github.com/metacubex/mihomo/listener"
	LC "github.com/metacubex/mihomo/listener/config"
	"github.com/metacubex/mihomo/log"
	"github.com/metacubex/mihomo/tunnel"
)

const subscriptionProviderName = "DETOUR_SUBSCRIPTION"

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
		return fmt.Errorf("engine is not ready")
	}
	provider, ok := tunnel.Providers()[subscriptionProviderName]
	if !ok {
		return fmt.Errorf("subscription provider is not active")
	}
	if err := provider.Update(); err != nil {
		return err
	}
	provider.HealthCheck()
	return nil
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
			_, _ = fmt.Fprintf(f, "%s %s\n", e.Type(), e.Payload)
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
