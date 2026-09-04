package engine

import (
	"testing"

	C "github.com/metacubex/mihomo/constant"
)

func TestParseFailureKeepsExistingRuntimeLogging(t *testing.T) {
	Stop()
	t.Cleanup(Stop)

	valid := "mode: rule\nlog-level: silent\nproxies: []\nrules:\n  - MATCH,DIRECT\n"
	logPath := t.TempDir() + "/engine.log"
	if err := Start(valid, logPath); err != nil {
		t.Fatalf("start valid runtime: %v", err)
	}
	if !Ready() {
		t.Fatal("valid runtime must be ready")
	}
	originalHomeDir := C.Path.HomeDir()
	logMu.Lock()
	hadSubscription := logSub != nil
	logMu.Unlock()
	if !hadSubscription {
		t.Fatal("valid logged runtime must have an active log subscription")
	}

	if err := Start("mode: [", t.TempDir()+"/replacement.log"); err == nil {
		t.Fatal("invalid replacement config must fail parsing")
	}
	if !Ready() {
		t.Fatal("parse failure must leave the existing runtime ready")
	}
	if got := C.Path.HomeDir(); got != originalHomeDir {
		t.Fatalf("parse failure changed active HomeDir to %q, want %q", got, originalHomeDir)
	}
	logMu.Lock()
	stillSubscribed := logSub != nil
	logMu.Unlock()
	if !stillSubscribed {
		t.Fatal("parse failure must preserve the existing runtime log subscription")
	}
}
