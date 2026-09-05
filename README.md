# Detour

Detour is an Android-only VPN/network client with per-app routing. Selected applications can use Direct, VPN, or DPI paths while the app owns the Android `VpnService`, routing policy, profile state, and UI.

## Current platform

- Android application module only (`:app`); no desktop/iOS/KMP target.
- minSdk 29, compileSdk 37, targetSdk 36, Java 17.
- Kotlin + Jetpack Compose + Navigation 3.
- Android `VpnService` supplies a dual-stack IPv4/IPv6 TUN interface and per-app allow-list.
- Mihomo is embedded as the data plane for TUN/gVisor, DNS, UID/destination rules, multi-protocol subscription providers, VLESS/Reality, WireGuard/AmneziaWG and two-hop outbound chaining.
- ByeDPI is packaged as a local native `ciadpi` backend and exposed to the engine through an authenticated loopback SOCKS endpoint.
- WARP/AmneziaWG and direct VLESS profiles are managed by Detour; imported subscriptions may contain the explicitly allowed Mihomo-compatible remote proxy types, but imported configs never take ownership of Detour routing or DNS policy.
- Android Always-on VPN is supported. Lockdown remains an Android system setting and can block apps intentionally left on Detour's Direct/outside-TUN path.

See [docs/architecture.md](docs/architecture.md) for the current component boundaries and lifecycle.

## Build

Install JDK 17, Go, Git, Python, unzip/zip, curl, Android command-line tools, and the exact Android SDK components used by CI:

```bash
sdkmanager "platforms;android-37.0" "build-tools;36.0.0" "ndk;28.0.13004108"
```

Set the Android/JDK environment for your shell:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/28.0.13004108"
export ANDROID_NDK_ROOT="$ANDROID_NDK_HOME"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$(go env GOPATH)/bin:$PATH"
```

CI uses Go 1.26.7 and pinned Go-side tooling:

```bash
go install golang.org/x/mobile/cmd/gomobile@v0.0.0-20260821190718-4776eadac327
go install golang.org/x/vuln/cmd/govulncheck@v1.7.0
gomobile init
```

Build the debug APK:

```bash
./gradlew :app:assembleDebug
```

Gradle builds the native artifacts required by the app before packaging. Generated AAR/SO files, caches, IDE state, and machine-specific SDK configuration are not committed.

## Verification

Run the Android/Gradle portion of the GitHub Actions gate with:

```bash
./gradlew --dependency-verification strict \
  :app:testDebugUnitTest \
  :app:lintDebug \
  :app:lintRelease \
  :app:assembleDebug \
  :app:assembleDebugAndroidTest \
  :app:assembleRelease \
  :app:bundleRelease \
  :baselineprofile:assemble
```

CI additionally verifies dependency metadata, runs the embedded Go package with the race detector, scans Go source/binaries for known vulnerabilities, checks 16 KB ELF alignment and APK size, and exercises both CI-signed arm64 APK and AAB release paths.

`assembleDebugAndroidTest` compiles the instrumentation test APK. Pull requests and pushes to `main` execute it on hosted Android 16 and Android 17 (16 KB) emulators. With a local device/emulator connected, run:

```bash
./gradlew :app:connectedDebugAndroidTest
```

Device-only checks for routing, VPN lifecycle, navigation motion and adaptive refresh are listed in [docs/testing.md](docs/testing.md).

## Documentation

- [Architecture](docs/architecture.md) — Android/VPN/engine boundaries and runtime flow.
- [Testing](docs/testing.md) — CI contract and current device smoke checklist.
- [Native pins](docs/pins.md) — exact Mihomo/ByeDPI revisions and Android embedding notes.
- [WARP profiles](docs/warp-profiles.md) — supported WARP/AmneziaWG import behavior.

The Gradle wrapper distribution checksum and dependency verification metadata are committed. Native source revisions are pinned; generated native binaries are reproducible build outputs rather than repository source files.
