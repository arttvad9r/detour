package engine

import (
	"encoding/json"

	"github.com/metacubex/mihomo/tunnel/statistic"
)

type trafficSnapshot struct {
	UploadBytesPerSecond   int64 `json:"uploadBytesPerSecond"`
	DownloadBytesPerSecond int64 `json:"downloadBytesPerSecond"`
	UploadedBytes          int64 `json:"uploadedBytes"`
	DownloadedBytes        int64 `json:"downloadedBytes"`
}

// ResetTrafficStats starts a new user-visible traffic session. Android calls
// this only after Start has successfully created and validated the TUN runtime,
// so provider/bootstrap traffic is not counted in the connected-session UI.
func ResetTrafficStats() {
	statistic.DefaultManager.ResetStatistic()
}

// TrafficStats returns the same counters used by mihomo's own statistics
// manager. The *PerSecond fields are the bytes observed in the last one-second
// sampling window; totals are accumulated since ResetTrafficStats.
func TrafficStats() string {
	if !Ready() {
		return ""
	}
	uploadNow, downloadNow := statistic.DefaultManager.Now()
	uploadTotal, downloadTotal := statistic.DefaultManager.Total()
	payload, err := json.Marshal(trafficSnapshot{
		UploadBytesPerSecond:    nonNegativeTraffic(uploadNow),
		DownloadBytesPerSecond: nonNegativeTraffic(downloadNow),
		UploadedBytes:           nonNegativeTraffic(uploadTotal),
		DownloadedBytes:         nonNegativeTraffic(downloadTotal),
	})
	if err != nil {
		return ""
	}
	return string(payload)
}

func nonNegativeTraffic(value int64) int64 {
	if value < 0 {
		return 0
	}
	return value
}
