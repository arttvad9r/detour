# Detour

Android network client with per-app routing through embedded native components.

## Development on Arch Linux

Install the host tools:

```bash
sudo pacman -S --needed jdk17-openjdk go git python unzip zip curl
```

Install Android command-line tools, then install the exact SDK components used by the project:

```bash
sdkmanager "platforms;android-36" "build-tools;36.0.0" "ndk;28.0.13004108"
```

Set the Android/JDK environment for your shell. Adjust `ANDROID_HOME` if your SDK lives elsewhere:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/28.0.13004108"
export ANDROID_NDK_ROOT="$ANDROID_NDK_HOME"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$(go env GOPATH)/bin:$PATH"
```

CI builds the native engine with Go 1.26.7. Keep the local Go toolchain on the same release when producing release artifacts. Install the Go-side build and vulnerability tools:

```bash
go install golang.org/x/mobile/cmd/gomobile@latest
go install golang.org/x/vuln/cmd/govulncheck@latest
```

Build the app:

```bash
./gradlew :app:assembleDebug
```

The Gradle build creates the native artifacts it consumes before packaging. Generated artifacts, caches, and machine-specific SDK configuration are kept out of git.

## Verification

Run the same core checks configured in GitHub Actions:

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
bash engine/vulnscan.sh
```

With an Android emulator or device connected, instrumented tests can be run separately:

```bash
./gradlew :app:connectedDebugAndroidTest
```

The Gradle wrapper verifies its distribution checksum, and dependency verification metadata is committed in `gradle/verification-metadata.xml`. Native source revisions and embedding patches are recorded in `docs/pins.md`.

Selected-app IPv6 traffic is captured and rejected explicitly when unsupported rather than escaping through the physical interface. DNS settings accept IP literals or HTTPS DoH endpoints; hostname-based DoH gets a bootstrap resolver in the generated engine configuration.

WARP/AmneziaWG import behavior is documented in `docs/warp-profiles.md`. Historical device and test evidence is recorded in `docs/testing.md`; documents under `docs/superpowers/` are project history rather than current build instructions.
