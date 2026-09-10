#!/usr/bin/env bash
# Build Detour's Mihomo Android AAR without gomobile's temporary module resolver.
# The generated gobind package is compiled inside the existing vendored module.
set -euo pipefail

WORK_DIR="${1:?usage: bind-offline.sh WORK_DIR GOBIND_BIN}"
GOBIND_BIN="${2:?usage: bind-offline.sh WORK_DIR GOBIND_BIN}"
ANDROID_API="${DETOUR_ANDROID_API:-24}"
NDK_ROOT="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"

[[ -d "$WORK_DIR/vendor" ]] || {
  echo "FATAL: vendored Go graph missing from $WORK_DIR" >&2
  exit 2
}
[[ -x "$GOBIND_BIN" ]] || {
  echo "FATAL: gobind binary missing: $GOBIND_BIN" >&2
  exit 2
}
[[ -n "$NDK_ROOT" && -d "$NDK_ROOT/toolchains/llvm/prebuilt" ]] || {
  echo "FATAL: Android NDK is not configured" >&2
  exit 2
}
[[ -n "$SDK_ROOT" && -d "$SDK_ROOT/platforms" ]] || {
  echo "FATAL: Android SDK is not configured" >&2
  exit 2
}

HOST_PREBUILT="$(find "$NDK_ROOT/toolchains/llvm/prebuilt" -mindepth 1 -maxdepth 1 -type d -print -quit)"
[[ -n "$HOST_PREBUILT" ]] || {
  echo "FATAL: NDK LLVM prebuilt toolchain not found" >&2
  exit 2
}

ANDROID_JAR="$(find "$SDK_ROOT/platforms" -mindepth 2 -maxdepth 2 -name android.jar -print | sort -V | tail -n 1)"
[[ -f "$ANDROID_JAR" ]] || {
  echo "FATAL: Android platform android.jar not found" >&2
  exit 2
}

GEN_DIR="$WORK_DIR/detour-gobind-build"
GO_BIND_DIR="$WORK_DIR/detour_gobind"
JNI_DIR="$GEN_DIR/jni"
JAVA_CLASSES="$GEN_DIR/classes"
AAR="$WORK_DIR/engine.aar"
rm -rf "$GEN_DIR" "$GO_BIND_DIR" "$AAR" "$WORK_DIR/engine-sources.jar"
mkdir -p "$GEN_DIR" "$GO_BIND_DIR" "$JNI_DIR" "$JAVA_CLASSES"
trap 'rm -rf "$GEN_DIR" "$GO_BIND_DIR"' EXIT

# Generate Go + Java bridge sources while resolving the application package
# through the already-vendored module. No module download is permitted.
(
  cd "$WORK_DIR"
  env \
    GOOS=android \
    CGO_ENABLED=1 \
    GOPROXY=off \
    GOSUMDB=off \
    GOTOOLCHAIN=local \
    GOFLAGS='-mod=vendor' \
    "$GOBIND_BIN" \
      -lang=go,java \
      -outdir="$GEN_DIR" \
      -javapkg=dev.detour.engine \
      -tags=with_gvisor \
      .
)

[[ -d "$GEN_DIR/src/gobind" ]] || {
  echo "FATAL: gobind did not generate Go bridge sources" >&2
  exit 1
}
cp -R "$GEN_DIR/src/gobind/." "$GO_BIND_DIR/"

build_so() {
  local goarch="$1"
  local abi="$2"
  local clang_prefix="$3"
  local cc="$HOST_PREBUILT/bin/${clang_prefix}${ANDROID_API}-clang"
  local cxx="$HOST_PREBUILT/bin/${clang_prefix}${ANDROID_API}-clang++"
  local out="$JNI_DIR/$abi/libgojni.so"

  [[ -x "$cc" && -x "$cxx" ]] || {
    echo "FATAL: NDK compiler wrappers missing for $abi" >&2
    exit 2
  }
  mkdir -p "$(dirname "$out")"
  (
    cd "$WORK_DIR"
    env \
      GOOS=android \
      GOARCH="$goarch" \
      CGO_ENABLED=1 \
      CC="$cc" \
      CXX="$cxx" \
      GOPROXY=off \
      GOSUMDB=off \
      GOTOOLCHAIN=local \
      GOFLAGS='-mod=vendor -tags=with_gvisor' \
      go build \
        -trimpath \
        -buildvcs=false \
        -buildmode=c-shared \
        -o="$out" \
        ./detour_gobind
  )
}

build_so arm64 arm64-v8a aarch64-linux-android
build_so amd64 x86_64 x86_64-linux-android

mapfile -t JAVA_SOURCES < <(find "$GEN_DIR/java" -type f -name '*.java' -print | sort)
(( ${#JAVA_SOURCES[@]} > 0 )) || {
  echo "FATAL: gobind did not generate Java bridge sources" >&2
  exit 1
}

javac \
  -encoding UTF-8 \
  -source 8 \
  -target 8 \
  -bootclasspath "$ANDROID_JAR" \
  -d "$JAVA_CLASSES" \
  "${JAVA_SOURCES[@]}"
jar --create --file "$GEN_DIR/classes.jar" -C "$JAVA_CLASSES" .

cat > "$GEN_DIR/AndroidManifest.xml" <<EOF
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="go.engine.gojni">
    <uses-sdk android:minSdkVersion="$ANDROID_API" />
</manifest>
EOF
cat > "$GEN_DIR/proguard.txt" <<'EOF'
-keep class go.** { *; }
-keep class dev.detour.engine.** { *; }
EOF
: > "$GEN_DIR/R.txt"
mkdir -p "$GEN_DIR/res"

# AAR is a regular ZIP. Python's zipfile lets us emit a stable file order and
# timestamps, avoiding host clock noise in this native intermediate artifact.
python3 - "$GEN_DIR" "$AAR" <<'PYEOF'
from pathlib import Path
import sys
import zipfile

root = Path(sys.argv[1])
out = Path(sys.argv[2])
fixed = (1980, 1, 1, 0, 0, 0)
entries = [
    (root / "AndroidManifest.xml", "AndroidManifest.xml"),
    (root / "classes.jar", "classes.jar"),
    (root / "proguard.txt", "proguard.txt"),
    (root / "R.txt", "R.txt"),
]
for so in sorted((root / "jni").glob("*/*.so")):
    entries.append((so, so.relative_to(root).as_posix()))

with zipfile.ZipFile(out, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
    for path, name in entries:
        info = zipfile.ZipInfo(name, fixed)
        info.compress_type = zipfile.ZIP_DEFLATED
        info.external_attr = 0o100644 << 16
        zf.writestr(info, path.read_bytes())
    info = zipfile.ZipInfo("res/", fixed)
    info.external_attr = (0o40755 << 16) | 0x10
    zf.writestr(info, b"")
PYEOF

for abi in arm64-v8a x86_64; do
  unzip -t "$AAR" "jni/$abi/libgojni.so" >/dev/null
  [[ -s "$JNI_DIR/$abi/libgojni.so" ]] || {
    echo "FATAL: empty JNI library for $abi" >&2
    exit 1
  }
done
unzip -t "$AAR" classes.jar AndroidManifest.xml >/dev/null

echo "offline AAR: $AAR ($(du -h "$AAR" | cut -f1))"
