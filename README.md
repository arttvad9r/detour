# Detour

Detour is an Android-only VPN/network client with per-app routing. Selected applications can use Direct, VPN, or DPI paths while the app owns the Android `VpnService`, routing policy, profile state, and UI.

## Current platform

- Android application module only (`:app`); no desktop/iOS/KMP target.
- minSdk 29, compile/target SDK 36, Java 17.
- Kotlin + Jetpack Compose + Navigation Compose.
- Android `VpnService` supplies the TUN interface and per-app allow-list.
- Mihomo is embedded as the current data plane for TUN/gVisor, DNS, UID rules, VLESS/Reality, WireGuard/AmneziaWG and outbound chaining.
- ByeDPI is packaged as a local native `ciadpi` backend and exposed to the engine through a loopback SOCKS endpoint.
- WARP/AmneziaWG and VLESS profiles are managed by Detour; imported proxy configs do not take ownership of Detour routing rules.

See [docs/architecture.md](docs/architecture.md) for the current component boundaries and lifecycle.

## Build

Install JDK 17, Go, Git, Python, unzip/zip, curl, Android command-line tools, and the exact Android SDK components used by CI:

```bash
sdkmanager "platforms;android-36" "build-tools;36.0.0" "ndk;28.0.13004108"
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

Run the same core gate as GitHub Actions:

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
bash engine/vulnscan.sh
```

With a device or emulator connected:

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
