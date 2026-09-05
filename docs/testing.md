# Testing

This document defines the current verification contract. Historical screenshots, one-off device investigations and completed remediation plans are intentionally not kept as active project documentation.

## CI gate

Every push and pull request runs the strict Android build/test gate, verifies that dependency-trust metadata was not rewritten, runs the embedded Go package under the race detector against the exact prepared Mihomo source tree, checks Go module integrity, scans source/binaries for known Go vulnerabilities, and verifies release size/alignment/signing paths.

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

git diff --exit-code -- gradle/verification-metadata.xml
# In the temporary engine module that points at .cache/mihomo-src:
go mod verify
go test -race -tags with_gvisor ./...
python3 tools/apk_size_report.py app/build/outputs/apk/release
bash engine/vulnscan.sh
```

The first release assembly/bundle is the universal compatibility baseline. CI then generates a disposable test keystore and rebuilds both release APK and release AAB for the primary distribution ABI with signing and version overrides enabled. The disposable key exists only to exercise the production signing path; it is never used for published builds.

```bash
./gradlew :app:assembleRelease :app:bundleRelease \
  -PdetourReleaseAbi=arm64-v8a \
  -PdetourVersionName=0.1.0-ci \
  -PdetourVersionCode=1001
```

CI verifies the signed APK with Android `apksigner`, `zipalign`, the 16 KB ELF checker and the APK size/ABI report. The arm64 distribution check requires exactly `arm64-v8a` native libraries and enforces a 30 MiB maximum APK size.

The signed AAB is independently checked as a JAR-signed bundle and inspected to ensure that its native payload contains only `arm64-v8a`. This exercises the package type required for Google Play instead of assuming that a valid APK implies a valid App Bundle.

The universal and arm64 JSON APK size reports are retained as the `apk-size-reports` workflow artifact when available; a prior build failure is not obscured by a secondary missing-report failure.

Pull requests and pushes to `main` additionally install hosted Android 16 and Android 17 emulator images and run the instrumentation suite. Ordinary branch pushes still compile the instrumentation APK but skip emulator download and runtime tests. The pull-request gate therefore validates the exact review revision on a device before merge, and the `main` push validates the integrated revision after merge.

The Android workflow pins:

- JDK 17;
- compile SDK 37 and target SDK 36;
- Android platform package `platforms;android-37.0`;
- Android 16 / API 36 `google_apis` x86_64 system image;
- Android 17 / API 37 16 KB `google_apis_ps16k` x86_64 system image;
- build-tools 36.0.0;
- NDK 28.0.13004108;
- Go 1.26.7;
- pinned `gomobile` and `govulncheck` versions.

Dependency verification runs in strict mode against the committed `gradle/verification-metadata.xml` file, and CI fails if the build changes that file. The Go race/module gate copies the checked-in bridge package into a temporary module, redirects its Mihomo replacement to the exact `.cache/mihomo-src` tree prepared by the Android build, then runs `go mod verify` and `go test -race` with the production `with_gvisor` feature set.

After release assembly, `tools/apk_size_report.py` records the total APK size, packaged native ABI set, component totals, native library sizes per ABI, and the largest ZIP entries in the GitHub Actions job summary. It also writes machine-readable JSON reports. The arm64 distribution report is a hard gate: exceeding 30 MiB or packaging any ABI other than `arm64-v8a` fails CI.

`engine/vulnscan.sh` covers the checked-in/patched Go engine source and the produced Android binary artifacts. Native source revisions and embedding patches are documented in `pins.md`.

## Instrumented tests

For pull requests and `main` pushes, hosted CI creates headless Android 16 / API 36 and Android 17 / API 37 16-KB emulators after the normal build gate and executes `:app:connectedDebugAndroidTest`. Keeping runtime tests in the same job reuses the already-built Mihomo/ByeDPI artifacts instead of rebuilding the native engine in a second job.

The hosted runtime suite intentionally stays mostly below the live external VPN data plane. It currently covers:

- real `MainActivity` launch and primary Home controls;
- navigation/detail recreation behavior;
- on-device Preferences DataStore profile mutations and duplicate-id rejection;
- backup restore policy and round trips;
- Android-Keystore-backed credential encryption checks;
- Compose accessibility/layout regression coverage including touch targets and large-font behavior;
- Quick Settings and VPN manifest declarations;
- Android Always-on capability metadata;
- platform backup disabled and cleartext-traffic disabled application flags;
- local ByeDPI authentication behavior where device execution is sufficient.

VPN consent, live TUN establishment, per-app routing, network changes, DNS behavior and external DPI/VPN traffic validation still require the device smoke checklist below.

With a local emulator or device connected, run the same instrumentation suite with:

```bash
ANDROID_SERIAL=<serial> ./gradlew :app:connectedDebugAndroidTest
```

## Unit/regression coverage

The JVM suite covers deterministic host-side behavior including:

- VLESS parsing, Reality validation, fingerprints and profile storage/migration;
- subscription metadata, background update policy and per-profile selected-node serialization;
- manual vs automatic subscription group generation;
- search/sort/favorites presentation policy;
- WARP/AmneziaWG profile parsing/storage and fail-closed selection behavior;
- generated dual-stack Mihomo configuration and rule ordering;
- domain/domain-suffix/IPv4/IPv6 CIDR destination rules;
- two-hop multi-hop persistence, resolver policy, config generation, backup and auto-connect preflight;
- DNS validation/bootstrap generation;
- backup v1-v5 serialization/import policy;
- DPI presets;
- DataStore settings mapping;
- foreground network-aware auto-connect eligibility;
- active/inactive profile mutation policy;
- notification-permission prompt policy;
- theme-transition and VPN-status visual policy;
- live traffic parsing/formatting.

The Go suite additionally covers engine Start/Stop serialization, failed replacement semantics, provider cleanup, multi-protocol subscription preparation, sing-box normalization, selected-node/cache boundaries, sensitive-URL redaction, detached latency helpers and preservation of the active runtime/logging/HomeDir when a replacement config fails before teardown.

Do not maintain manual test-count totals in this file; the test runner is the source of truth.

## Device smoke checklist

Run this after changes to VPN lifecycle, engine/config generation, routing, package inventory, navigation, permissions or display policy.

### VPN lifecycle

- Idle → Connect → Active → Disconnect.
- Fast connection must not flash a transient `Starting` state unnecessarily.
- Failure exposes one retry path through the main action button.
- Foreground notification shows the real VPN state and live RX/TX rate while Active.
- Quick Settings tile reflects the real state and remains retryable after a failure.
- On Android 13+, a first manual connection can request notification permission in context; denial must not prevent VPN operation.
- First connection from Quick Settings handles VPN consent correctly and does not repeatedly nag for notification permission after a denial.
- Network changes rebuild the active tunnel without restart storms.
- Android system Always-on starts the configured tunnel after the user enables it in VPN settings.
- With Lockdown enabled, confirm the documented platform behavior for Detour Direct/outside-TUN applications instead of assuming those applications can bypass Lockdown.

### Profiles, subscriptions and persistence

- Switch direct VLESS ↔ subscription ↔ WARP/AmneziaWG and reconnect successfully.
- Import supported subscription bodies (Mihomo YAML and URI/base64); verify unsupported/local proxy types do not enter the runtime provider.
- If sing-box JSON is used, verify supported mapped outbounds load while routing/DNS/selectors from the source config are not imported.
- Select a non-first subscription server, disconnect/reconnect and verify the same server remains selected and carries traffic.
- Switch subscription selection to Auto and confirm the live node can change without exposing a manual selection action.
- Change the underlying network while a subscription is active and verify the selected/mode state survives the tunnel rebuild.
- Verify multiple subscriptions retain independent selected servers, favorites and update metadata.
- Export profiles/settings, import into a clean settings state, and verify backup v5 restores destination rules and a valid multi-hop reference.
- Editing/replacing the active exit profile restarts the active tunnel.
- Editing an inactive profile does not interrupt the tunnel.
- Deleting the active exit stops the session instead of selecting another endpoint silently.
- Backup import while Active stops the stale tunnel and leaves auto-connect disabled.

### Multi-hop

- Configure direct VLESS → direct VLESS, WARP → subscription and direct VLESS → WARP chains and verify traffic through the exit probe.
- Verify subscription cannot be selected as the entry and WARP → WARP is rejected.
- Edit the active entry and verify the tunnel restarts.
- Delete the active entry and verify the tunnel stops and the reference is cleared.
- Delete/stale/corrupt/self-referencing entry state must fail closed rather than silently falling back to one hop.

### Per-app and destination routing

- Verify at least one app on `VPN`, one on `DPI`, and one `DIRECT`/outside the routed allow-list.
- Install/remove a routed application and confirm effective routes and the VPN allow-list are rebuilt.
- Verify domain, suffix, IPv4 CIDR and IPv6 CIDR overrides for VPN/DPI-routed applications.
- Confirm Direct applications remain outside destination overrides.
- Verify selected IPv4 and IPv6 Internet traffic is captured by the dual-stack TUN and does not escape over the physical interface.
- On API 29–32, verify LAN destinations are rejected before destination/UID VPN/DPI rules.
- Check DNS resolution through the configured IP/HTTPS DoH policy.

### App Routes UI

- First entry into App Routes should render the warmed app list without an empty intermediate layout.
- App icons should already be present in the first normally visible frame; no mass placeholder → icon swap.
- Search, Show system apps and route changes should not mass-fade/recreate the list.
- Scroll/fling should remain smooth while icons are loaded from the process cache/background path.

### Navigation and motion

Exercise repeatedly:

`Home → Settings → App Routes → Back → Back`

Also open Destination Rules, VPN profiles, DPI, DNS, Diagnostics, Backup and Theme from Settings.

Verify:

- only the top full-screen destination moves;
- the lower screen remains stationary;
- no full-screen alpha crossfade/ghosting;
- no abrupt layer removal at the end of Back;
- predictive Back returns to the correct prior Navigation 3 destination;
- system animator-duration scale is respected.

### Adaptive refresh and layout

On phone, tablet/foldable/resizable emulator and a compatible high-refresh device:

- Home uses its expanded split layout without stretching controls excessively;
- Settings list/detail navigation remains usable at expanded widths;
- large font does not clip critical labels/actions;
- navigation should request high-refresh residency only while moving;
- active scroll/fling and bounded list/expand motion may request high refresh;
- static screens should be allowed to return to the system/default adaptive policy;
- touch boost remains platform-managed;
- the app must not pin a concrete 120 Hz mode;
- compare 60 Hz and 120 Hz: physical animation duration should remain time-based while the higher-refresh display renders more intermediate frames.

Frame-rate requests are scheduler hints, not a guarantee of a particular panel mode. Full adaptive-refresh behavior depends on the Android version, device display stack and OEM implementation.

## Release evidence

Before treating a revision as release-ready, record at minimum:

1. green CI for the exact commit, including signed APK and signed AAB checks;
2. a fresh Android 16/17 or physical-device smoke for the areas changed;
3. for native/routing changes, a successful connect and IPv4/IPv6 per-app routing check on the exact produced build;
4. for publication, the real production signing/Play App Signing process and Play Console declarations remain an external release responsibility and must match the app's actual behavior.

Do not describe historical device results as validation of a newer commit.
