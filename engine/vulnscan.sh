#!/usr/bin/env bash
# Engine verification over the real dependency set (pinned mihomo + Triplet patches).
# Run after :buildMihomoAar so .cache/mihomo-src and engine.aar match the build.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CACHE="$REPO_ROOT/.cache/mihomo-src"
AAR="$REPO_ROOT/engine/libs/engine.aar"

[[ -d "$CACHE" ]] || { echo "run :buildMihomoAar first (need patched $CACHE)" >&2; exit 1; }
[[ -f "$AAR" ]] || { echo "run :buildMihomoAar first (need $AAR)" >&2; exit 1; }

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

cp "$REPO_ROOT/engine/mihomo/go/go.mod" "$WORK_DIR/"
cp "$REPO_ROOT/engine/mihomo/go/go.sum" "$WORK_DIR/"
# Test the same Go package source set that gomobile binds. This must include
# auxiliary bridge files such as subscription_prepare.go, not only engine.go.
cp "$REPO_ROOT/engine/mihomo/go"/*.go "$WORK_DIR/"
cd "$WORK_DIR"
sed -i "s|^replace github.com/metacubex/mihomo => .*|replace github.com/metacubex/mihomo => $CACHE|" go.mod
grep -q '^replace github.com/metacubex/mihomo' go.mod || \
  echo "replace github.com/metacubex/mihomo => $CACHE" >> go.mod

export GOFLAGS=-mod=mod
# Exercise the checked-in engine tests against the exact patched source tree and
# the same gVisor feature set used by the Android AAR.
echo "go test: with_gvisor"
go test -tags with_gvisor ./...

# Source mode gives call-graph precision. Match the production feature tag;
# platform/architecture differences are covered below by scanning the actual
# Android shared libraries produced by gomobile.
echo "govulncheck source: with_gvisor"
govulncheck -tags with_gvisor ./...

# Binary mode reads GOOS, GOARCH, Go version, modules and linked symbols from
# each shipped libgojni.so, so this validates the exact Android build outputs.
mapfile -t go_libs < <(unzip -Z1 "$AAR" | grep -E '^jni/[^/]+/libgojni\.so$')
if (( ${#go_libs[@]} == 0 )); then
  echo "FATAL: engine.aar contains no libgojni.so" >&2
  exit 1
fi
for entry in "${go_libs[@]}"; do
  extracted="$WORK_DIR/$(basename "$(dirname "$entry")")-libgojni.so"
  unzip -p "$AAR" "$entry" > "$extracted"
  echo "govulncheck binary: $entry"
  govulncheck -mode binary "$extracted"
done
