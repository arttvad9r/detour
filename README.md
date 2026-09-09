<p align="center">
  <strong>English</strong> · <a href="README.ru.md">Русский</a>
</p>

<div align="center">

# Detour

### Per-app routing for Android.

**Direct · VPN · DPI**

Detour is an open-source Android network client that lets **each app use the route you choose**. Keep some apps direct, send others through a VPN, and use ByeDPI only where it is needed — all inside one Android VPN service.

[**Download latest APK**](https://github.com/arttvad9r/detour/releases/latest) · [Quick start](docs/getting-started.md) · [Features](docs/features.md)

[![Android 10+](https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white)](https://github.com/arttvad9r/detour/releases/latest)
[![Android CI](https://github.com/arttvad9r/detour/actions/workflows/android.yml/badge.svg)](https://github.com/arttvad9r/detour/actions/workflows/android.yml)
[![Latest release](https://img.shields.io/github/v/release/arttvad9r/detour)](https://github.com/arttvad9r/detour/releases/latest)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

</div>

<table align="center">
  <tr>
    <td align="center"><img src="docs/assets/screenshots/home-connected.jpg" width="230" alt="Detour connected home screen"><br><strong>Connection at a glance</strong></td>
    <td align="center"><img src="docs/assets/screenshots/app-routes.jpg" width="230" alt="Detour per-app routes"><br><strong>Direct / VPN / DPI per app</strong></td>
    <td align="center"><img src="docs/assets/screenshots/vpn-profiles.jpg" width="230" alt="Detour VPN profiles"><br><strong>Multiple VPN profiles</strong></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/assets/screenshots/vpn-add-profile.jpg" width="230" alt="Detour add VPN profile sheet"><br><strong>Flexible profile imports</strong></td>
    <td align="center"><img src="docs/assets/screenshots/dpi-proxy-test.jpg" width="230" alt="Detour ByeDPI strategy test"><br><strong>ByeDPI strategy testing</strong></td>
    <td align="center"><img src="docs/assets/screenshots/appearance.jpg" width="230" alt="Detour appearance screen"><br><strong>Light, dark & community themes</strong></td>
  </tr>
</table>

<p align="center"><sub>Real Detour captures from an Android device. No generated product mockups.</sub></p>

## One tunnel. Three routes.

Most VPN apps make one decision for the entire phone. Detour makes it **per application**:

- **Direct** — use the normal network connection.
- **VPN** — route selected apps through VLESS Reality, an HTTPS subscription, WARP, or AmneziaWG.
- **DPI** — route selected apps through the native ByeDPI bypass path.

Apps that do not need a tunnel can stay out of it. Apps that need different treatment can get it without switching between separate clients.

## What Detour gives you

- **Per-app routing** with searchable app lists and Direct / VPN / DPI choices.
- **VPN profiles** for VLESS Reality, HTTPS subscriptions, WARP and AmneziaWG, including supported Amnezia `vpn://` imports.
- **Subscription control** with server selection, search, sorting, latency tests and runtime availability.
- **ByeDPI tools** with recommended/custom strategies and a Proxy Test workspace for comparing strategies against selected hosts.
- **Tunnel DNS** with Cloudflare, Google, AdGuard, custom IP, or HTTPS DNS-over-HTTPS.
- **Android integration** with a Quick Settings tile, connect-on-launch, foreground connection control, export/import, and multiple themes.

See the [full feature guide](docs/features.md) for exact supported formats and behavior.

## Install

Detour requires **Android 10 / API 29 or newer**. The current public GitHub release publishes an `arm64-v8a` APK.

1. Download the APK from the [latest GitHub release](https://github.com/arttvad9r/detour/releases/latest).
2. Add or import a VPN profile, then choose routes for the apps you care about.
3. Tap **Connect** and approve Android's VPN permission the first time.

For profile setup, DNS, DPI configuration and troubleshooting, use the [quick-start guide](docs/getting-started.md).

> [!NOTE]
> Detour is still pre-release software. Review imported configurations before relying on them for sensitive traffic.

## Open source by design

Detour does not require a project account and does not intentionally send project analytics or automatic crash reports. Sensitive profile material is stored locally with Android Keystore-backed encryption.

Detour-authored code is available under the [MIT License](LICENSE). Embedded components retain their own licenses and distribution obligations; see [third-party notices](THIRD_PARTY_NOTICES.md).

## Project docs

[Getting started](docs/getting-started.md) · [Features](docs/features.md) · [Architecture](docs/architecture.md) · [Testing](docs/testing.md) · [Privacy](PRIVACY.md) · [Security](SECURITY.md) · [Contributing](CONTRIBUTING.md)

<details>
<summary><strong>For developers and maintainers</strong></summary>

Detour is written in Kotlin with Jetpack Compose. Android `VpnService` owns the tunnel, Mihomo provides the main data plane, and native ByeDPI is integrated for DPI routing. Build, test, dependency pinning and release procedures live in the dedicated documentation instead of the product landing page:

- [Architecture](docs/architecture.md)
- [Testing and local verification](docs/testing.md)
- [Pinned native dependencies](docs/pins.md)
- [Release process](docs/releasing.md)
- [Release checklist](docs/release-checklist.md)

</details>
