package engine

import "testing"

func TestSanitizedSubscriptionNodeDiagnosticsKeepsTransportFieldsAndDropsSecrets(t *testing.T) {
	proxy := map[string]any{
		"name":               "Main - 2",
		"type":               "vless",
		"server":             "203.0.113.9",
		"port":               443,
		"uuid":               "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee",
		"network":            "grpc",
		"tls":                true,
		"servername":         "www.example.com",
		"client-fingerprint": "firefox",
		"flow":               "xtls-rprx-vision",
		"xudp":               true,
		"reality-opts": map[string]any{
			"public-key": "secret-pbk",
			"short-id":   "secret-sid",
		},
		"grpc-opts": map[string]any{
			"grpc-service-name": "grpc-service",
		},
	}

	got := sanitizedSubscriptionNodeDiagnostics(proxy)
	if got["name"] != "Main - 2" || got["network"] != "grpc" {
		t.Fatalf("unexpected identity/transport fields: %#v", got)
	}
	if got["server"] != "203.0.113.9" || got["port"] != "443" {
		t.Fatalf("unexpected endpoint fields: %#v", got)
	}
	if got["tls"] != true || got["reality"] != true || got["xudp"] != true {
		t.Fatalf("unexpected boolean capability fields: %#v", got)
	}
	if got["servername"] != "www.example.com" || got["client-fingerprint"] != "firefox" {
		t.Fatalf("unexpected TLS fields: %#v", got)
	}
	if got["flow"] != "xtls-rprx-vision" || got["grpc-service-name"] != "grpc-service" {
		t.Fatalf("unexpected VLESS transport fields: %#v", got)
	}
	for _, secret := range []string{"uuid", "public-key", "short-id", "pbk", "sid"} {
		if _, exists := got[secret]; exists {
			t.Fatalf("secret field %q leaked: %#v", secret, got)
		}
	}
}
