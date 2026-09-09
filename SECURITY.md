# Security policy

## Supported versions

Security fixes are developed on `main` and included in the newest published Detour release. Older releases may not receive backports.

## Reporting a vulnerability

Please avoid publishing exploitable security details, VPN credentials, invitation links, private keys, preshared keys, Reality credentials, or user traffic in a public issue.

Use GitHub's private vulnerability reporting / Security Advisory flow for this repository when available. If that UI is unavailable, open a minimal public issue that contains no exploit details or secrets and asks the maintainer for a private reporting channel.

A useful report includes the affected Detour version/commit, Android version/device class, impact, reproducible steps using synthetic credentials, and any relevant sanitized logs.

## Security boundaries

- Android `VpnService` owns the device TUN and per-app VPN allow-list.
- VPN profile credentials are stored in encrypted DataStore values backed by Android Keystore.
- Credential editors use Android secure-window protections where appropriate.
- Imported configuration is validated before it becomes active routing material.
- CI includes unit/lint/build checks, native-engine race testing, vulnerability scanning, release APK verification and Android instrumentation.

These controls reduce risk but do not make Detour, its dependencies, or third-party VPN endpoints immune to vulnerabilities.
