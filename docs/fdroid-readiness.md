# F-Droid readiness

Detour keeps an upstream `.fdroid.yml` so the F-Droid build path is continuously testable before and after submission to `fdroiddata`.

## Current candidate

**v0.2.1** (`versionCode 2001`) is the first validated F-Droid-ready release. The upstream recipe points at the exact `v0.2.1` tag. That tag resolves to release commit `f2588120ce5df15c57d082fe9b23cdc6a2fa9315`, which contains the Fastlane metadata, pinned source inputs and offline native build path.

The repository also contains a historical `v0.2.0` tag that predates this reproducible build path. Automatic update/tag discovery therefore remains disabled in the upstream recipe until the long-term `fdroiddata` update policy is reviewed.

## Reproducible native inputs

- Go: **1.26.7**, provided to F-Droid as source through `srclibs: go@go1.26.7`.
- Android NDK: **r28 / 28.0.13004108**.
- Mihomo: `ac017cdd246ce8bd547653d927e7bf77d7ee73d5` (`v1.19.30`) through the `third_party/mihomo` git submodule.
- ByeDPI: `ba532298de7b28cfe854aea83d061369d13ca290` through the `third_party/byedpi` git submodule.
- Go module source graph: committed under `engine/mihomo/go/vendor/`.
- `gobind`: compiled from the vendored `golang.org/x/mobile` source. No downloaded `gomobile` binary is required.

`engine/mihomo/build-offline.sh` and `engine/byedpi/build-offline.sh` only consume these prepared source inputs. Android CI has a dedicated native-build step that runs with `GOPROXY=off`, `GOSUMDB=off`, interactive git disabled, and HTTP/HTTPS/ALL proxy variables pointed at an unreachable local port. This check is a required release gate.

## F-Droid scanner review

The F-Droid scanner reports three binary-looking files in the committed Go vendor graph. They are source-controlled data inputs from FLOSS dependencies rather than downloaded executable artifacts, so the recipe keeps narrowly scoped `scanignore` entries for exactly these paths:

- `golang.org/x/net/publicsuffix/data/nodes` and `data/children` are generated lookup tables. The upstream BSD-licensed `publicsuffix/gen.go` generator writes both files from the Public Suffix List.
- `github.com/metacubex/zerotier-go/default_planet.bin` is a small ZeroTier public Earth trust-anchor payload. The MPL-2.0 dependency embeds it directly with `//go:embed default_planet.bin` and parses/validates it at runtime.

The scanner also reports `github.com/vmihailenco/msgpack/v5/package.json` because it is a package-manager manifest without a matching lockfile. Detour does not use that JavaScript packaging metadata for the Go build, so the F-Droid recipe removes only that file with `scandelete` before scanning/building rather than ignoring the warning.

These exceptions must stay path-specific. If any of these dependency versions or files change, review the scanner findings again instead of broadening `scanignore`.

## FLOSS and Anti-Features review

The Android application dependency graph contains AndroidX/Compose, Kotlin/Kotlinx, SnakeYAML and JSON libraries; it does not include Google Play Services, Firebase, proprietary analytics, advertising SDKs or crash-reporting SDKs. The manifest has no analytics/tracker service components.

Detour is not tied to a mandatory proprietary VPN backend: users import or configure their own VPN profiles and subscriptions, and routing/DPI features work locally. Optional DNS presets and support for third-party VPN services do not make those services mandatory. F-Droid packagers remain the final authority on Anti-Feature classification, so any future telemetry, mandatory hosted service, advertising or proprietary dependency must trigger a fresh review.

## Validation completed for v0.2.1

The candidate passed all of the following before release:

- `fdroid readmeta`
- `fdroid rewritemeta`
- `fdroid lint`
- F-Droid `fetchsrclibs`
- F-Droid scanner with the documented path-specific exceptions
- a full `fdroid build --on-server` inside the official F-Droid buildserver image
- Android offline native-engine build
- Android unit tests, lint and dependency verification
- native race detector and vulnerability scan
- APK size and 16 KB ELF alignment checks
- signed arm64 APK verification
- instrumentation tests on Android 16 and Android 17

The GitHub release `v0.2.1` was then built from the same release commit and published with the signed APK and SHA-256 checksum.

## Remaining F-Droid work

The upstream reproducible-build blocker is closed. What remains is the normal downstream inclusion process:

1. Copy the tested recipe to `fdroiddata` for `dev.detour.app`.
2. Keep `commit: v0.2.1` (or the exact release commit) in the submission.
3. Review the preferred `UpdateCheckMode`/`AutoUpdateMode` policy with F-Droid maintainers.
4. Submit the `fdroiddata` merge request and address packager review feedback.

Future dependency or native-engine updates must keep the F-Droid buildserver CI green and re-review scanner exceptions.
