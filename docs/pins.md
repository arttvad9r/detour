# Native pins

Detour builds its native dependencies from exact upstream revisions. Do not replace these with floating tags/branches in release builds.

## Mihomo

- Version: `v1.19.30`
- Commit: `ac017cdd246ce8bd547653d927e7bf77d7ee73d5`
- Android AAR build: `engine/mihomo/build.sh`
- Go build tag: `with_gvisor`

`engine/mihomo/build.sh` resets the checkout to that exact commit before every build and applies fail-closed source transforms. Every transform matches an expected pinned-source layout and aborts the build if upstream code no longer matches the reviewed anchor.

The current Detour embedding/compatibility patches are:

1. Android package-rule discovery inside Mihomo is disabled because a normal application cannot read `/data/system/packages.xml`.
2. Process/UID lookup is bridged to the Android host through `Engine.setProcessResolver` and `ConnectivityManager.getConnectionOwnerUid`.
3. Named custom listeners are closed together with the TUN across engine stop/restart cycles.
4. Invalid VLESS gRPC + XTLS Vision combinations emitted by some subscriptions are normalized to Mihomo-compatible semantics.
5. Empty VLESS gRPC service names use Xray-compatible `//Tun` semantics.
6. The embedded REALITY client advertises the minimum client version required by current Xray deployments supported by Detour.
7. URLTest exposes bounded, sanitized error classification/text to the Android bridge without logging subscription credentials.
8. Mihomo configuration apply propagates recovered panics back to the embedding bridge instead of silently leaving a partially applied runtime.
9. Mihomo's process-wide log level is backed by `sync/atomic`; the pinned upstream revision otherwise races when WireGuard/AmneziaWG logging overlaps a configuration-level change during restart.

The host app resolves package→UID deterministically and remains the primary owner of per-app routing. Engine-side UID attribution is a supporting mechanism; PROCESS-NAME routing is not relied on because shared UIDs and Android visibility rules make it unsuitable as the product contract.

The embedded API is intentionally small: install the resolver, start generated YAML, report readiness, stop, query subscription runtime state, run detached subscription latency probes, and forward logs. Mihomo still implements the current data plane: gVisor TUN, DNS, TCP/UDP forwarding, VLESS/Reality, WireGuard/AmneziaWG and outbound chaining.

With a host-supplied TUN file descriptor, the Android `VpnService.Builder` configures the IPv4 tunnel interface itself. Detour intentionally does not configure an IPv6 address, route or DNS server on the Android VPN; Android therefore blocks that unconfigured address family for routed apps. The generated Mihomo rules also reject IPv6 defensively, while the active routed data plane remains IPv4-only.

## ByeDPI

- CLI version reported by upstream source: `17.3`
- Commit: `ba532298de7b28cfe854aea83d061369d13ca290`
- Reference consumer: ByeByeDPI snapshot `01b080e2fe41898d8371495a9db887da54e28798`
- Build script: `engine/byedpi/build.sh`
- Android targets: `arm64-v8a`, `x86_64`
- API baseline: Android 21 native target

This is a post-`v0.17.3` upstream revision selected to match the exact ByeDPI source used by the reviewed ByeByeDPI proxy-test corpus. The build fetches and resets to the commit directly; it does not trust a moving branch or tag.

The build produces the dynamic-bionic `ciadpi` executable and packages it through Android `jniLibs` under a `.so` filename so it is delivered with the APK. `DpiBackend` executes it as a child process and exposes its loopback SOCKS listener to the embedded engine.

Detour applies a build-time source transform to the exact pinned ByeDPI commit so the internal SOCKS listener requires SOCKS5 RFC1929 username/password authentication. The process-ephemeral credentials are supplied to the child over stdin rather than argv or environment, and the transform uses exact single-match anchors so source drift fails the build instead of producing a partially patched binary. Loopback binding is therefore not treated as an authentication boundary by itself.

## Toolchain

The CI/release contract currently pins:

- JDK 17;
- compile SDK 37 and target SDK 36;
- build-tools 36.0.0;
- NDK 28.0.13004108;
- Go 1.26.7;
- `golang.org/x/mobile/cmd/gomobile@v0.0.0-20260821190718-4776eadac327`;
- `golang.org/x/vuln/cmd/govulncheck@v1.7.0`.

GitHub Actions themselves are pinned by commit SHA in `.github/workflows/android.yml` and `.github/workflows/release.yml`.

## Updating a native pin

A native dependency update is not a version-only change. Before merging it:

1. update the exact version/commit in the build script;
2. re-check every Detour source transform against the new upstream source and preserve fail-closed anchors;
3. run the full Gradle gate, `go mod verify`, the full `go test -race` embedded-engine gate and `engine/vulnscan.sh`;
4. run a fresh device smoke covering connect/disconnect, subscription selection persistence and Direct/VPN/DPI routing;
5. update this file with the new exact revision and any changed patch rationale.

Generated AAR/SO outputs are build artifacts and remain ignored by git.
