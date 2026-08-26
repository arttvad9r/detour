# Detour

Android VPN-клиент: локальный прокси на базе mihomo + обход блокировок через ByeDPI.

Актуальный corrective pass: [docs/superpowers/plans/2026-08-26-detour-corrective-pass.md](docs/superpowers/plans/2026-08-26-detour-corrective-pass.md).

Сборка:

```bash
nix-shell --run "./gradlew :app:assembleDebug"
```

`shell.nix` supplies Java, Android SDK/NDK and pinned `gomobile`; native
sources are pinned in [docs/pins.md](docs/pins.md). The VPN is deliberately
IPv4-only: selected applications never receive an IPv6 TUN route.

Unit tests and current test evidence are documented in
[docs/testing.md](docs/testing.md). Device checks remain separate from
automated checks.
