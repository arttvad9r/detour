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
	headers.Set("Profile-Update-Interval", "12")
	headers.Set("Subscription-Userinfo", "upload=1024; download=2048; total=8192; expire=1893456000")
	headers.Set("Support-Url", "https://support.example.com/help")
	headers.Set("Profile-Web-Page-Url", "https://example.com/account")

	metadata := parseSubscriptionMetadataHeaders(headers)
	if metadata.Title != "Detour Premium" {
		t.Fatalf("unexpected title: %q", metadata.Title)
	}
	if metadata.UpdateIntervalHours != 12 {
		t.Fatalf("unexpected update interval: %d", metadata.UpdateIntervalHours)
	}
	if metadata.UploadBytes != 1024 || metadata.DownloadBytes != 2048 || metadata.TotalBytes != 8192 {
		t.Fatalf("unexpected usage: %+v", metadata)
	}
	if metadata.ExpireAtUnix != 1893456000 {
		t.Fatalf("unexpected expiry: %d", metadata.ExpireAtUnix)
	}
	if metadata.SupportURL != "https://support.example.com/help" {
		t.Fatalf("unexpected support URL: %q", metadata.SupportURL)
	}
	if metadata.ProfileWebPageURL != "https://example.com/account" {
		t.Fatalf("unexpected profile URL: %q", metadata.ProfileWebPageURL)
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

	t.Run("milliseconds expiry", func(t *testing.T) {
		headers := http.Header{"Subscription-Userinfo": []string{"expire=1893456000000"}}
		if got := parseSubscriptionMetadataHeaders(headers).ExpireAtUnix; got != 1893456000 {
			t.Fatalf("unexpected expiry: %d", got)
		}
	})

	t.Run("unsafe URL ignored", func(t *testing.T) {
		headers := http.Header{"Support-Url": []string{"http://example.com"}}
		if got := parseSubscriptionMetadataHeaders(headers).SupportURL; got != "" {
			t.Fatalf("unsafe URL accepted: %q", got)
		}
	})

	t.Run("invalid interval ignored", func(t *testing.T) {
		headers := http.Header{"Profile-Update-Interval": []string{"999999"}}
		if got := parseSubscriptionMetadataHeaders(headers).UpdateIntervalHours; got != 0 {
			t.Fatalf("invalid interval accepted: %d", got)
		}
	})
}
