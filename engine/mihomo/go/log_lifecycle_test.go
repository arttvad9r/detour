package engine

import "testing"

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
	logMu.Lock()
	stillSubscribed := logSub != nil
	logMu.Unlock()
	if !stillSubscribed {
		t.Fatal("parse failure must preserve the existing runtime log subscription")
	}
}
