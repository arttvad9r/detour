# Native pins

Detour builds its native dependencies from exact upstream revisions. Do not replace these with floating tags/branches in release builds.

## Mihomo

- Version: `v1.19.30`
- Commit: `ac017cdd246ce8bd547653d927e7bf77d7ee73d5`
- Android AAR build: `engine/mihomo/build.sh`
- Go build tag: `with_gvisor`

Detour applies two Android embedding adjustments during the build:

1. Android package-rule discovery inside Mihomo is disabled because a normal application cannot read `/data/system/packages.xml`.
2. Process/UID lookup is bridged to the Android host through `Engine.setProcessResolver` and `ConnectivityManager.getConnectionOwnerUid`.

The host app resolves package→UID deterministically and remains the primary owner of per-app routing. Engine-side UID attribution is a supporting mechanism; PROCESS-NAME routing is not relied on because shared UIDs and Android visibility rules make it unsuitable as the product contract.

The embedded API is intentionally small: install the resolver, start generated YAML, report readiness, stop, and forward logs. Mihomo still implements the current data plane: gVisor TUN, DNS, TCP/UDP forwarding, VLESS/Reality, WireGuard/AmneziaWG and outbound chaining.

With a host-supplied TUN file descriptor, the Android `VpnService.Builder` must configure the fake-IP interface address itself. The current tunnel uses the validated IPv4 `/30` arrangement. Selected IPv6 traffic is captured and explicitly rejected while the routed data plane remains IPv4-only.

## ByeDPI

- Version: `v0.17.3`
- Commit: `7efde1b1296eaaa187b70e951894dde17527489c`
- Build script: `engine/byedpi/build.sh`
- Android targets: `arm64-v8a`, `x86_64`
- API baseline: Android 21 native target

The build produces the dynamic-bionic `ciadpi` executable and packages it through Android `jniLibs` under a `.so` filename so it is delivered with the APK. `DpiBackend` executes it as a child process and exposes its loopback SOCKS listener to the embedded engine.

Detour applies a build-time source transform to the exact pinned ByeDPI commit so the internal SOCKS listener requires SOCKS5 RFC1929 username/password authentication. The process-ephemeral credentials are supplied to the child over stdin rather than argv or environment, and the transform uses exact single-match anchors so source drift fails the build instead of producing a partially patched binary. Loopback binding is therefore not treated as an authentication boundary by itself.

## Toolchain

The CI/release contract currently pins:

- JDK 17;
- Android platform/target 36;
- build-tools 36.0.0;
- NDK 28.0.13004108;
- Go 1.26.7;
- `golang.org/x/mobile/cmd/gomobile@v0.0.0-20260821190718-4776eadac327`;
- `golang.org/x/vuln/cmd/govulncheck@v1.7.0`.

GitHub Actions themselves are pinned by commit SHA in `.github/workflows/android.yml`.

## Updating a native pin

A native dependency update is not a version-only change. Before merging it:

1. update the exact version/commit in the build script;
2. re-check every Detour patch against the new upstream source;
3. run the full Gradle gate and `engine/vulnscan.sh`;
4. run a fresh device smoke covering connect/disconnect and Direct/VPN/DPI routing;
5. update this file with the new exact revision and any changed patch rationale.

Generated AAR/SO outputs are build artifacts and remain ignored by git.