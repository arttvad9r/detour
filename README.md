# Detour

Android network client with per-app routing through embedded native components.

## Build

The repository includes a pinned Nix flake (`flake.nix` and `flake.lock`). Use it for the reproducible Java, Android SDK/NDK, Go, and gomobile environment:

```bash
nix develop -c ./gradlew :app:assembleDebug
```

The Gradle build creates the native artifacts it consumes before packaging. Generated artifacts, caches, and machine-specific SDK configuration are kept out of git.

## Verification

Run the same core checks configured in GitHub Actions:

```bash
nix develop -c ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
nix develop -c bash engine/vulnscan.sh
```

With an Android emulator or device connected, instrumented tests can be run separately:

```bash
nix develop -c ./gradlew :app:connectedDebugAndroidTest
```

The Gradle wrapper verifies its distribution checksum, and dependency verification metadata is committed in `gradle/verification-metadata.xml`. Native source revisions and embedding patches are recorded in `docs/pins.md`.

Selected-app IPv6 traffic is captured and rejected explicitly when unsupported rather than escaping through the physical interface. DNS settings accept IP literals or HTTPS DoH endpoints; hostname-based DoH gets a bootstrap resolver in the generated engine configuration.

WARP/AmneziaWG import behavior is documented in `docs/warp-profiles.md`. Historical device and test evidence is recorded in `docs/testing.md`; documents under `docs/superpowers/` are project history rather than current build instructions.
