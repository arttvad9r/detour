#!/usr/bin/env bash
# Cross-compiles ByeDPI's ciadpi for Android ABIs and places it into
# app jniLibs as libciadpi.so (executable-from-nativeLibraryDir trick).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CACHE="${BYEDPI_CACHE:-$REPO_ROOT/.cache/byedpi-src}"
OUT_DIR="$REPO_ROOT/app/src/main/jniLibs"
AUTH_TRANSFORM="$REPO_ROOT/engine/byedpi/apply_socks_auth.py"
# ByeByeDPI reference snapshot 01b080e2fe41898d8371495a9db887da54e28798
# uses this exact post-v0.17.3 upstream revision for its proxy-test corpus.
BYEDPI_COMMIT="ba532298de7b28cfe854aea83d061369d13ca290"

[[ -n "${ANDROID_NDK_HOME:-}" ]] || { echo "ANDROID_NDK_HOME is required (NDK 28.0.13004108)" >&2; exit 127; }

if [[ ! -d "$CACHE/.git" ]]; then
  rm -rf "$CACHE"
  git clone --filter=blob:none --no-checkout https://github.com/hufrea/byedpi.git "$CACHE"
fi
# Fetch the exact reviewed revision instead of relying on a moving branch/tag.
git -C "$CACHE" fetch --depth 1 origin "$BYEDPI_COMMIT"
git -C "$CACHE" reset --hard "$BYEDPI_COMMIT"
git -C "$CACHE" clean -fdx
ACTUAL_COMMIT="$(git -C "$CACHE" rev-parse HEAD)"
[[ "$ACTUAL_COMMIT" == "$BYEDPI_COMMIT" ]] || {
  echo "unexpected ByeDPI source: $ACTUAL_COMMIT" >&2
  exit 1
}
python3 "$AUTH_TRANSFORM" "$CACHE"
git -C "$CACHE" diff --check
grep -q -- "--socks5-auth-stdin" "$CACHE/main.c"
grep -q "S_AUTH_USERPASS" "$CACHE/proxy.h"
grep -q "FLAG_S5_AUTH" "$CACHE/conev.h"
grep -q "auth_socks5_userpass" "$CACHE/proxy.c"
echo "detour socks auth transform applied to $BYEDPI_COMMIT"

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
