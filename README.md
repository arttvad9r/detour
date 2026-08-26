# Detour

Android VPN-клиент: локальный прокси на базе mihomo + обход блокировок через ByeDPI.

## Build

```bash
nix-shell --run "./gradlew :app:assembleDebug"
```

The Gradle build creates both native artifacts before packaging: the mihomo
AAR and the ByeDPI libraries. `shell.nix` supplies Java, Android SDK/NDK,
Go, and gomobile. It intentionally accepts `pkgs` as an argument so callers
can provide a pinned nixpkgs revision; this repository does not currently
contain a nixpkgs lock or flake.

The wrapper verifies the Gradle distribution checksum. Dependency verification
metadata is committed in `gradle/verification-metadata.xml`; CI also runs
`govulncheck` over the embedded Go engine and fails on reachable findings.

Native source revisions and their build patches are recorded in
[docs/pins.md](docs/pins.md). IPv6 is captured by the TUN and explicitly
rejected by the engine, so selected applications cannot silently bypass the
VPN over the physical IPv6 interface.

Unit tests and historical device evidence remain in [docs/testing.md](docs/testing.md).
The old corrective-pass and design documents under `docs/superpowers/` are
archived project history, not current build instructions.
