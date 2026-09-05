package engine

import (
	"encoding/base64"
	"encoding/json"
	"errors"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"
	"unicode/utf8"
)

// SubscriptionMetadata is deliberately returned to Android as JSON so the
// gomobile boundary stays narrow and backwards-compatible with the existing
// subscription catalog APIs.
type SubscriptionMetadata struct {
	Title               string `json:"title,omitempty"`
	UpdateIntervalHours int    `json:"updateIntervalHours,omitempty"`
	UploadBytes         int64  `json:"uploadBytes,omitempty"`
	DownloadBytes       int64  `json:"downloadBytes,omitempty"`
	TotalBytes          int64  `json:"totalBytes,omitempty"`
	ExpireAtUnix        int64  `json:"expireAtUnix,omitempty"`
	SupportURL          string `json:"supportUrl,omitempty"`
	ProfileWebPageURL   string `json:"profileWebPageUrl,omitempty"`
	Announcement        string `json:"announcement,omitempty"`
}

// FetchSubscriptionMetadata reads the de-facto subscription response headers
// used by modern panels. Routing/listener/DNS headers are intentionally ignored:
// Detour remains the policy owner and only imports presentation/update metadata.
func FetchSubscriptionMetadata(subscriptionURL string) string {
	parsed, err := parseSubscriptionURL(subscriptionURL)
	if err != nil {
		return ""
	}

	client := &http.Client{
		Timeout: 15 * time.Second,
		CheckRedirect: func(req *http.Request, via []*http.Request) error {
			if len(via) >= 5 {
				return errors.New("too many subscription redirects")
			}
			if req.URL.Scheme != "https" || req.URL.Host == "" || req.URL.User != nil {
				return errors.New("unsafe subscription redirect")
			}
			if len(via) > 0 && !sameSubscriptionOrigin(via[0].URL, req.URL) {
				return errors.New("cross-origin subscription redirect")
			}
			return nil
		},
	}

	req, err := http.NewRequest(http.MethodGet, parsed.String(), nil)
	if err != nil {
		return ""
	}
	req.Header.Set("User-Agent", subscriptionUserAgent)

	resp, err := client.Do(req)
	if err != nil {
		return ""
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return ""
	}

	metadata := parseSubscriptionMetadataHeaders(resp.Header)
	payload, err := json.Marshal(metadata)
	if err != nil {
		return ""
	}
	return string(payload)
}

func parseSubscriptionMetadataHeaders(headers http.Header) SubscriptionMetadata {
	metadata := SubscriptionMetadata{
		Title:             decodeSubscriptionMetadataText(headers.Get("profile-title"), 256, true),
		SupportURL:        safeSubscriptionMetadataURL(headers.Get("support-url")),
		ProfileWebPageURL: safeSubscriptionMetadataURL(headers.Get("profile-web-page-url")),
		Announcement:      decodeSubscriptionMetadataText(headers.Get("announce"), 2048, false),
	}

	if value, err := strconv.Atoi(strings.TrimSpace(headers.Get("profile-update-interval"))); err == nil {
		// Provider values are in hours. Clamp absurd values instead of letting a
		// malformed header turn into an unusable scheduler duration later.
		if inRange(value, 1, 24*365) {
			metadata.UpdateIntervalHours = value
		}
	}

	parseSubscriptionUserInfo(headers.Get("subscription-userinfo"), &metadata)
	return metadata
}

func parseSubscriptionUserInfo(raw string, metadata *SubscriptionMetadata) {
	for _, part := range strings.Split(raw, ";") {
		pair := strings.SplitN(strings.TrimSpace(part), "=", 2)
		if len(pair) != 2 {
			continue
		}
		value, err := strconv.ParseInt(strings.TrimSpace(pair[1]), 10, 64)
		if err != nil || value < 0 {
			continue
		}
		switch strings.ToLower(strings.TrimSpace(pair[0])) {
		case "upload":
			metadata.UploadBytes = value
		case "download":
			metadata.DownloadBytes = value
		case "total":
			metadata.TotalBytes = value
		case "expire":
			// Some panels historically emitted milliseconds. Keep compatibility
			// without accepting values outside a practical timestamp range.
			if value > 32_000_000_000 {
				value /= 1000
			}
			if value > 0 {
				metadata.ExpireAtUnix = value
			}
		}
	}
}

func decodeSubscriptionMetadataText(raw string, maxBytes int, firstLineOnly bool) string {
	value := strings.TrimSpace(raw)
	if value == "" {
		return ""
	}

	if strings.HasPrefix(strings.ToLower(value), "base64:") {
		encoded := strings.TrimSpace(value[len("base64:"):])
		var decoded []byte
		var err error
		for _, encoding := range []*base64.Encoding{
			base64.StdEncoding,
			base64.RawStdEncoding,
			base64.URLEncoding,
			base64.RawURLEncoding,
		} {
			decoded, err = encoding.DecodeString(encoded)
			if err == nil {
				break
			}
		}
		if err != nil || !utf8.Valid(decoded) {
			return ""
		}
		value = strings.TrimSpace(string(decoded))
	}

	value = strings.ReplaceAll(value, "\r\n", "\n")
	value = strings.ReplaceAll(value, "\r", "\n")
	if firstLineOnly {
		value = strings.SplitN(value, "\n", 2)[0]
	}
	value = strings.TrimSpace(value)
	if value == "" || len(value) > maxBytes || !utf8.ValidString(value) {
		return ""
	}
	for _, r := range value {
		if r < 0x20 && r != '\n' && r != '\t' {
			return ""
		}
	}
	return value
}

func safeSubscriptionMetadataURL(raw string) string {
	value := strings.TrimSpace(raw)
	if value == "" || len(value) > 2048 {
		return ""
	}
	parsed, err := url.Parse(value)
	if err != nil || parsed.Scheme != "https" || parsed.Host == "" || parsed.User != nil {
		return ""
	}
	return parsed.String()
}

func inRange(value, minValue, maxValue int) bool {
	return value >= minValue && value <= maxValue
}
