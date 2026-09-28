package engine

import (
	"encoding/base64"
	"net/http"
	"strconv"
	"strings"
	"unicode/utf8"
)

// subscriptionMetadata carries the presentation-only response headers used by
// modern subscription panels. Routing, DNS and listener headers are ignored:
// Detour remains the policy owner and only shows what the provider reports.
type subscriptionMetadata struct {
	Title         string `json:"title,omitempty"`
	UploadBytes   int64  `json:"uploadBytes,omitempty"`
	DownloadBytes int64  `json:"downloadBytes,omitempty"`
	TotalBytes    int64  `json:"totalBytes,omitempty"`
	ExpireAtUnix  int64  `json:"expireAtUnix,omitempty"`
}

func (m subscriptionMetadata) empty() bool {
	return m == subscriptionMetadata{}
}

func parseSubscriptionMetadataHeaders(headers http.Header) subscriptionMetadata {
	metadata := subscriptionMetadata{
		Title: decodeSubscriptionTitle(headers.Get("profile-title")),
	}
	parseSubscriptionUserInfo(headers.Get("subscription-userinfo"), &metadata)
	return metadata
}

func parseSubscriptionUserInfo(raw string, metadata *subscriptionMetadata) {
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
			// Some panels historically emitted milliseconds.
			if value > 32_000_000_000 {
				value /= 1000
			}
			metadata.ExpireAtUnix = value
		}
	}
}

// decodeSubscriptionTitle accepts both raw and "base64:"-prefixed titles and
// keeps only the first line, so a provider cannot push multi-line UI text.
func decodeSubscriptionTitle(raw string) string {
	const maxBytes = 256
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
		value = string(decoded)
	}
	value = strings.ReplaceAll(value, "\r", "\n")
	value = strings.TrimSpace(strings.SplitN(value, "\n", 2)[0])
	if value == "" || len(value) > maxBytes || !utf8.ValidString(value) {
		return ""
	}
	for _, r := range value {
		if r < 0x20 || r == 0x7f {
			return ""
		}
	}
	return value
}
