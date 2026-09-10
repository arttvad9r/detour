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
MOBILE_BIND_ANDROID="$BIND_DIR/vendor/golang.org/x/mobile/cmd/gomobile/bind_androidapp.go"
MOBILE_BIND_COMMON="$BIND_DIR/vendor/golang.org/x/mobile/cmd/gomobile/bind.go"
MOBILE_BIND_ANDROID_BACKUP="$CACHE_ROOT/bind_androidapp.go.orig"
MOBILE_BIND_COMMON_BACKUP="$CACHE_ROOT/bind.go.orig"

[[ -d "$SOURCE" ]] || {
  echo "Mihomo submodule is missing. Run: git submodule update --init --recursive" >&2
  exit 2
}
[[ -f "$BIND_DIR/vendor/modules.txt" ]] || {
  echo "Go vendor tree is missing: $BIND_DIR/vendor/modules.txt" >&2
  exit 2
}
for source in "$MOBILE_BIND_ANDROID" "$MOBILE_BIND_COMMON"; do
  [[ -f "$source" ]] || {
    echo "Vendored gomobile source is missing: $source" >&2
    exit 2
  }
done

actual_commit="$(git -C "$SOURCE" rev-parse HEAD)"
[[ "$actual_commit" == "$MIHOMO_COMMIT" ]] || {
  echo "Unexpected Mihomo submodule revision: $actual_commit" >&2
  echo "Expected: $MIHOMO_COMMIT" >&2
  exit 2
}

mkdir -p "$CACHE_ROOT"
rm -rf \
  "$LOCAL_ORIGIN" "$BUILD_CACHE" "$TOOL_BIN" "$PATCHED" \
  "$MOBILE_BIND_ANDROID_BACKUP" "$MOBILE_BIND_COMMON_BACKUP"

# Build the legacy patch script against a local-only git remote. It still uses
# git fetch/reset internally, but every fetch below resolves to this filesystem.
git clone --no-hardlinks "$SOURCE" "$LOCAL_ORIGIN" >/dev/null
git -C "$LOCAL_ORIGIN" checkout --detach "$MIHOMO_COMMIT" >/dev/null
git -C "$LOCAL_ORIGIN" tag -f "$MIHOMO_TAG" "$MIHOMO_COMMIT"
git clone --no-hardlinks "$LOCAL_ORIGIN" "$BUILD_CACHE" >/dev/null
git -C "$BUILD_CACHE" remote set-url origin "$LOCAL_ORIGIN"

# gomobile normally creates a temporary Android module and runs `go mod tidy`
# inside it. That is correct for online development builds but defeats a strict
# offline/F-Droid build even when the complete dependency graph is committed.
# Build a local gomobile binary with a narrow offline mode:
# - generated Android modules receive the committed vendor tree;
# - `go mod tidy` is skipped;
# - module-graph probe errors become fatal instead of silently producing an
#   empty go.mod. This gives deterministic diagnostics for the pinned graph.
cp "$MOBILE_BIND_ANDROID" "$MOBILE_BIND_ANDROID_BACKUP"
cp "$MOBILE_BIND_COMMON" "$MOBILE_BIND_COMMON_BACKUP"
restore_mobile_source() {
  if [[ -f "$MOBILE_BIND_ANDROID_BACKUP" ]]; then
    cp "$MOBILE_BIND_ANDROID_BACKUP" "$MOBILE_BIND_ANDROID"
    rm -f "$MOBILE_BIND_ANDROID_BACKUP"
  fi
  if [[ -f "$MOBILE_BIND_COMMON_BACKUP" ]]; then
    cp "$MOBILE_BIND_COMMON_BACKUP" "$MOBILE_BIND_COMMON"
    rm -f "$MOBILE_BIND_COMMON_BACKUP"
  fi
}
trap restore_mobile_source EXIT

python3 - "$MOBILE_BIND_ANDROID" <<'PYEOF'
from pathlib import Path
import sys

p = Path(sys.argv[1])
s = p.read_text()
old = '''\t\tif err := writeGoMod(srcDir, "android", arch); err != nil {\n\t\t\treturn err\n\t\t}\n\n\t\t// Run `go mod tidy` to force to create go.sum.\n\t\t// Without go.sum, `go build` fails as of Go 1.16.\n\t\tif err := goModTidyAt(srcDir, env); err != nil {\n\t\t\treturn err\n\t\t}\n'''
new = '''\t\tif err := writeGoMod(srcDir, "android", arch); err != nil {\n\t\t\treturn err\n\t\t}\n\n\t\tif vendorDir := os.Getenv("DETOUR_GOMOBILE_VENDOR"); vendorDir != "" {\n\t\t\tif !buildN {\n\t\t\t\tif err := doCopyAll(filepath.Join(srcDir, "vendor"), vendorDir); err != nil {\n\t\t\t\t\treturn err\n\t\t\t\t}\n\t\t\t}\n\t\t} else {\n\t\t\t// Preserve normal upstream behavior outside Detour's offline build.\n\t\t\tif err := goModTidyAt(srcDir, env); err != nil {\n\t\t\t\treturn err\n\t\t\t}\n\t\t}\n'''
if old not in s:
    raise SystemExit('FATAL: gomobile Android module layout changed')
p.write_text(s.replace(old, new, 1))
PYEOF

python3 - "$MOBILE_BIND_COMMON" <<'PYEOF'
from pathlib import Path
import sys

p = Path(sys.argv[1])
s = p.read_text()
old = '''\toutput, err := cmd.Output()\n\tif err != nil {\n\t\t// Module information is not available at src.\n\t\treturn nil, nil\n\t}\n'''
new = '''\toutput, err := cmd.CombinedOutput()\n\tif err != nil {\n\t\tif os.Getenv("DETOUR_GOMOBILE_VENDOR") != "" {\n\t\t\treturn nil, fmt.Errorf("offline module graph probe failed: %w: %s", err, strings.TrimSpace(string(output)))\n\t\t}\n\t\t// Preserve normal upstream behavior outside Detour's offline build.\n\t\treturn nil, nil\n\t}\n'''
if old not in s:
    raise SystemExit('FATAL: gomobile module graph probe layout changed')
p.write_text(s.replace(old, new, 1))
PYEOF

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
restore_mobile_source
trap - EXIT

export PATH="$TOOL_BIN:$PATH"
export GOPROXY=off
export GOSUMDB=off

# Reuse the reviewed Detour patch sequence, but make both the package tests and
# gomobile's generated Android modules consume the committed vendor tree. The
# pristine Mihomo copy in vendor is replaced by the exact locally patched source
# before compilation. No module download or remote git fetch is needed.
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

bind_anchor = 'gomobile bind -target android/arm64,android/amd64 -androidapi 24 -javapkg=dev.detour.engine .'
if bind_anchor not in s:
    raise SystemExit('FATAL: Mihomo gomobile bind anchor changed')
s = s.replace(
    bind_anchor,
    'export DETOUR_GOMOBILE_VENDOR="$WORK_DIR/vendor"\n' + bind_anchor,
    1,
)

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
