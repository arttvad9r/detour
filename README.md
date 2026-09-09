<p align="center">
  <strong>English</strong> · <a href="README.ru.md">Русский</a>
</p>

<div align="center">

# Detour

### Per-app routing for Android

**Direct · VPN · DPI**

Open-source Android networking for choosing **how each app reaches the internet**.
Keep selected apps direct, route others through VPN, and use ByeDPI only where needed — inside one Android VPN service.

[**Download APK**](https://github.com/arttvad9r/detour/releases/latest) · [Quick start](docs/getting-started.md) · [Features](docs/features.md)

[![Android 10+](https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white)](https://github.com/arttvad9r/detour/releases/latest)
[![Android CI](https://github.com/arttvad9r/detour/actions/workflows/android.yml/badge.svg)](https://github.com/arttvad9r/detour/actions/workflows/android.yml)
[![Latest release](https://img.shields.io/github/v/release/arttvad9r/detour)](https://github.com/arttvad9r/detour/releases/latest)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

</div>

<p align="center">
  <img src="docs/assets/screenshots/settings.jpg" width="200" alt="Detour settings">
  <img src="docs/assets/screenshots/dns.jpg" width="200" alt="Detour DNS settings">
</p>
<p align="center">
  <img src="docs/assets/screenshots/dpi-bypass.jpg" width="200" alt="Detour DPI bypass">
  <img src="docs/assets/screenshots/appearance.jpg" width="200" alt="Detour themes">
</p>
<p align="center"><sub>Real Detour captures from an Android device. No generated product mockups and no private VPN configuration data.</sub></p>

## One tunnel. Three routes.

Detour assigns a route **per application**, instead of forcing the whole phone through one path:

- **Direct** — normal network connection.
- **VPN** — VLESS Reality, HTTPS subscriptions, WARP or AmneziaWG.
- **DPI** — native ByeDPI bypass for selected apps.

## What it does

- Per-app **Direct / VPN / DPI** routing with app search.
- VLESS Reality, HTTPS subscriptions, WARP and AmneziaWG profiles, including supported Amnezia `vpn://` imports.
- Subscription server selection, sorting, latency checks and live availability.
- ByeDPI recommended/custom strategies and a strategy-testing workspace.
- Tunnel DNS: Cloudflare, Google, AdGuard, custom IP or HTTPS DoH.
- Quick Settings tile, connect-on-launch, export/import and multiple themes.

[See the full feature guide →](docs/features.md)

## Install

Detour requires **Android 10 / API 29+**. The current public GitHub release provides an `arm64-v8a` APK.

1. Download the APK from [GitHub Releases](https://github.com/arttvad9r/detour/releases/latest).
2. Add or import a VPN profile and choose routes for your apps.
3. Tap **Connect** and approve Android's VPN permission.

[Getting started →](docs/getting-started.md)

> [!NOTE]
> Detour is pre-release software and is currently distributed outside Google Play. Review imported configurations and install release artifacts only from this repository.

## Open source by design

No Detour account is required. Detour does not intentionally send project analytics or automatic crash reports. Sensitive profile material is stored locally with Android Keystore-backed encryption.

Detour-authored code is licensed under [MIT](LICENSE). Embedded components keep their own licenses; see [third-party notices](THIRD_PARTY_NOTICES.md).

**Docs:** [Getting started](docs/getting-started.md) · [Features](docs/features.md) · [Architecture](docs/architecture.md) · [Testing](docs/testing.md) · [Privacy](PRIVACY.md) · [Security](SECURITY.md) · [Contributing](CONTRIBUTING.md)

<details>
<summary><strong>For developers and maintainers</strong></summary>

Detour is built with Kotlin and Jetpack Compose. Android `VpnService` owns the tunnel, Mihomo provides the main data plane, and native ByeDPI handles the DPI route.

[Architecture](docs/architecture.md) · [Testing](docs/testing.md) · [Dependency pins](docs/pins.md) · [Releasing](docs/releasing.md)

</details>
