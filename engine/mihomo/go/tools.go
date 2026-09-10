//go:build tools

package engine

// Keep gomobile in the module graph so `go mod vendor` captures the exact
// tool source and all of its dependencies for offline/F-Droid builds.
import _ "golang.org/x/mobile/cmd/gomobile"
