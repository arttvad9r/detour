#!/usr/bin/env bash
# Cross-compiles byedpi's ciadpi for Android ABIs and places it into
# app jniLibs as libciadpi.so (executable-from-nativeLibraryDir trick).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CACHE="${BYEDPI_CACHE:-$REPO_ROOT/.cache/byedpi-src}"
OUT_DIR="$REPO_ROOT/app/src/main/jniLibs"

[[ -n "${ANDROID_NDK_HOME:-}" ]] || { echo "run inside nix-shell (NDK required)" >&2; exit 127; }

# Pin: последний стабильный тег на момент сборки, фиксируется в docs/pins.md.
TAG="${BYEDPI_TAG:-$(git ls-remote --tags https://github.com/hufrea/byedpi.git 'v*' \
  | awk -F/ '{print $NF}' | grep -vi '{}' | sort -V | tail -1)}"
echo "byedpi tag: $TAG"

if [[ ! -d "$CACHE/.git" ]]; then
  mkdir -p "$CACHE"
  git clone --depth 1 --branch "$TAG" https://github.com/hufrea/byedpi.git "$CACHE"
fi

TC="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin"
declare -A CLANG=(
  [arm64-v8a]="aarch64-linux-android21-clang"
  [x86_64]="x86_64-linux-android21-clang"
)

for abi in arm64-v8a x86_64; do
  make -C "$CACHE" clean >/dev/null 2>&1 || true
  # Dynamic linking: a static bionic binary cannot resolve DNS (getaddrinfo
  # requires libnetd_client, unavailable in static executables).
  make -C "$CACHE" -j"$(nproc)" CC="$TC/${CLANG[$abi]}" LDFLAGS="" >/dev/null
  mkdir -p "$OUT_DIR/$abi"
  cp "$CACHE/ciadpi" "$OUT_DIR/$abi/libciadpi.so"
  chmod +x "$OUT_DIR/$abi/libciadpi.so"
  echo "$abi -> $(file "$OUT_DIR/$abi/libciadpi.so" | cut -d: -f2)"
done
echo "Done: $OUT_DIR"
