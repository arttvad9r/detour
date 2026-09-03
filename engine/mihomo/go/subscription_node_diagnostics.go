package engine

// SubscriptionNodeDiagnostics is retained for binary/API compatibility with
// older Android code. Runtime proxy-configuration logging was temporary and is
// intentionally disabled.
func SubscriptionNodeDiagnostics(homeDir string, nodeName string) string {
	return ""
}
