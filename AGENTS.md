# Detour

Android per-app routing client: each app goes Direct, VPN (VLESS Reality, subscriptions, WARP/AmneziaWG) or DPI (ByeDPI) inside one `VpnService`. Kotlin/Compose app in `app/`, embedded Mihomo and ByeDPI built from pinned sources in `engine/`.

## Where things are documented
- Architecture and boundaries: `docs/architecture.md`
- Build, test gates, device QA: `docs/testing.md`; toolchain pins: `docs/pins.md`
- Release: `docs/releasing.md`, `docs/release-checklist.md`
- Contribution rules (fail-closed routing, synthetic fixtures, shared UI components): `CONTRIBUTING.md`

## Commands
- Debug build: `./gradlew :app:assembleDebug`
- Choose checks for the change using `docs/testing.md`; documentation and repository housekeeping do not require an app build.
- Instrumentation: use a dedicated emulator and set `ANDROID_SERIAL` explicitly. Use the owner's physical phone only when they explicitly authorize it.

## Rules not obvious from the code
- Detour owns DNS, routing and listeners; imported Clash/Mihomo/WireGuard configs contribute only compatible outbounds.
- WARP/AmneziaWG groups use `fallback`, not `url-test`: switching a live endpoint for small speed gains breaks long-lived QUIC sessions.
- Subscriptions go through Mihomo `proxy-providers`; don't write provider-specific subscription parsers.
- Android allows only one active `VpnService`: Detour and Tailscale cannot run at the same time, split tunneling doesn't change that.
- Product direction: a per-app routing client with a simple main UI. Broader configuration and design changes are product decisions for the owner.

## Git
- `main` keeps the latest released application code as a stable return point. Documentation, repository cleanup and other small maintenance changes that do not alter the shipped app may also land there without a release or version bump. Build, dependency and packaging changes go through `dev`.
- Application development happens in branches from `dev`. Branch names are unrestricted. Keep concurrent agent work isolated.
- Before merging development into `dev`, run checks appropriate to the change and obtain the owner's approval. A request to merge is approval for that merge; do not ask again.
- Release an approved, tested revision of `dev` by merging it into `main` and tagging the release `vX.Y.Z`. Continue subsequent development on `dev`. See `docs/releasing.md`.
- Pull requests are optional. A request to merge or release authorizes the required Git operations without creating a PR.
- Carry maintenance changes into both `main` and `dev` without bringing unreleased application code into `main`. Preserve unrelated local work, avoid rewriting shared history, and remove temporary branches when finished.
