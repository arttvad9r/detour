package engine

import (
	"encoding/base64"
	"net/http"
	"testing"
)

func TestParseSubscriptionMetadataHeaders(t *testing.T) {
	title := base64.StdEncoding.EncodeToString([]byte("Detour Premium"))
	headers := http.Header{}
	headers.Set("Profile-Title", "base64:"+title)
	headers.Set("Subscription-Userinfo", "upload=1024; download=2048; total=8192; expire=1893456000")

	metadata := parseSubscriptionMetadataHeaders(headers)
	want := subscriptionMetadata{
		Title:         "Detour Premium",
		UploadBytes:   1024,
		DownloadBytes: 2048,
		TotalBytes:    8192,
		ExpireAtUnix:  1893456000,
	}
	if metadata != want {
		t.Fatalf("unexpected metadata: %+v", metadata)
	}
}

func TestSubscriptionMetadataCompatibilityAndSafety(t *testing.T) {
	t.Run("raw title", func(t *testing.T) {
		headers := http.Header{"Profile-Title": []string{"My VPN"}}
		if got := parseSubscriptionMetadataHeaders(headers).Title; got != "My VPN" {
			t.Fatalf("unexpected title: %q", got)
		}
	})

	t.Run("base64 title keeps first line", func(t *testing.T) {
		encoded := base64.StdEncoding.EncodeToString([]byte("Name\nDescription"))
		headers := http.Header{"Profile-Title": []string{"base64:" + encoded}}
		if got := parseSubscriptionMetadataHeaders(headers).Title; got != "Name" {
			t.Fatalf("unexpected title: %q", got)
		}
	})

	t.Run("control characters rejected", func(t *testing.T) {
		encoded := base64.StdEncoding.EncodeToString([]byte("Bad\x1bTitle"))
		headers := http.Header{"Profile-Title": []string{"base64:" + encoded}}
		if got := parseSubscriptionMetadataHeaders(headers).Title; got != "" {
			t.Fatalf("unsafe title accepted: %q", got)
		}
	})

	t.Run("milliseconds expiry", func(t *testing.T) {
		headers := http.Header{"Subscription-Userinfo": []string{"expire=1893456000000"}}
		if got := parseSubscriptionMetadataHeaders(headers).ExpireAtUnix; got != 1893456000 {
			t.Fatalf("unexpected expiry: %d", got)
		}
	})

	t.Run("malformed userinfo ignored", func(t *testing.T) {
		headers := http.Header{"Subscription-Userinfo": []string{"upload=-1; total=abc; download"}}
		if got := parseSubscriptionMetadataHeaders(headers); !got.empty() {
			t.Fatalf("malformed userinfo accepted: %+v", got)
		}
	})
}
