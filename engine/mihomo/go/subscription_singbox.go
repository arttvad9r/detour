package engine

import (
	"encoding/json"
	"strings"
)

var singBoxSupportedOutboundTypes = map[string]string{
	"vless":       "vless",
	"vmess":       "vmess",
	"trojan":      "trojan",
	"shadowsocks": "ss",
}

type singBoxRoot struct {
	Outbounds []json.RawMessage `json:"outbounds"`
}

// parseSingBoxSubscription extracts only remote proxy outbounds from a sing-box
// JSON config. Routing, DNS, selectors, dialer chaining and multiplexing remain
// Detour-owned concepts and are deliberately not imported. A recognized JSON
// config with no safely convertible nodes returns recognized=true and an empty
// slice so callers do not reinterpret it as an unrelated share-link format.
func parseSingBoxSubscription(body []byte) (proxies []map[string]any, recognized bool) {
	var envelope map[string]json.RawMessage
	if err := json.Unmarshal(body, &envelope); err != nil {
		return nil, false
	}
	rawOutbounds, ok := envelope["outbounds"]
	if !ok {
		return nil, false
	}
	recognized = true

	var root singBoxRoot
	if err := json.Unmarshal(body, &root); err != nil || len(root.Outbounds) == 0 {
		return nil, true
	}

	proxies = make([]map[string]any, 0, min(len(root.Outbounds), maxSubscriptionNodes))
	for _, raw := range root.Outbounds {
		proxy, ok := convertSingBoxOutbound(raw)
		if !ok {
			continue
		}
		proxies = append(proxies, proxy)
		if len(proxies) >= maxSubscriptionNodes {
			break
		}
	}
	return proxies, true
}

func convertSingBoxOutbound(raw json.RawMessage) (map[string]any, bool) {
	var fields map[string]json.RawMessage
	if err := json.Unmarshal(raw, &fields); err != nil {
		return nil, false
	}
	outboundType, ok := jsonString(fields["type"])
	if !ok {
		return nil, false
	}
	mihomoType, supported := singBoxSupportedOutboundTypes[strings.ToLower(strings.TrimSpace(outboundType))]
	if !supported {
		return nil, false
	}

	allowed := singBoxAllowedOutboundFields(mihomoType)
	if !onlyAllowedJSONFields(fields, allowed) {
		return nil, false
	}
	if rawMultiplex, exists := fields["multiplex"]; exists && !jsonSemanticallyEmpty(rawMultiplex) {
		return nil, false
	}

	name, nameOK := jsonString(fields["tag"])
	server, serverOK := jsonString(fields["server"])
	port, portOK := jsonInt(fields["server_port"])
	name = strings.TrimSpace(name)
	server = strings.TrimSpace(server)
	if !nameOK || !serverOK || !portOK || name == "" || server == "" || port < 1 || port > 65535 {
		return nil, false
	}

	udp, networkOK := singBoxUDPEnabled(fields["network"])
	if !networkOK {
		return nil, false
	}
	proxy := map[string]any{
		"name":   name,
		"type":   mihomoType,
		"server": server,
		"port":   port,
		"udp":    udp,
	}

	switch mihomoType {
	case "vless":
		uuid, ok := requiredJSONText(fields["uuid"])
		if !ok {
			return nil, false
		}
		proxy["uuid"] = uuid
		if flow, ok := optionalJSONText(fields["flow"]); ok && flow != "" {
			proxy["flow"] = flow
		}
		if packetEncoding, ok := optionalJSONText(fields["packet_encoding"]); ok && packetEncoding != "" {
			if packetEncoding != "packetaddr" && packetEncoding != "xudp" {
				return nil, false
			}
			proxy["packet-encoding"] = packetEncoding
		}
	case "vmess":
		uuid, ok := requiredJSONText(fields["uuid"])
		if !ok {
			return nil, false
		}
		proxy["uuid"] = uuid
		cipher := "auto"
		if security, ok := optionalJSONText(fields["security"]); ok && security != "" {
			cipher = security
		}
		proxy["cipher"] = cipher
		if alterID, ok := optionalJSONInt(fields["alter_id"]); ok {
			if alterID < 0 {
				return nil, false
			}
			proxy["alterId"] = alterID
		} else {
			proxy["alterId"] = 0
		}
		if padding, ok := optionalJSONBool(fields["global_padding"]); ok {
			proxy["global-padding"] = padding
		}
		if authenticatedLength, ok := optionalJSONBool(fields["authenticated_length"]); ok {
			proxy["authenticated-length"] = authenticatedLength
		}
		if packetEncoding, ok := optionalJSONText(fields["packet_encoding"]); ok && packetEncoding != "" {
			if packetEncoding != "packetaddr" && packetEncoding != "xudp" {
				return nil, false
			}
			proxy["packet-encoding"] = packetEncoding
		}
	case "trojan":
		password, ok := requiredJSONText(fields["password"])
		if !ok {
			return nil, false
		}
		proxy["password"] = password
	case "ss":
		method, methodOK := requiredJSONText(fields["method"])
		password, passwordOK := requiredJSONText(fields["password"])
		if !methodOK || !passwordOK {
			return nil, false
		}
		proxy["cipher"] = method
		proxy["password"] = password
		if plugin, ok := optionalJSONText(fields["plugin"]); ok && plugin != "" {
			return nil, false
		}
		if pluginOpts, ok := optionalJSONText(fields["plugin_opts"]); ok && pluginOpts != "" {
			return nil, false
		}
		if rawUOT, exists := fields["udp_over_tcp"]; exists && !jsonSemanticallyEmpty(rawUOT) {
			return nil, false
		}
	}

	if mihomoType != "ss" {
		if rawTLS, exists := fields["tls"]; exists {
			if !applySingBoxTLS(proxy, mihomoType, rawTLS) {
				return nil, false
			}
		} else if mihomoType == "trojan" {
			// Mihomo's Trojan adapter is TLS-based. Do not silently turn a
			// sing-box plaintext Trojan outbound into TLS.
			return nil, false
		}

		if rawTransport, exists := fields["transport"]; exists {
			if !applySingBoxTransport(proxy, rawTransport) {
				return nil, false
			}
		}
	}
	return proxy, true
}

func singBoxAllowedOutboundFields(mihomoType string) map[string]struct{} {
	common := []string{"type", "tag", "server", "server_port", "network", "multiplex"}
	allowed := make(map[string]struct{}, 16)
	for _, field := range common {
		allowed[field] = struct{}{}
	}
	switch mihomoType {
	case "vless":
		for _, field := range []string{"uuid", "flow", "tls", "packet_encoding", "transport"} {
			allowed[field] = struct{}{}
		}
	case "vmess":
		for _, field := range []string{
			"uuid", "security", "alter_id", "global_padding", "authenticated_length",
			"tls", "packet_encoding", "transport",
		} {
			allowed[field] = struct{}{}
		}
	case "trojan":
		for _, field := range []string{"password", "tls", "transport"} {
			allowed[field] = struct{}{}
		}
	case "ss":
		for _, field := range []string{"method", "password", "plugin", "plugin_opts", "udp_over_tcp"} {
			allowed[field] = struct{}{}
		}
	}
	return allowed
}

func singBoxUDPEnabled(raw json.RawMessage) (bool, bool) {
	if len(raw) == 0 || string(raw) == "null" {
		return true, true
	}
	network, ok := jsonString(raw)
	if !ok {
		return false, false
	}
	switch strings.ToLower(strings.TrimSpace(network)) {
	case "":
		return true, true
	case "tcp":
		return false, true
	case "udp":
		// Mihomo's proxy adapters can disable UDP but cannot disable TCP.
		return false, false
	default:
		return false, false
	}
}

func applySingBoxTLS(proxy map[string]any, mihomoType string, raw json.RawMessage) bool {
	if jsonSemanticallyEmpty(raw) {
		return mihomoType != "trojan"
	}
	var fields map[string]json.RawMessage
	if err := json.Unmarshal(raw, &fields); err != nil {
		return false
	}
	if !onlyAllowedJSONFields(fields, stringSet(
		"enabled", "disable_sni", "server_name", "insecure", "alpn", "utls", "reality",
	)) {
		return false
	}
	enabled, ok := optionalJSONBool(fields["enabled"])
	if !ok || !enabled {
		return mihomoType != "trojan" && allJSONFieldsEmptyExcept(fields, "enabled")
	}
	if disabledSNI, ok := optionalJSONBool(fields["disable_sni"]); ok && disabledSNI {
		return false
	}
	proxy["tls"] = true

	if serverName, ok := optionalJSONText(fields["server_name"]); ok && serverName != "" {
		if mihomoType == "trojan" {
			proxy["sni"] = serverName
		} else {
			proxy["servername"] = serverName
		}
	}
	if insecure, ok := optionalJSONBool(fields["insecure"]); ok {
		proxy["skip-cert-verify"] = insecure
	}
	if rawALPN, exists := fields["alpn"]; exists && !jsonSemanticallyEmpty(rawALPN) {
		var alpn []string
		if err := json.Unmarshal(rawALPN, &alpn); err != nil || len(alpn) == 0 {
			return false
		}
		for _, value := range alpn {
			if strings.TrimSpace(value) == "" {
				return false
			}
		}
		proxy["alpn"] = alpn
	}
	if rawUTLS, exists := fields["utls"]; exists && !jsonSemanticallyEmpty(rawUTLS) {
		var utls map[string]json.RawMessage
		if err := json.Unmarshal(rawUTLS, &utls); err != nil ||
			!onlyAllowedJSONFields(utls, stringSet("enabled", "fingerprint")) {
			return false
		}
		utlsEnabled, ok := optionalJSONBool(utls["enabled"])
		if !ok || !utlsEnabled {
			return false
		}
		fingerprint, ok := requiredJSONText(utls["fingerprint"])
		if !ok {
			return false
		}
		proxy["client-fingerprint"] = fingerprint
	}
	if rawReality, exists := fields["reality"]; exists && !jsonSemanticallyEmpty(rawReality) {
		var reality map[string]json.RawMessage
		if err := json.Unmarshal(rawReality, &reality); err != nil ||
			!onlyAllowedJSONFields(reality, stringSet("enabled", "public_key", "short_id")) {
			return false
		}
		realityEnabled, ok := optionalJSONBool(reality["enabled"])
		if !ok || !realityEnabled {
			return false
		}
		publicKey, ok := requiredJSONText(reality["public_key"])
		if !ok {
			return false
		}
		realityOpts := map[string]any{"public-key": publicKey}
		if shortID, ok := optionalJSONText(reality["short_id"]); ok && shortID != "" {
			realityOpts["short-id"] = shortID
		}
		proxy["reality-opts"] = realityOpts
	}
	return true
}

func applySingBoxTransport(proxy map[string]any, raw json.RawMessage) bool {
	if jsonSemanticallyEmpty(raw) {
		return true
	}
	var fields map[string]json.RawMessage
	if err := json.Unmarshal(raw, &fields); err != nil {
		return false
	}
	transportType, ok := jsonString(fields["type"])
	if !ok {
		return false
	}
	switch strings.ToLower(strings.TrimSpace(transportType)) {
	case "ws":
		if !onlyAllowedJSONFields(fields, stringSet(
			"type", "path", "headers", "max_early_data", "early_data_header_name",
		)) {
			return false
		}
		wsOpts := make(map[string]any)
		if path, ok := optionalJSONText(fields["path"]); ok && path != "" {
			wsOpts["path"] = path
		}
		if headers, ok := singBoxStringHeaders(fields["headers"]); ok && len(headers) > 0 {
			wsOpts["headers"] = headers
		} else if !ok {
			return false
		}
		if maxEarlyData, ok := optionalJSONInt(fields["max_early_data"]); ok {
			if maxEarlyData < 0 {
				return false
			}
			wsOpts["max-early-data"] = maxEarlyData
		}
		if headerName, ok := optionalJSONText(fields["early_data_header_name"]); ok && headerName != "" {
			wsOpts["early-data-header-name"] = headerName
		}
		proxy["network"] = "ws"
		proxy["ws-opts"] = wsOpts
		return true
	case "grpc":
		if !onlyAllowedJSONFields(fields, stringSet("type", "service_name")) {
			return false
		}
		grpcOpts := make(map[string]any)
		if serviceName, ok := optionalJSONText(fields["service_name"]); ok && serviceName != "" {
			grpcOpts["grpc-service-name"] = serviceName
		}
		proxy["network"] = "grpc"
		proxy["grpc-opts"] = grpcOpts
		return true
	case "httpupgrade":
		if !onlyAllowedJSONFields(fields, stringSet("type", "host", "path", "headers")) {
			return false
		}
		headers, ok := singBoxStringHeaders(fields["headers"])
		if !ok {
			return false
		}
		if host, ok := optionalJSONText(fields["host"]); ok && host != "" {
			if existing, exists := headers["Host"]; exists && !strings.EqualFold(existing, host) {
				return false
			}
			headers["Host"] = host
		}
		wsOpts := map[string]any{"v2ray-http-upgrade": true}
		if path, ok := optionalJSONText(fields["path"]); ok && path != "" {
			wsOpts["path"] = path
		}
		if len(headers) > 0 {
			wsOpts["headers"] = headers
		}
		proxy["network"] = "ws"
		proxy["ws-opts"] = wsOpts
		return true
	default:
		return false
	}
}

func singBoxStringHeaders(raw json.RawMessage) (map[string]string, bool) {
	if len(raw) == 0 || jsonSemanticallyEmpty(raw) {
		return map[string]string{}, true
	}
	var headers map[string]string
	if err := json.Unmarshal(raw, &headers); err != nil {
		return nil, false
	}
	for key, value := range headers {
		if strings.TrimSpace(key) == "" || strings.ContainsAny(key, "\r\n") || strings.ContainsAny(value, "\r\n") {
			return nil, false
		}
	}
	return headers, true
}

func onlyAllowedJSONFields(fields map[string]json.RawMessage, allowed map[string]struct{}) bool {
	for key := range fields {
		if _, ok := allowed[key]; !ok {
			return false
		}
	}
	return true
}

func allJSONFieldsEmptyExcept(fields map[string]json.RawMessage, ignored ...string) bool {
	ignoredSet := stringSet(ignored...)
	for key, value := range fields {
		if _, ignored := ignoredSet[key]; ignored {
			continue
		}
		if !jsonSemanticallyEmpty(value) {
			return false
		}
	}
	return true
}

func jsonSemanticallyEmpty(raw json.RawMessage) bool {
	if len(raw) == 0 {
		return true
	}
	trimmed := strings.TrimSpace(string(raw))
	return trimmed == "" || trimmed == "null" || trimmed == "{}" || trimmed == "[]" || trimmed == "false" || trimmed == `""` || trimmed == "0"
}

func jsonString(raw json.RawMessage) (string, bool) {
	if len(raw) == 0 {
		return "", false
	}
	var value string
	if err := json.Unmarshal(raw, &value); err != nil {
		return "", false
	}
	return value, true
}

func jsonInt(raw json.RawMessage) (int, bool) {
	if len(raw) == 0 {
		return 0, false
	}
	var value int
	if err := json.Unmarshal(raw, &value); err != nil {
		return 0, false
	}
	return value, true
}

func optionalJSONInt(raw json.RawMessage) (int, bool) {
	if len(raw) == 0 || string(raw) == "null" {
		return 0, false
	}
	return jsonInt(raw)
}

func optionalJSONBool(raw json.RawMessage) (bool, bool) {
	if len(raw) == 0 || string(raw) == "null" {
		return false, false
	}
	var value bool
	if err := json.Unmarshal(raw, &value); err != nil {
		return false, false
	}
	return value, true
}

func requiredJSONText(raw json.RawMessage) (string, bool) {
	value, ok := jsonString(raw)
	value = strings.TrimSpace(value)
	return value, ok && value != ""
}

func optionalJSONText(raw json.RawMessage) (string, bool) {
	if len(raw) == 0 || string(raw) == "null" {
		return "", false
	}
	value, ok := jsonString(raw)
	return strings.TrimSpace(value), ok
}

func stringSet(values ...string) map[string]struct{} {
	set := make(map[string]struct{}, len(values))
	for _, value := range values {
		set[value] = struct{}{}
	}
	return set
}
