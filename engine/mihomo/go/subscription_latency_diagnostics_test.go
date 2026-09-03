package engine

import (
	"context"
	"errors"
	"strings"
	"testing"
)

func TestSubscriptionLatencyTargetsExposeTransportErrors(t *testing.T) {
	targets := []subscriptionLatencyTarget{
		{
			Name: "Working",
			Probe: func(context.Context) (int, error) {
				return 81, nil
			},
		},
		{
			Name: "Reality failure",
			Probe: func(context.Context) (int, error) {
				return 0, errors.New("REALITY handshake failed: connection closed")
			},
		},
		{
			Name: "Invalid delay",
			Probe: func(context.Context) (int, error) {
				return 0, nil
			},
		},
	}

	results := runSubscriptionLatencyTargets(targets)
	if len(results) != 3 {
		t.Fatalf("got %d results, want 3", len(results))
	}
	if got := results[0].DelayMs; got != 81 {
		t.Fatalf("working delay = %d, want 81", got)
	}
	if got := results[1].ErrorClass; got != "reality" {
		t.Fatalf("REALITY error class = %q, want reality", got)
	}
	if got := results[1].ErrorText; !strings.Contains(got, "REALITY handshake failed") {
		t.Fatalf("REALITY error text = %q", got)
	}
	if got := results[2].ErrorClass; got != "other" {
		t.Fatalf("zero-delay error class = %q, want other", got)
	}

	payload := marshalSubscriptionLatencyResults(results)
	for _, expected := range []string{
		`"name":"Reality failure"`,
		`"errorClass":"reality"`,
		`"errorText":"REALITY handshake failed: connection closed"`,
		`"name":"Working","delayMs":81`,
	} {
		if !strings.Contains(payload, expected) {
			t.Fatalf("payload %q does not contain %q", payload, expected)
		}
	}
}

func TestSubscriptionLatencyDiagnosticsPayloadKeepsTransportAndDropsSecrets(t *testing.T) {
	proxy := map[string]any{
		"name":               "Main - 2",
		"type":               "vless",
		"server":             "edge.example.com",
		"port":               443,
		"uuid":               "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee",
		"network":            "tcp",
		"tls":                true,
		"servername":         "www.starlink.com",
		"client-fingerprint": "firefox",
		"flow":               "xtls-rprx-vision",
		"encryption":         "none",
		"reality-opts": map[string]any{
			"public-key": "secret-pbk",
			"short-id":   "secret-sid",
		},
	}

	payload := subscriptionLatencyDiagnosticsPayload(proxy)
	for _, want := range []string{
		`"name":"Main - 2"`,
		`"network":"tcp"`,
		`"tls":true`,
		`"reality":true`,
		`"servername":"www.starlink.com"`,
		`"client-fingerprint":"firefox"`,
		`"flow":"xtls-rprx-vision"`,
		`"encryption":"none"`,
	} {
		if !strings.Contains(payload, want) {
			t.Fatalf("payload %q does not contain %q", payload, want)
		}
	}
	for _, secret := range []string{
		"aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee",
		"secret-pbk",
		"secret-sid",
		"public-key",
		"short-id",
	} {
		if strings.Contains(payload, secret) {
			t.Fatalf("payload leaked %q: %s", secret, payload)
		}
	}
}
