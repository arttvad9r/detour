# Changelog

All notable user-facing changes to Detour are recorded here.

## Unreleased

No user-facing changes have been recorded after the current public release yet.

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
