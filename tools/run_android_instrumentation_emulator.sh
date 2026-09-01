#!/usr/bin/env bash
set -euo pipefail

API_LEVEL="${1:?usage: run_android_instrumentation_emulator.sh <api-level> [system-image] [expected-page-size]}"
SYSTEM_IMAGE="${2:-system-images;android-${API_LEVEL};google_apis;x86_64}"
EXPECTED_PAGE_SIZE="${3:-}"
AVD_NAME="detour-ci-api${API_LEVEL}"
EMULATOR="$ANDROID_SDK_ROOT/emulator/emulator"
EMULATOR_SERIAL="emulator-5554"
LOG_PREFIX="$RUNNER_TEMP/detour-api${API_LEVEL}"
EMULATOR_LOG="${LOG_PREFIX}-emulator.log"
INSTRUMENTATION_LOG="${LOG_PREFIX}-instrumentation.log"
LOGCAT_LOG="${LOG_PREFIX}-logcat.txt"
export ANDROID_USER_HOME="$RUNNER_TEMP/android-user-home-api${API_LEVEL}"
export ANDROID_AVD_HOME="$ANDROID_USER_HOME/avd"
mkdir -p "$ANDROID_AVD_HOME"

if [[ ! -x "$EMULATOR" ]]; then
  echo "Android Emulator binary not found: $EMULATOR" >&2
  exit 1
fi
if [[ -e /dev/kvm ]]; then
  sudo chmod 666 /dev/kvm
fi
"$EMULATOR" -accel-check || true

echo "no" | avdmanager create avd \
  --force \
  --name "$AVD_NAME" \
  --package "$SYSTEM_IMAGE"

if ! "$EMULATOR" -list-avds | grep -Fxq "$AVD_NAME"; then
  echo "Created AVD is not visible to Android Emulator" >&2
  find "$ANDROID_USER_HOME" -maxdepth 3 -type f -print >&2 || true
  exit 1
fi

cleanup() {
  set +e
  adb -s "$EMULATOR_SERIAL" emu kill >/dev/null 2>&1 || true
  if [[ -n "${EMULATOR_PID:-}" ]]; then
    for _ in $(seq 1 20); do
      if ! kill -0 "$EMULATOR_PID" 2>/dev/null; then
        break
      fi
      sleep 1
    done
    kill "$EMULATOR_PID" >/dev/null 2>&1 || true
    wait "$EMULATOR_PID" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

"$EMULATOR" "@$AVD_NAME" \
  -port 5554 \
  -no-window \
  -no-audio \
  -no-boot-anim \
  -no-snapshot \
  -gpu swiftshader_indirect \
  -accel auto \
  > "$EMULATOR_LOG" 2>&1 &
EMULATOR_PID=$!

adb start-server
device_ready=0
for _ in $(seq 1 60); do
  if adb -s "$EMULATOR_SERIAL" get-state 2>/dev/null | grep -qx device; then
    device_ready=1
    break
  fi
  if ! kill -0 "$EMULATOR_PID" 2>/dev/null; then
    echo "Android emulator exited before adb connected" >&2
    tail -n 200 "$EMULATOR_LOG" >&2 || true
    exit 1
  fi
  sleep 2
done

if [[ "$device_ready" != "1" ]]; then
  echo "Android emulator did not appear in adb" >&2
  adb devices -l >&2 || true
  tail -n 200 "$EMULATOR_LOG" >&2 || true
  exit 1
fi

booted=0
for _ in $(seq 1 120); do
  if [[ "$(adb -s "$EMULATOR_SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; then
    booted=1
    break
  fi
  if ! kill -0 "$EMULATOR_PID" 2>/dev/null; then
    echo "Android emulator exited while booting" >&2
    tail -n 200 "$EMULATOR_LOG" >&2 || true
    exit 1
  fi
  sleep 2
done

if [[ "$booted" != "1" ]]; then
  echo "Android emulator did not finish booting" >&2
  tail -n 200 "$EMULATOR_LOG" >&2 || true
  exit 1
fi

ACTUAL_API="$(adb -s "$EMULATOR_SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')"
if [[ "$ACTUAL_API" != "$API_LEVEL" ]]; then
  echo "Expected API $API_LEVEL emulator, got API $ACTUAL_API" >&2
  exit 1
fi

if [[ -n "$EXPECTED_PAGE_SIZE" ]]; then
  ACTUAL_PAGE_SIZE="$(adb -s "$EMULATOR_SERIAL" shell getconf PAGE_SIZE | tr -d '\r')"
  if [[ "$ACTUAL_PAGE_SIZE" != "$EXPECTED_PAGE_SIZE" ]]; then
    echo "Expected page size $EXPECTED_PAGE_SIZE, got $ACTUAL_PAGE_SIZE" >&2
    exit 1
  fi
  echo "Verified runtime page size: $ACTUAL_PAGE_SIZE bytes"
fi

echo "Running instrumentation on Android $(adb -s "$EMULATOR_SERIAL" shell getprop ro.build.version.release | tr -d '\r') / API $ACTUAL_API"
adb -s "$EMULATOR_SERIAL" shell input keyevent 82 || true
adb -s "$EMULATOR_SERIAL" shell settings put global window_animation_scale 0
adb -s "$EMULATOR_SERIAL" shell settings put global transition_animation_scale 0
adb -s "$EMULATOR_SERIAL" shell settings put global animator_duration_scale 0

set +e
ANDROID_SERIAL="$EMULATOR_SERIAL" ./gradlew :app:connectedDebugAndroidTest --stacktrace 2>&1 | tee "$INSTRUMENTATION_LOG"
TEST_EXIT=${PIPESTATUS[0]}
set -e

if [[ "$TEST_EXIT" -ne 0 ]]; then
  echo "--- instrumentation log tail (API $API_LEVEL) ---" >&2
  tail -n 250 "$INSTRUMENTATION_LOG" >&2 || true
  echo "--- emulator log (API $API_LEVEL) ---" >&2
  tail -n 200 "$EMULATOR_LOG" >&2 || true
  adb -s "$EMULATOR_SERIAL" logcat -d -t 1000 > "$LOGCAT_LOG" 2>&1 || true
  echo "--- device logcat (API $API_LEVEL) ---" >&2
  tail -n 500 "$LOGCAT_LOG" >&2 || true
  exit "$TEST_EXIT"
fi
