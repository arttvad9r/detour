# F-Droid readiness

Detour keeps an upstream `.fdroid.yml` so the F-Droid build path can be tested before a submission to `fdroiddata`.

## Current candidate

The upstream recipe builds candidate version **0.2.1** (`versionCode 2001`) from `HEAD`. A future `fdroiddata` submission must replace `HEAD` with the exact release tag/commit. The repository has a historical `v0.2.0` tag that predates this build path, so automatic tag discovery is intentionally disabled in the upstream recipe.

## Reproducible native inputs

- Go: **1.26.7**, provided to F-Droid as source through `srclibs: go@go1.26.7`.
- Android NDK: **r28 / 28.0.13004108**.
- Mihomo: `ac017cdd246ce8bd547653d927e7bf77d7ee73d5` (`v1.19.30`) through the `third_party/mihomo` git submodule.
- ByeDPI: `ba532298de7b28cfe854aea83d061369d13ca290` through the `third_party/byedpi` git submodule.
- Go module source graph: committed under `engine/mihomo/go/vendor/`.
- `gobind`: compiled from the vendored `golang.org/x/mobile` source. No downloaded `gomobile` binary is required.

`engine/mihomo/build-offline.sh` and `engine/byedpi/build-offline.sh` only consume these prepared source inputs. The Android workflow has a dedicated native-build step that runs with `GOPROXY=off`, `GOSUMDB=off`, interactive git disabled, and HTTP/HTTPS/ALL proxy variables pointed at an unreachable local port. This check must stay green.

## F-Droid scanner review

The F-Droid scanner reports three binary-looking files in the committed Go vendor graph. They are source-controlled data inputs from FLOSS dependencies rather than downloaded executable artifacts, so the recipe keeps narrowly scoped `scanignore` entries for exactly these paths:

- `golang.org/x/net/publicsuffix/data/nodes` and `data/children` are generated lookup tables. The upstream BSD-licensed `publicsuffix/gen.go` generator writes both files from the Public Suffix List.
- `github.com/metacubex/zerotier-go/default_planet.bin` is a 570-byte ZeroTier public Earth trust-anchor payload. The MPL-2.0 dependency embeds it directly with `//go:embed default_planet.bin` and parses/validates it at runtime.

The scanner also reports `github.com/vmihailenco/msgpack/v5/package.json` because it is a package-manager manifest without a matching lockfile. Detour does not use that JavaScript packaging metadata for the Go build, so the F-Droid recipe removes only that file with `scandelete` before scanning/building rather than ignoring the warning.

These exceptions must stay path-specific. If any of these dependency versions or files change, review the scanner findings again instead of broadening `scanignore`.

## FLOSS and Anti-Features review

The Android application dependency graph contains AndroidX/Compose, Kotlin/Kotlinx, SnakeYAML and JSON libraries; it does not include Google Play Services, Firebase, proprietary analytics, advertising SDKs or crash-reporting SDKs. The manifest has no analytics/tracker service components.

Detour is not tied to a mandatory proprietary VPN backend: users import or configure their own VPN profiles and subscriptions, and routing/DPI features work locally. Optional DNS presets and support for third-party VPN services do not make those services mandatory. F-Droid packagers remain the final authority on Anti-Feature classification, so any future telemetry, mandatory hosted service, advertising or proprietary dependency must trigger a fresh review.

## Validation

Before submission, run the upstream recipe through the current F-Droid toolchain:

```text
fdroid readmeta
fdroid rewritemeta
fdroid lint
fdroid build
```

The final acceptance gate is an actual build in the F-Droid build-server environment, not merely a successful normal Gradle build.

## Submission sequence

1. Keep the offline native-engine check green in Android CI.
2. Validate `.fdroid.yml` with `readmeta`, `rewritemeta`, `lint` and `build` in the F-Droid build-server environment.
3. Merge the reproducible-build changes to `main`.
4. Create the next release tag from that `main` commit so the tag contains Fastlane metadata, pinned source inputs and the offline build path.
5. Copy the tested recipe to `fdroiddata`, replacing `commit: HEAD` with the release tag/commit and reviewing update-check policy.
