package engine

import (
	"encoding/json"

	"github.com/metacubex/mihomo/log"
)

// TestSubscriptionCatalogLatencyDetailed keeps the existing latency behavior but
// emits the sanitized source mapping for every offline-tested subscription node.
// This makes manual diagnostics independent of UI selection automation while
// keeping credentials out of logcat. When the engine is active, it preserves the
// existing live-provider path and does not invent mapping details that are not
// available from the runtime adapter objects.
func TestSubscriptionCatalogLatencyDetailed(subscriptionURL string) string {
	if results, ok := activeSubscriptionLatencyResults(); ok {
		return marshalSubscriptionLatencyResults(results)
	}

	proxies, err := fetchPreparedSubscriptionProxies(subscriptionURL)
	if err != nil || len(proxies) == 0 {
		return ""
	}
	for _, proxy := range proxies {
		payload, marshalErr := json.Marshal(sanitizedSubscriptionNodeDiagnostics(proxy))
		if marshalErr != nil {
			continue
		}
		log.Infoln("[DETOUR_PROXY_CONFIG] stage=latency source=%s", string(payload))
	}
	return marshalSubscriptionLatencyResults(runSubscriptionLatencyTests(proxies, mihomoSubscriptionLatencyProbe))
}
