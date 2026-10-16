# Guided first connection

- Show setup on a clean installation after settings load; skip it for existing configuration or a completed/skipped guide.
- Persist that a first run has started so an unfinished guide is still offered after routes/profiles have been saved. Explicit Skip/Finish dismisses it.
- Offer DPI-only, VLESS, subscription and WARP/Amnezia scenarios; retain the selected scenario across recreation.
- Reuse existing protected profile editors/importers and app routing. Do not select profiles, assign routes, or connect without an explicit user action.
- Confirm a compatible active profile and effective app routes before enabling Connect. Preserve shared-UID rejection and missing-profile fail-closed behavior.
- Reuse the existing Android VPN consent/notification permission flow; finish only after connection or explicit Skip.
- Make the guide available from Settings; provide Russian/English copy, large-font layout and native accessibility.
- Keep onboarding completion local to this installation and outside exported credential/settings backups.

Verification: policy/unit tests, DataStore completion persistence, native goal/skip/editor/routing/connection journeys, recreation and large-font checks; strict JVM/lint/APK gate and Android 16/17 instrumentation. Live paid/private VPN servers are outside automated fixtures.

## Verification (2026-10-16)

- Strict dependency verification, 292 JVM tests, debug/release lint, debug/test/release APKs and baseline-profile assembly passed. Trust metadata remained unchanged.
- All 51 instrumentation tests passed on Android 16 / API 36 and Android 17 / API 37. This includes scenario-specific protected editors, skip persistence, activity recreation, self-UID exclusion, backup-local completion state, large-font reachability and failed-connection retry.
- Native Russian screens inspected in light/dark themes and at 200% font size.
- DPI-only device journey passed: choose an app, return to the guide, request VPN consent, cancel/retry consent, connect, finish, and reopen to Home. An unfinished first run was also checked after a forced process restart.

Limits: real VLESS/subscription/WARP servers were not exercised; their existing forms/import paths were reused. The local Android 17 image uses 4 KB pages, not the CI 16 KB image. Native engine code was unchanged.
