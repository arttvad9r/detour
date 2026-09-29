# Changelog

All notable user-facing changes to Detour are recorded here.

## Unreleased

### Added
- Rename any saved profile: VLESS keys, subscriptions and WireGuard/AmneziaWG/WARP configs. Renaming does not restart an active tunnel.
- Replace the configuration file of a WireGuard profile without losing its name.

### Changed
- WireGuard profiles are labelled WARP, AmneziaWG or AmneziaWG 3.1 by their actual endpoint instead of by the profile name, on the profile list and on Home. New imports are named after the detected type.
- The key editor no longer blocks screenshots and screen recording.

### Removed
- The "Always-on VPN" row in Settings, which only opened the system VPN list. Android's own Always-on VPN setting still works with Detour.

## 0.3.0 — 2026-09-28

### Added
- Subscription plan card: traffic left, usage bar and expiry date reported by the provider, with a warning color near the limit or expiry.
- Server picker for subscriptions: search, sort by ping and one-tap latency test in a bottom sheet.
- The provider's own subscription title replaces the host name until you rename the profile.
- Add profiles from the clipboard with automatic link type detection (`vless://`, HTTPS subscription, `vpn://`).
- Import a profile from a QR code in a screenshot or photo, decoded on the device without camera permission.
- Live download/upload speed and session traffic on Home and in the VPN notification.
- Android Always-on VPN: Detour now starts by itself after reboot when selected as the Always-on VPN.

### Changed
- One "Add by link" editor accepts both VLESS keys and subscription URLs.
- The Home server row opens server selection for subscriptions.
- Appearing cards, notices and the traffic line animate in place instead of shifting the layout.
- Build toolchain updated: AGP 9.4.1, Kotlin 2.4.20, Compose BOM 2026.09.00, Baseline Profile 1.5.0.

## 0.2.1 — 2026-09-10

### Changed
- Native engines (Mihomo, ByeDPI) are pinned to exact revisions and build reproducibly offline, enabling F-Droid source builds.
- Obtainium and F-Droid metadata, bilingual product page and APK verification guidance.

## 0.1.1 — 2026-09-09

### Added
- Unified VPN profile screen with one add-profile flow.
- Amnezia `vpn://` import for XRay VLESS Reality and AmneziaWG 3.1.
- Multiple independent WireGuard-family profiles, allowing Cloudflare WARP and multiple AmneziaWG profiles to coexist.
- Versioned backup support for the multi-WireGuard profile collection.
- ByeDPI Proxy Test workflow for comparing reference/custom strategies and applying a working result manually.

### Changed
- Profile selection, edit and delete operations are identity-based and do not overwrite unrelated WireGuard profiles.
- UI selection/input treatment is quieter: large surfaces remain neutral while actions, borders, switches and compact state marks retain the theme accent.
- Public-release UI received final spacing, profile-flow, and presentation polish.

### Security / reliability
- Sensitive VPN profile storage remains encrypted with Android Keystore-backed encryption.
- CI covers Android 16/17 instrumentation, native race checks, dependency verification, vulnerability scanning, 16 KB ELF alignment and release APK verification.
- Signed semantic-tag release publication and arm64 APK verification are part of the release pipeline.
