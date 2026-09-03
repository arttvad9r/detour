package engine

import (
	"fmt"
	"net"
	"testing"
)

func TestStopReleasesCustomProbeListener(t *testing.T) {
	reservation, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("reserve loopback port: %v", err)
	}
	port := reservation.Addr().(*net.TCPAddr).Port
	if err := reservation.Close(); err != nil {
		t.Fatalf("release reserved port: %v", err)
	}

	config := fmt.Sprintf(`
mode: rule
log-level: silent
proxies: []
listeners:
  - name: PROBE_TEST
    type: mixed
    listen: 127.0.0.1
    port: %d
    proxy: DIRECT
rules:
  - MATCH,DIRECT
`, port)

	if err := Start(config, ""); err != nil {
		t.Fatalf("start with custom listener: %v", err)
	}
	defer Stop()

	if listener, listenErr := net.Listen("tcp", fmt.Sprintf("127.0.0.1:%d", port)); listenErr == nil {
		_ = listener.Close()
		t.Fatal("probe listener port unexpectedly free while engine is active")
	}

	Stop()
	listener, err := net.Listen("tcp", fmt.Sprintf("127.0.0.1:%d", port))
	if err != nil {
		t.Fatalf("probe listener port still occupied after Stop: %v", err)
	}
	_ = listener.Close()
}
