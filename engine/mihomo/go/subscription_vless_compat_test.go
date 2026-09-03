package engine

import (
	"encoding/base64"
	"testing"
)

const compatRealityPublicKey = "ppQ9FwLrLIa0AOrp1WvcyiaQ37vg2WSy_CD4bIdiTUw"

func TestPreparedSubscriptionUsesHostAsMissingRealitySNI(t *testing.T) {
	const link = "vless://a1b2c3d4-eacc-4433-981b-7e5f9a8b1234@142.98.76.54:34888?encryption=none&security=reality&type=tcp&host=github.io&fp=ios&pbk=" + compatRealityPublicKey + "&sid=6ba85179f3a2b4c5&flow=xtls-rprx-vision#HostFallback"

	proxies, err := parsePreparedSubscriptionProxies([]byte(link))
	if err != nil {
		t.Fatalf("Reality VLESS subscription body was rejected: %v", err)
	}
	if len(proxies) != 1 {
		t.Fatalf("got %d proxies, want 1", len(proxies))
	}
	if got := proxies[0]["servername"]; got != "github.io" {
		t.Fatalf("servername = %v, want host fallback github.io", got)
	}
	if got := proxies[0]["client-fingerprint"]; got != "iOS" {
		t.Fatalf("client-fingerprint = %v, want iOS", got)
	}
}

func TestPreparedSubscriptionExplicitSNIWinsOverHost(t *testing.T) {
	const link = "vless://a1b2c3d4-eacc-4433-981b-7e5f9a8b1234@142.98.76.54:34888?encryption=none&security=reality&type=tcp&host=wrong.example&sni=github.io&fp=chrome&pbk=" + compatRealityPublicKey + "&sid=6ba85179f3a2b4c5&flow=xtls-rprx-vision#ExplicitSNI"

	proxies, err := parsePreparedSubscriptionProxies([]byte(link))
	if err != nil {
		t.Fatalf("Reality VLESS subscription body was rejected: %v", err)
	}
	if len(proxies) != 1 {
		t.Fatalf("got %d proxies, want 1", len(proxies))
	}
	if got := proxies[0]["servername"]; got != "github.io" {
		t.Fatalf("servername = %v, want explicit github.io", got)
	}
}

func TestPreparedSubscriptionNormalizesBase64VlessCompatibility(t *testing.T) {
	const link = "vless://a1b2c3d4-eacc-4433-981b-7e5f9a8b1234@142.98.76.54:34888?encryption=none&security=reality&type=tcp&host=github.io&fp=ios&pbk=" + compatRealityPublicKey + "&sid=6ba85179f3a2b4c5&flow=xtls-rprx-vision#Base64Fallback"
	body := []byte(base64.StdEncoding.EncodeToString([]byte(link)))

	proxies, err := parsePreparedSubscriptionProxies(body)
	if err != nil {
		t.Fatalf("base64 Reality VLESS subscription body was rejected: %v", err)
	}
	if len(proxies) != 1 {
		t.Fatalf("got %d proxies, want 1", len(proxies))
	}
	if got := proxies[0]["servername"]; got != "github.io" {
		t.Fatalf("servername = %v, want host fallback github.io", got)
	}
	if got := proxies[0]["client-fingerprint"]; got != "iOS" {
		t.Fatalf("client-fingerprint = %v, want iOS", got)
	}
}
