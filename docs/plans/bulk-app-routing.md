# App route filters and bulk assignment

Scope: App Routes only; preserve Direct as the default and the existing single-app selector.

## Behavior

- Filter All / Direct / VPN / DPI together with search and system-app visibility.
- Enter explicit selection mode, toggle individual apps, or select the current visible results.
- Clear selection when query, route filter, or system-app visibility changes. Discard uninstalled selections.
- Apply Direct / VPN / DPI to the selection in one DataStore transaction, preserving unrelated routes.
- Restart the active tunnel once after a changed batch is persisted; unchanged batches do not restart it.
- On persistence failure, show an error and retain selection for retry; leave selection mode after success.
- Provide Russian and English UI copy and native accessibility semantics.

## Verification

Unit tests cover combined filtering. Instrumentation covers native selection/filter interactions,
failure/retry, restart count, bulk DataStore persistence and Direct override removal.
Run unit tests, debug/release lint, debug/test APK builds and native emulator instrumentation.
Inspect the full native App Routes screen at normal and enlarged font sizes.

## Verified locally (2026-10-16)

- Strict Gradle gate passed: 283 JVM tests, debug/release lint, debug/test/release APKs and baseline-profile assembly. Dependency verification metadata stayed unchanged.
- All 42 instrumentation tests passed on Android 16 / API 36 and Android 17 / API 37.
- Native Russian screens inspected in light/dark themes and at 200% font scale. Selection mode hides search/settings; large fonts use two filter rows and vertically stacked route actions.
- Android 16 DPI-only smoke: connected after bulk assignment; changed two of four routed apps to Direct while connected; the tunnel restarted and remained connected with two routed apps.
- Existing fail-closed handling still rejects shared-UID system packages (observed with SIM Toolkit and the emulator calendar shared-user identity).

Limits: live VPN credentials and network throughput were not tested. The Android 17 image uses 4 KB pages; the CI 16 KB image was not exercised locally. Native engine code was unchanged, so Go race/vulnerability scans were not repeated.
