//go:build tools

package engine

// Keep gomobile and gobind in the module graph so `go mod vendor` captures
// the exact tool sources and all dependencies for offline/F-Droid builds.
import (
    _ "golang.org/x/mobile/cmd/gobind"
    _ "golang.org/x/mobile/cmd/gomobile"
)
