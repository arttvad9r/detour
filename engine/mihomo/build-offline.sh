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

# Build the exact gomobile/gobind versions from the vendored source graph.
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

# Reuse the reviewed Detour patch sequence, but make its Go compilation consume
# the committed vendor tree. The patched Mihomo checkout replaces the pristine
# vendored Mihomo copy before compilation, so no module download is required.
python3 - "$ORIGINAL" "$PATCHED" <<'PYEOF'
from pathlib import Path
import sys

src = Path(sys.argv[1])
dst = Path(sys.argv[2])
s = src.read_text()

copy_anchor = 'cp "$BIND_DIR/go.sum" "$WORK_DIR/go.sum"\ncp "$BIND_DIR"/*.go "$WORK_DIR"/'
copy_replacement = '''cp "$BIND_DIR/go.sum" "$WORK_DIR/go.sum"
cp -R "$BIND_DIR/vendor" "$WORK_DIR/vendor"
cp "$BIND_DIR"/*.go "$WORK_DIR"/
VENDORED_MIHOMO="$WORK_DIR/vendor/github.com/metacubex/mihomo"
rm -rf "$VENDORED_MIHOMO"
mkdir -p "$(dirname "$VENDORED_MIHOMO")"
cp -R "$CACHE" "$VENDORED_MIHOMO"
rm -rf "$VENDORED_MIHOMO/.git"'''
if copy_anchor not in s:
    raise SystemExit('FATAL: Mihomo build script copy anchor changed')
s = s.replace(copy_anchor, copy_replacement, 1)

replace_anchor = "line = 'replace github.com/metacubex/mihomo => $CACHE'"
if replace_anchor not in s:
    raise SystemExit('FATAL: Mihomo build script replace anchor changed')
s = s.replace(replace_anchor, "line = ''", 1)

flags_anchor = 'export GOFLAGS="-mod=mod -tags=with_gvisor"'
if flags_anchor not in s:
    raise SystemExit('FATAL: Mihomo build script GOFLAGS anchor changed')
s = s.replace(flags_anchor, 'export GOFLAGS="-mod=vendor -tags=with_gvisor"', 1)

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
