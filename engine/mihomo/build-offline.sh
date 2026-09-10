#!/usr/bin/env bash
# Offline/reproducible wrapper for Detour's Mihomo AAR build.
# All third-party source comes from pinned git submodules or the committed Go vendor tree.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MIHOMO_COMMIT="ac017cdd246ce8bd547653d927e7bf77d7ee73d5"
MIHOMO_TAG="v1.19.30"
SOURCE="$REPO_ROOT/third_party/mihomo"
BIND_DIR="$REPO_ROOT/engine/mihomo/go"
CACHE_ROOT="${DETOUR_NATIVE_CACHE:-$REPO_ROOT/.cache/offline-native}"
LOCAL_ORIGIN="$CACHE_ROOT/mihomo-origin"
BUILD_CACHE="$CACHE_ROOT/mihomo-build"
TOOL_BIN="$CACHE_ROOT/bin"
ORIGINAL="$REPO_ROOT/engine/mihomo/build.sh"
PATCHED="$REPO_ROOT/engine/mihomo/.build-offline.generated.sh"

[[ -d "$SOURCE" ]] || {
  echo "Mihomo submodule is missing. Run: git submodule update --init --recursive" >&2
  exit 2
}
[[ -f "$BIND_DIR/vendor/modules.txt" ]] || {
  echo "Go vendor tree is missing: $BIND_DIR/vendor/modules.txt" >&2
  exit 2
}

actual_commit="$(git -C "$SOURCE" rev-parse HEAD)"
[[ "$actual_commit" == "$MIHOMO_COMMIT" ]] || {
  echo "Unexpected Mihomo submodule revision: $actual_commit" >&2
  echo "Expected: $MIHOMO_COMMIT" >&2
  exit 2
}

mkdir -p "$CACHE_ROOT"
rm -rf "$LOCAL_ORIGIN" "$BUILD_CACHE" "$TOOL_BIN" "$PATCHED"

# Build the legacy patch script against a local-only git remote. It still uses
# git fetch/reset internally, but every fetch below resolves to this filesystem.
git clone --no-hardlinks "$SOURCE" "$LOCAL_ORIGIN" >/dev/null
git -C "$LOCAL_ORIGIN" checkout --detach "$MIHOMO_COMMIT" >/dev/null
git -C "$LOCAL_ORIGIN" tag -f "$MIHOMO_TAG" "$MIHOMO_COMMIT"
git clone --no-hardlinks "$LOCAL_ORIGIN" "$BUILD_CACHE" >/dev/null
git -C "$BUILD_CACHE" remote set-url origin "$LOCAL_ORIGIN"

# Build the exact gomobile/gobind versions from the committed vendor graph.
# This happens before network is needed and with the module proxy disabled.
mkdir -p "$TOOL_BIN"
(
  cd "$BIND_DIR"
  env \
    GOBIN="$TOOL_BIN" \
    GOPROXY=off \
    GOSUMDB=off \
    GOFLAGS='-mod=vendor' \
    go install golang.org/x/mobile/cmd/gomobile golang.org/x/mobile/cmd/gobind
)
export PATH="$TOOL_BIN:$PATH"
export GOPROXY=off
export GOSUMDB=off

# Reuse the reviewed Detour patch sequence. For the binding stage, materialize
# the committed vendor graph as a temporary GOPATH and replace only Mihomo with
# the exact locally patched checkout. GOPATH mode makes gomobile skip its
# generated-module `go mod tidy`, which would otherwise attempt module
# resolution even though every source package is already present locally.
python3 - "$ORIGINAL" "$PATCHED" <<'PYEOF'
from pathlib import Path
import sys

src = Path(sys.argv[1])
dst = Path(sys.argv[2])
s = src.read_text()

start_anchor = 'cp "$BIND_DIR/go.mod" "$WORK_DIR/go.mod"'
end_anchor = 'gomobile bind -target android/arm64,android/amd64 -androidapi 24 -javapkg=dev.detour.engine .'
start = s.find(start_anchor)
end = s.find(end_anchor)
if start < 0 or end < 0 or end < start:
    raise SystemExit('FATAL: Mihomo build script binding anchors changed')
end += len(end_anchor)

gopath_block = r'''GOPATH_DIR="$WORK_DIR/gopath"
ENGINE_DIR="$GOPATH_DIR/src/engine"
mkdir -p "$GOPATH_DIR/src" "$ENGINE_DIR"
cp -R "$BIND_DIR/vendor/." "$GOPATH_DIR/src/"
cp "$BIND_DIR"/*.go "$ENGINE_DIR/"
PATCHED_MIHOMO="$GOPATH_DIR/src/github.com/metacubex/mihomo"
rm -rf "$PATCHED_MIHOMO"
mkdir -p "$(dirname "$PATCHED_MIHOMO")"
cp -R "$CACHE" "$PATCHED_MIHOMO"
rm -rf "$PATCHED_MIHOMO/.git"

cd "$ENGINE_DIR"
export GO111MODULE=off
export GOPATH="$GOPATH_DIR"
unset GOFLAGS
go test -tags=with_gvisor ./...
gomobile bind -tags=with_gvisor -target android/arm64,android/amd64 -androidapi 24 -javapkg=dev.detour.engine .'''

s = s[:start] + gopath_block + s[end:]
dst.write_text(s)
PYEOF
chmod +x "$PATCHED"
trap 'rm -f "$PATCHED"' EXIT

MIHOMO_CACHE="$BUILD_CACHE" bash "$PATCHED"

# Preserve the legacy cache path used by the CI race-detector step. The target
# itself is the already-patched local checkout built above; no network fetches
# are involved.
rm -rf "$REPO_ROOT/.cache/mihomo-src"
ln -s "$BUILD_CACHE" "$REPO_ROOT/.cache/mihomo-src"
