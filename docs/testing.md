# Testing

Choose checks according to what changed. Report what was actually checked and any material gaps; a PR or a full app build is not required for every task.

## Choosing checks

| Change | Relevant validation |
| --- | --- |
| Documentation, repository cleanup | Diff/whitespace checks, local links and affected ignore rules; no app build or device tests |
| CI or tooling configuration | Validate syntax and changed conditions/scripts; run the Android workflow manually if its build behavior needs exercising |
| App logic or bug fix | Focused regression tests and the affected build/lint tasks |
| UI | Affected screens and UI tests as useful; light/dark or large-font checks when the change affects them |
| VPN lifecycle, routing, DNS, credentials or native dependencies | Relevant app/engine tests and device smoke of the changed network/security behavior |
| Public app release | Full Android validation for the exact release revision, plus the release artifact checks below |

Development branches merge into `dev` after relevant checks and the owner's approval. A request to merge is approval; creating a PR is optional. Do not repeat a successful check unless the inputs changed or there is an unresolved concern.

## CI gate

Application changes pushed to `dev` run the Android integration gate. Optional PRs also run it. Documentation, `docs/`, `.github/` configuration, `.gitignore` and `.gitattributes` changes alone are excluded; validate those files directly. Feature-branch pushes and pushes to `main` do not automatically repeat the integration gate.

Use the `Android` workflow's manual **Run workflow** action on any development branch when hosted validation is needed before merging. Manual runs include Android 16 and Android 17 instrumentation. Before a release, run it on the exact candidate in `dev` (or `main` if needed). This is the full validation used by the release guard.

The integration gate performs strict Android builds/tests/lint, checks dependency-trust and Go module integrity, runs the embedded Go race detector, scans source/binaries for vulnerabilities, and verifies APK alignment/signing paths. The equivalent local build command is available when the change warrants that coverage:

```bash
./gradlew --dependency-verification strict \
  :app:testDebugUnitTest \
  :app:lintDebug \
  :app:lintRelease \
  :app:assembleDebug \
  :app:assembleDebugAndroidTest \
  :app:assembleRelease \
  :baselineprofile:assemble

git diff --exit-code -- gradle/verification-metadata.xml
# In the temporary engine module that points at .cache/mihomo-src:
go mod verify
go test -race -tags with_gvisor ./...
python3 tools/apk_size_report.py app/build/outputs/apk/release
bash engine/vulnscan.sh
```

The first release assembly is the universal compatibility baseline. CI then generates a disposable test keystore and rebuilds the release APK for the primary distribution ABI with signing and version overrides enabled. The disposable key exists only to exercise the production signing path; it is never used for published builds.

```bash
./gradlew :app:assembleRelease \
  -PdetourReleaseAbi=arm64-v8a \
  -PdetourVersionName=0.1.0-ci \
  -PdetourVersionCode=1001
```

CI verifies that signed APK with Android `apksigner`. The arm64 distribution check requires the APK to contain exactly `arm64-v8a` native libraries. APK size is reported for comparison rather than blocked by an arbitrary fixed budget. The universal and arm64 JSON size reports are retained as the `apk-size-reports` workflow artifact when available.

Manual runs and optional pull requests additionally install Android 16 and Android 17 emulator images and execute instrumentation. Automatic `dev` pushes compile the instrumentation APK but skip emulator download and runtime tests.

The Android workflow pins:

- JDK 17;
- compile SDK 37 and target SDK 36;
- Android platform package `platforms;android-37.0`;
- Android 16 / API 36 `google_apis` x86_64 system image;
- Android 17 / API 37 16 KB `google_apis_ps16k` x86_64 system image;
- build-tools 36.0.0;
- NDK 28.0.13004108;
- Go 1.26.9;
- pinned `gomobile` and `govulncheck` versions.

Dependency verification runs in strict mode against the committed `gradle/verification-metadata.xml` file, and CI fails if the build changes that file. The Go race/module gate copies the checked-in bridge package into a temporary module, redirects its Mihomo replacement to the exact `.cache/mihomo-src` tree prepared by the Android build, then runs `go mod verify` and `go test -race` with the production `with_gvisor` feature set.

After release assembly, `tools/apk_size_report.py` records total APK size, native ABI set, component totals, native library sizes per ABI and the largest ZIP entries. Unexpected ABIs fail verification; size growth is reviewed using the reports.

`engine/vulnscan.sh` covers the checked-in/patched Go engine source and the produced Android binary artifacts. Native source revisions and embedding patches are documented in `pins.md`.

## Instrumented tests

For manual runs and optional pull requests, hosted CI creates headless Android 16 / API 36 and Android 17 / API 37 emulators after the build gate and executes `:app:connectedDebugAndroidTest`, reusing the already-built native artifacts.

The hosted runtime suite intentionally stays below the VPN data plane. It currently covers:

- real `MainActivity` launch and primary Home controls;
- on-device Preferences DataStore profile mutations and duplicate-id rejection;
- backup restore policy, including confirmation before replacement and forced-off auto-connect;
- backup JSON → parser → DataStore round-trip for multiple subscriptions with independent selected nodes;
- Android-Keystore-backed credential encryption checks;
- Compose accessibility/layout regression coverage including large-font subscription controls and indivisible DPI parameter values;
- DPI application markers derived from committed settings when reopening history.

VPN consent, live TUN establishment, per-app routing, network changes, DNS behavior and DPI/VPN traffic validation still require the device smoke checklist below.

Run the full suite on a clean leased emulator before seeding profiles or routes for manual UI review. The activity smoke tests exercise the initial empty state.
Use the standard system font scale (`1.0`) for this run: the large-font tests
apply their own `2.0` override and compare it against the system-sized control.

Use a dedicated emulator for local automation. Use the owner's physical phone only with explicit authorization. Select the target serial when running:

```bash
ANDROID_SERIAL=<serial> ./gradlew :app:connectedDebugAndroidTest
```

## Unit/regression coverage

The JVM suite covers the behavior that must remain deterministic without a device, including:

- VLESS parsing, fingerprints and profile storage/migration;
- subscription profile persistence, selected-node serialization and generated selector configuration;
- WARP/AmneziaWG profile parsing/storage and fail-closed selection behavior;
- generated Mihomo configuration and rule ordering;
- DNS validation/bootstrap generation;
- backup serialization/import policy;
- DPI presets;
- DataStore settings mapping;
- auto-connect eligibility;
- active/inactive profile mutation policy;
- theme-transition and VPN-status visual policy.

The Go suite additionally covers engine Start/Stop serialization, failed replacement semantics, provider cleanup, subscription selection/cache boundaries, sensitive-URL redaction, detached latency helpers and preservation of the active runtime/logging/HomeDir when a replacement config fails before teardown.

Do not maintain manual test-count totals in this file; the test runner is the source of truth.

## Device smoke checklist

Use the sections relevant to the changed behavior. A navigation or layout adjustment does not require retesting unrelated VPN profiles or transports. For network/security changes, verify the affected behavior on an emulator or an explicitly authorized physical device with suitable synthetic/test profiles.

### VPN lifecycle

- Idle → Connect → Active → Disconnect.
- Fast connection must not flash a transient `Starting` state unnecessarily.
- Failure keeps the Home status card on the neutral surface and exposes one retry path through the main action button.
- Foreground notification and Quick Settings tile reflect the real VPN state.
- Network changes rebuild the active tunnel without restart storms.

### Profiles and persistence

- Switch VLESS ↔ subscription ↔ WARP/AmneziaWG and reconnect successfully.
- Select a non-first subscription server, disconnect/reconnect and verify the same server remains selected and carries traffic.
- Change the underlying network while a subscription is active and verify the selected server survives the automatic tunnel rebuild.
- Verify multiple subscriptions retain independent selected servers.
- Export VLESS + multiple subscriptions + WARP, import into a clean settings state, and verify every profile plus each selected subscription server is restored.
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

Home keeps a single route-summary entry above profile/server and DNS settings.
Direct counts include unassigned launcher apps (including system apps); VPN/DPI
counts use the effective installed-package assignments. These are configuration
counts, not live traffic. Package changes must refresh the summary on resume.

Buttons should yield slightly and change tone while held, then settle on release
or cancellation without shifting nearby content. Cancelled gestures and disabled
buttons must not dispatch actions or haptics. The Home connection action uses a
light tick; navigation-only actions do not. Confirm/reject and route-selection
haptics retain their existing meanings and respect Android's haptic settings.

Exercise repeatedly:

`Home → Settings → App Routes → Back → Back`

Also open VPN profiles, DPI, DNS, Backup, Theme and Help from Settings.
The setup wizard appears automatically on first launch; reopening it is under
Help → Run setup again and must preserve the current profiles, routes and DNS.

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

1. successful full Android validation for the exact release commit;
2. focused device/emulator checks where the changes need runtime verification;
3. for native/routing changes, a successful connect and per-app routing check on the produced build. State clearly if external VPN endpoints or the target network are unavailable rather than claiming coverage.

Do not describe historical device results as validation of a newer commit.
