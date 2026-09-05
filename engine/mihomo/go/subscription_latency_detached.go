package engine

// TestSubscriptionCatalogLatencyDetached tests independently parsed subscription
// proxies instead of borrowing live provider objects. User-triggered latency scans
// can therefore never hold the global runtime lifecycle mutex or delay Start/Stop.
// The parsing/normalization path is identical to the materialized runtime provider.
func TestSubscriptionCatalogLatencyDetached(subscriptionURL string) string {
	proxies, err := fetchPreparedSubscriptionProxies(subscriptionURL)
	if err != nil || len(proxies) == 0 {
		return ""
	}
	return marshalSubscriptionLatencyResults(
		runSubscriptionLatencyTests(proxies, mihomoSubscriptionLatencyProbe),
	)
}
