package engine

import (
	"testing"

	"github.com/metacubex/mihomo/component/profile/cachefile"
	C "github.com/metacubex/mihomo/constant"
)

func TestActiveSubscriptionSelectionDoesNotFallbackToNativeCache(t *testing.T) {
	Stop()
	oldHomeDir := C.Path.HomeDir()
	homeDir := t.TempDir()
	C.SetHomeDir(homeDir)
	cachefile.Cache().SetSelected(subscriptionSelectionKey(homeDir), "stale-node")

	readyMu.Lock()
	ready = true
	readyMu.Unlock()
	t.Cleanup(func() {
		readyMu.Lock()
		ready = false
		readyMu.Unlock()
		C.SetHomeDir(oldHomeDir)
	})

	if got := SubscriptionSelectedNode(homeDir); got != "" {
		t.Fatalf("active selection without a live selector = %q, want blank", got)
	}
}
