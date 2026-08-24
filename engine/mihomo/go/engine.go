// Package engine exposes a minimal Android embedding for mihomo.
//
// The TUN file descriptor is supplied by the host through the generated
// configuration (tun.file-descriptor). The host excludes its own UID from
// the VpnService, so engine sockets never re-enter the TUN.
package engine

import (
	"fmt"
	"net/netip"
	"os"
	"strconv"
	"strings"

	"github.com/metacubex/mihomo/component/process"
	"github.com/metacubex/mihomo/config"
	"github.com/metacubex/mihomo/hub/executor"
	"github.com/metacubex/mihomo/log"
)

// ProcessResolver resolves the owning app of a TUN connection host-side.
// Return "" if unknown. Non-empty response MUST be "<uid> <packageName>".
type ProcessResolver interface {
	Resolve(network string, srcIP string, srcPort int64, dstIP string, dstPort int64) string
}

var hostResolver ProcessResolver

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
func Start(configYAML string, logPath string) error {
	if logPath != "" {
		log.SetLevel(log.DEBUG)
		subscribeLogs(logPath)
	}
	cfg, err := config.Parse([]byte(configYAML))
	if err != nil {
		return err
	}
	executor.ApplyConfig(cfg, true)
	return nil
}

// Stop shuts down the mihomo runtime.
func Stop() {
	executor.Shutdown()
}

func subscribeLogs(path string) {
	sub := log.Subscribe()
	f, err := os.Create(path)
	if err != nil {
		return
	}
	go func() {
		defer f.Close()
		defer log.UnSubscribe(sub)
		for e := range sub {
			_, _ = fmt.Fprintf(f, "%s %s\n", e.Type(), e.Payload)
		}
	}()
}
