package engine

import "testing"

func TestReadyIsFalseBeforeStart(t *testing.T) {
	if Ready() {
		t.Fatal("engine must not report readiness before Start")
	}
}
