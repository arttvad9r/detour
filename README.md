# Detour

Detour is an open-source Android VPN/network client focused on explicit per-app routing. Each app can be routed through **Direct**, **VPN**, or **DPI** while Detour owns the Android `VpnService`, profile selection, DNS policy and routing state.

> Pre-release software. Review the current limitations and verify the configuration you import before relying on Detour for sensitive traffic.

## Features

- Per-app `Direct` / `VPN` / `DPI` routing.
- VLESS Reality (`xtls-rprx-vision`) profiles.
- Subscription profiles with explicit node selection.
- Cloudflare WARP and AmneziaWG, including supported AWG 3.1 fields.
- Amnezia `vpn://` import for supported XRay VLESS Reality and AmneziaWG invitations.
- Multiple WireGuard-family profiles stored side by side; importing one does not replace another.
- Native ByeDPI backend for the DPI route.
- Local encrypted storage for sensitive profile material.
- Light/dark themes, adaptive Compose UI and Android 16/17 CI coverage.

## Architecture

- Android-only (`:app`), minSdk 29, compileSdk 37, targetSdk 36, Java 17.
- Kotlin + Jetpack Compose + Navigation3.
- Android `VpnService` supplies the TUN interface and per-app allow-list.
- Mihomo is embedded as the data plane for TUN/gVisor, DNS, UID rules, VLESS/Reality, WireGuard/AmneziaWG and outbound chaining.
- ByeDPI is packaged as a local native `ciadpi` backend and exposed to the engine through a loopback SOCKS endpoint.

See [docs/architecture.md](docs/architecture.md) for component boundaries and lifecycle, and [docs/pins.md](docs/pins.md) for exact native revisions.

## Privacy and security

Detour does not require a project account and does not intentionally upload project analytics or automatic crash reports. VPN/DNS/subscription services configured by the user are third parties and receive the network requests required by those protocols. Sensitive profile material is stored locally in encrypted DataStore values backed by Android Keystore.

Read [PRIVACY.md](PRIVACY.md) and [SECURITY.md](SECURITY.md) before reporting issues or distributing builds. Never post real VPN invitations or credentials in public bug reports.

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

Gradle builds the required native artifacts before packaging. Generated AAR/SO files, caches, IDE state and machine-specific SDK configuration are not committed.

## Verification

Run the same core Gradle gate as GitHub Actions:

```bash
./gradlew --dependency-verification strict \
  :app:testDebugUnitTest \
  :app:lintDebug \
  :app:lintRelease \
  :app:assembleDebug \
  :app:assembleDebugAndroidTest \
  :app:assembleRelease
bash engine/vulnscan.sh
```

Execute instrumentation on a connected device/emulator with:

```bash
./gradlew :app:connectedDebugAndroidTest
```

The hosted Android workflow additionally tests Android 16 and Android 17, checks dependency trust, native race behavior, APK size, ABI contents and 16 KB ELF alignment. See [docs/testing.md](docs/testing.md).

## Releases

Signed GitHub releases are produced from semantic tags (`vMAJOR.MINOR.PATCH`) only after the exact commit has successful Android CI on `main`. Release signing credentials are repository secrets and are never committed.

See [docs/releasing.md](docs/releasing.md) and [docs/release-checklist.md](docs/release-checklist.md).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Real VPN credentials must never be committed or placed in tests/issues; synthetic fixtures only.

## License

Detour-authored code is licensed under the [MIT License](LICENSE). Bundled/embedded third-party components retain their own licenses. In particular, the embedded Mihomo engine has GPL-3.0 obligations that apply to binary distribution. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and [docs/pins.md](docs/pins.md).
