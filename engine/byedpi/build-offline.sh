#!/usr/bin/env bash
# Offline/reproducible wrapper for Detour's native ByeDPI build.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BYEDPI_COMMIT="ba532298de7b28cfe854aea83d061369d13ca290"
SOURCE="$REPO_ROOT/third_party/byedpi"
CACHE_ROOT="${DETOUR_NATIVE_CACHE:-$REPO_ROOT/.cache/offline-native}"
LOCAL_ORIGIN="$CACHE_ROOT/byedpi-origin"
BUILD_CACHE="$CACHE_ROOT/byedpi-build"

[[ -d "$SOURCE" ]] || {
  echo "ByeDPI submodule is missing. Run: git submodule update --init --recursive" >&2
  exit 2
}

actual_commit="$(git -C "$SOURCE" rev-parse HEAD)"
[[ "$actual_commit" == "$BYEDPI_COMMIT" ]] || {
  echo "Unexpected ByeDPI submodule revision: $actual_commit" >&2
  echo "Expected: $BYEDPI_COMMIT" >&2
  exit 2
}

mkdir -p "$CACHE_ROOT"
rm -rf "$LOCAL_ORIGIN" "$BUILD_CACHE"

# The existing build script retains its exact commit verification and Detour
# source transform, but its origin is a local filesystem clone rather than the
# public network.
git clone --no-hardlinks "$SOURCE" "$LOCAL_ORIGIN" >/dev/null
git -C "$LOCAL_ORIGIN" checkout --detach "$BYEDPI_COMMIT" >/dev/null
git clone --no-hardlinks "$LOCAL_ORIGIN" "$BUILD_CACHE" >/dev/null
git -C "$BUILD_CACHE" remote set-url origin "$LOCAL_ORIGIN"

BYEDPI_CACHE="$BUILD_CACHE" bash "$REPO_ROOT/engine/byedpi/build.sh"
