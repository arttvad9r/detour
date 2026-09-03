package engine

import (
	"context"
	"errors"
	"testing"

	"github.com/metacubex/mihomo/adapter"
	mihomoTLS "github.com/metacubex/mihomo/component/tls"
)

func TestEmbeddedDiagnosticsPatchesAreEnabled(t *testing.T) {
	if !adapter.DetourURLTestDiagnosticsEnabled {
		t.Fatal("URLTest diagnostics patch is disabled")
	}
	if !mihomoTLS.DetourRealityDiagnosticsEnabled {
		t.Fatal("REALITY diagnostics patch is disabled")
	}
}

func TestEmbeddedURLTestErrorClassification(t *testing.T) {
	tests := []struct {
		name string
		err  error
		want string
	}{
		{name: "timeout", err: context.DeadlineExceeded, want: "timeout"},
		{name: "reality", err: errors.New("REALITY handshake failed: connection closed"), want: "reality"},
		{name: "dns", err: errors.New("lookup edge.example: no such host"), want: "dns"},
		{name: "tls", err: errors.New("tls: failed to verify certificate"), want: "tls"},
		{name: "grpc", err: errors.New("grpc transport closed"), want: "grpc"},
		{name: "connection", err: errors.New("read: connection reset by peer"), want: "connection"},
		{name: "dial", err: errors.New("dial tcp 203.0.113.7:443: connection refused"), want: "dial"},
		{name: "other", err: errors.New("unexpected protocol error"), want: "other"},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			if got := adapter.DetourURLTestErrorClass(test.err); got != test.want {
				t.Fatalf("error class = %q, want %q", got, test.want)
			}
		})
	}
}
