# Testing

This document defines the current verification contract. Historical screenshots, one-off device investigations and completed remediation plans are intentionally not kept as active project documentation.

## CI gate

Every push and pull request runs:

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease
python3 tools/apk_size_report.py app/build/outputs/apk/release
bash engine/vulnscan.sh
```

The Android workflow pins:

- JDK 17;
- compile SDK 37 and target SDK 36;
- Android platform package `platforms;android-37.0`;
- build-tools 36.0.0;
- NDK 28.0.13004108;
- Go 1.26.7;
- pinned `gomobile` and `govulncheck` versions.

Dependency verification runs in strict mode against the committed `gradle/verification-metadata.xml` file.

After release assembly, `tools/apk_size_report.py` records the total APK size, component totals, native library sizes per ABI, and the largest ZIP entries in the GitHub Actions job summary. It also writes `app/build/reports/apk-size.json` for machine-readable follow-up. No hard size threshold is enforced yet; this establishes a baseline before changing ABI/distribution packaging.

`engine/vulnscan.sh` covers the checked-in/patched Go engine source and the produced Android binary artifacts. Native source revisions and embedding patches are documented in `pins.md`.

## Instrumented tests

Hosted CI compiles the instrumentation test APK with `:app:assembleDebugAndroidTest`, so androidTest source and dependencies are checked on every push and pull request. Executing instrumentation tests still requires an Android runtime.

With an emulator or device connected:

```bash
ANDROID_SERIAL=<serial> ./gradlew :app:connectedDebugAndroidTest
```

## Unit/regression coverage

The JVM suite covers the behavior that must remain deterministic without a device, including:

- VLESS parsing, fingerprints and profile storage/migration;
- WARP/AmneziaWG profile parsing/storage and fail-closed selection behavior;
- generated Mihomo configuration and rule ordering;
- DNS validation/bootstrap generation;
- backup serialization/import policy;
- DPI presets;
- DataStore settings mapping;
- auto-connect eligibility;
- active/inactive profile mutation policy;
- theme-transition and VPN-status visual policy.

Do not maintain manual test-count totals in this file; the test runner is the source of truth.

## Device smoke checklist

Run this after changes to VPN lifecycle, engine/config generation, routing, package inventory, navigation or display policy.

### VPN lifecycle

- Idle → Connect → Active → Disconnect.
- Fast connection must not flash a transient `Starting` state unnecessarily.
- Failure keeps the Home status card on the neutral surface and exposes one retry path through the main action button.
- Foreground notification and Quick Settings tile reflect the real VPN state.
- Network changes rebuild the active tunnel without restart storms.

### Profiles and persistence

- Switch VLESS ↔ WARP/AmneziaWG and reconnect successfully.
- Editing/replacing the active profile restarts the active tunnel.
- Editing an inactive profile does not interrupt the tunnel.
- Deleting the active profile stops the session instead of selecting another endpoint silently.
- Backup import while Active stops the stale tunnel and leaves auto-connect disabled.
- Reinstall/start behavior respects the persisted auto-connect preference.

### Per-app routing

- Verify at least one app on `VPN`, one on `DPI`, and one `DIRECT`/outside the routed allow-list.
- Install/remove a routed application and confirm effective routes and the VPN allow-list are rebuilt.
- Confirm selected IPv6 traffic does not bypass the VPN.
- On API 29–32, verify LAN destinations are rejected before UID→VPN/DPI rules.
- Check DNS resolution through the configured IP/DoH policy.

### App Routes UI

- First entry into App Routes should render the warmed app list without an empty intermediate layout.
- App icons should already be present in the first normally visible frame; no mass placeholder → icon swap.
- Search, Show system apps and route changes should not mass-fade/recreate the list.
- Scroll/fling should remain smooth while icons are loaded from the process cache/background path.

### Navigation and motion

Exercise repeatedly:

`Home → Settings → App Routes → Back → Back`

Also open VPN profiles, DPI, DNS, Backup and Theme from Settings.

Verify:

- only the top full-screen destination moves;
- the lower screen remains stationary;
- no full-screen alpha crossfade/ghosting;
- no abrupt layer removal at the end of Back;
- Settings and App Routes remain readable during the transition;
- system animator-duration scale is respected.

### Adaptive refresh

On a compatible high-refresh Android device, use the system refresh-rate overlay and/or Perfetto FrameTimeline:

- navigation should request high-refresh residency only while moving;
- active scroll/fling and bounded list/expand motion may request high refresh;
- static screens should be allowed to return to the system/default adaptive policy;
- touch boost remains platform-managed;
- the app must not pin a concrete 120 Hz mode;
- compare 60 Hz and 120 Hz: physical animation duration should remain time-based while the higher-refresh display renders more intermediate frames.

Frame-rate requests are scheduler hints, not a guarantee of a particular panel mode. Full adaptive-refresh behavior depends on the Android version, device display stack and OEM implementation.

## Release evidence

Before treating a revision as release-ready, record at minimum:

1. green CI for the exact commit;
2. a fresh Android device/emulator smoke for the areas changed;
3. for native/routing changes, a successful connect and per-app routing check on the exact produced build.

Do not describe historical device results as validation of a newer commit.
