// Package engine exposes a minimal Android embedding for mihomo.
//
// The TUN file descriptor is supplied by the host through the generated
// configuration (tun.file-descriptor). The host excludes its own UID from
// the VpnService, so engine sockets never re-enter the TUN.
package engine

import (
	"fmt"
	"os"

	"github.com/metacubex/mihomo/config"
	"github.com/metacubex/mihomo/hub/executor"
	"github.com/metacubex/mihomo/log"
)

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
