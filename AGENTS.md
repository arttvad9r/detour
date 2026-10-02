# Detour

Android per-app routing client: each app goes Direct, VPN (VLESS Reality, subscriptions, WARP/AmneziaWG) or DPI (ByeDPI) inside one `VpnService`. Kotlin/Compose app in `app/`, embedded Mihomo and ByeDPI built from pinned sources in `engine/`.

## Where things are documented
- Architecture and boundaries: `docs/architecture.md`
- Build, test gates, device QA: `docs/testing.md`; toolchain pins: `docs/pins.md`
- Release: `docs/releasing.md`, `docs/release-checklist.md`
- Contribution rules (fail-closed routing, synthetic fixtures, shared UI components): `CONTRIBUTING.md`

## Commands
- Debug build: `./gradlew :app:assembleDebug`
- Core gate before a PR: see `docs/testing.md` (strict dependency verification, unit tests, lint)
- Physical device: `ANDROID_SERIAL=<serial> ./gradlew :app:connectedDebugAndroidTest`

## Rules not obvious from the code
- Never create a tag or GitHub release without explicit permission from the owner.
- Detour owns DNS, routing and listeners; imported Clash/Mihomo/WireGuard configs contribute only compatible outbounds.
- WARP/AmneziaWG groups use `fallback`, not `url-test`: switching a live endpoint for small speed gains breaks long-lived QUIC sessions.
- Subscriptions go through Mihomo `proxy-providers`; don't write provider-specific subscription parsers.
- Android allows only one active `VpnService`: Detour and Tailscale cannot run at the same time, split tunneling doesn't change that.
- Scope: not a NekoBox clone or a generic Mihomo panel; advanced knobs stay out of the main UI.

## Status
- 0.4.0 (2026-10-02): main is the 0.2.1 feature set with updated dependencies; GitHub Releases only.
- The 0.3.x work (live traffic, subscription UI, import flow) continues on `dev`.
- Next: not recorded yet.
