package engine

// TestSubscriptionCatalogLatencyDetailed is kept as a compatibility alias for
// older Android bindings. It no longer emits per-node configuration diagnostics.
func TestSubscriptionCatalogLatencyDetailed(subscriptionURL string) string {
	return TestSubscriptionCatalogLatency(subscriptionURL)
}
