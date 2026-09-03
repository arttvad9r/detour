package engine

import (
	"encoding/json"
	"fmt"
	"strings"
)

// SubscriptionNodeDiagnostics returns only non-secret fields from the exact
// prepared VLESS mapping used by the subscription provider. It intentionally
// excludes UUID, subscription URL, REALITY public key/short ID and other
// credentials so Android diagnostics can safely distinguish converter/runtime
// mismatches without exposing account material.
func SubscriptionNodeDiagnostics(subscriptionURL string, nodeName string) string {
	name := strings.TrimSpace(nodeName)
	if name == "" {
		return ""
	}
	proxies, err := fetchPreparedSubscriptionProxies(subscriptionURL)
	if err != nil {
		return ""
	}
	for _, proxy := range proxies {
		proxyName, ok := safeCatalogLabel(proxy["name"], 256)
		if !ok || proxyName != name {
			continue
		}
		payload, err := json.Marshal(sanitizedSubscriptionNodeDiagnostics(proxy))
		if err != nil {
			return ""
		}
		return string(payload)
	}
	return ""
}

func sanitizedSubscriptionNodeDiagnostics(proxy map[string]any) map[string]any {
	result := map[string]any{
		"name":    safeDiagnosticValue(proxy["name"]),
		"type":    safeDiagnosticValue(proxy["type"]),
		"server":  safeDiagnosticValue(proxy["server"]),
		"port":    safeDiagnosticValue(proxy["port"]),
		"network": safeDiagnosticValue(proxy["network"]),
		"tls":     proxy["tls"] == true,
		"reality": proxy["reality-opts"] != nil,
		"xudp":    proxy["xudp"] == true,
	}
	copyDiagnosticField(result, proxy, "servername")
	copyDiagnosticField(result, proxy, "client-fingerprint")
	copyDiagnosticField(result, proxy, "flow")
	copyDiagnosticField(result, proxy, "encryption")

	if grpc, ok := proxy["grpc-opts"].(map[string]any); ok {
		copyDiagnosticField(result, grpc, "grpc-service-name")
	}
	if ws, ok := proxy["ws-opts"].(map[string]any); ok {
		copyDiagnosticFieldAs(result, ws, "path", "ws-path")
		if headers, ok := ws["headers"].(map[string]any); ok {
			copyDiagnosticFieldAs(result, headers, "Host", "ws-host")
		}
		if enabled, ok := ws["v2ray-http-upgrade"].(bool); ok {
			result["http-upgrade"] = enabled
		}
	}
	if h2, ok := proxy["h2-opts"].(map[string]any); ok {
		copyDiagnosticFieldAs(result, h2, "path", "h2-path")
		copyDiagnosticFieldAs(result, h2, "host", "h2-host")
	}
	if xhttp, ok := proxy["xhttp-opts"].(map[string]any); ok {
		copyDiagnosticFieldAs(result, xhttp, "path", "xhttp-path")
		copyDiagnosticFieldAs(result, xhttp, "host", "xhttp-host")
		copyDiagnosticFieldAs(result, xhttp, "mode", "xhttp-mode")
	}
	return result
}

func copyDiagnosticField(dst map[string]any, src map[string]any, key string) {
	copyDiagnosticFieldAs(dst, src, key, key)
}

func copyDiagnosticFieldAs(dst map[string]any, src map[string]any, key string, outputKey string) {
	value, exists := src[key]
	if !exists {
		return
	}
	if safe := safeDiagnosticValue(value); safe != "" {
		dst[outputKey] = safe
	}
}

func safeDiagnosticValue(value any) string {
	if value == nil {
		return ""
	}
	text := strings.TrimSpace(fmt.Sprint(value))
	if text == "" || len(text) > 512 {
		return ""
	}
	for _, r := range text {
		if r < 0x20 || r == 0x7f {
			return ""
		}
	}
	return text
}
