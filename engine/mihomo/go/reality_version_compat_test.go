package engine

import (
	"testing"

	mihomoTLS "github.com/metacubex/mihomo/component/tls"
)

func TestEmbeddedRealityClientVersionMatchesModernXrayMinimum(t *testing.T) {
	got := [3]byte{
		mihomoTLS.RealityClientVersionMajor,
		mihomoTLS.RealityClientVersionMinor,
		mihomoTLS.RealityClientVersionPatch,
	}
	want := [3]byte{26, 3, 27}
	if got != want {
		t.Fatalf("REALITY client version = %d.%d.%d, want %d.%d.%d", got[0], got[1], got[2], want[0], want[1], want[2])
	}
}
