package engine

import (
	"sync"
	"testing"

	"github.com/metacubex/mihomo/log"
)

func TestMihomoLogLevelIsRaceSafe(t *testing.T) {
	original := log.Level()
	t.Cleanup(func() { log.SetLevel(original) })

	levels := []log.LogLevel{log.DEBUG, log.INFO, log.WARNING, log.ERROR, log.SILENT}
	var wg sync.WaitGroup
	for worker := 0; worker < 8; worker++ {
		wg.Add(1)
		go func(offset int) {
			defer wg.Done()
			for i := 0; i < 1_000; i++ {
				log.SetLevel(levels[(i+offset)%len(levels)])
				_ = log.Level()
			}
		}(worker)
	}
	wg.Wait()
}
