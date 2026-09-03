package engine

import "github.com/metacubex/mihomo/adapter"

// Offline latency tests can run before any Mihomo config has been parsed.
// Match Detour's runtime config and NekoBox-style warm RTT semantics from the
// first process launch instead of inheriting Mihomo's UnifiedDelay=false default.
func init() {
	adapter.UnifiedDelay.Store(true)
}
