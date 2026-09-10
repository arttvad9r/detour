# F-Droid build notes

Detour's F-Droid build is intended to be reproducible from source without network access during native compilation.

## Pinned native inputs

| Component | Pinned input |
| --- | --- |
| Mihomo | `ac017cdd246ce8bd547653d927e7bf77d7ee73d5` (`v1.19.30`) |
| ByeDPI | `ba532298de7b28cfe854aea83d061369d13ca290` |
| Go | `go1.26.7` |
| `golang.org/x/mobile` source used for `gobind` | `v0.0.0-20260821190718-4776eadac327` |
| Android NDK | `28.0.13004108` |
| Android compile SDK | `37` |
| Android build tools | `36.0.0` |

Mihomo and ByeDPI are checked out as exact git submodule revisions before the build sandbox is isolated. The Go module graph required by the Mihomo bridge is committed under `engine/mihomo/go/vendor/`. The offline builder compiles `gobind` from that vendored source and builds the JNI shared libraries directly inside the existing vendored Go module; it does not run `go get`, `go mod download`, `go mod tidy`, `go install ...@version`, or a remote `git fetch` during native compilation.

`engine/mihomo/build-offline.sh` and `engine/byedpi/build-offline.sh` preserve the existing reviewed Detour source transforms while replacing their remotes with local filesystem clones. CI exercises both wrappers with `GOPROXY=off`, `GOSUMDB=off`, disabled interactive Git authentication, and deliberately unusable HTTP/HTTPS proxy settings.

## Proposed F-Droid recipe

The proposed metadata is maintained at `packaging/fdroid/dev.detour.app.yml` until it is submitted to `fdroiddata`.

The recipe builds Go `1.26.7` from F-Droid's `go` srclib, selects NDK `28.0.13004108`, then invokes the release Gradle build in the same shell so the exact Go toolchain is inherited by Detour's native build tasks. The expected result is the unsigned release APK at `app/build/outputs/apk/release/app-release-unsigned.apk`.

## FLOSS and Anti-Features review

The Android dependency catalog and app manifest were reviewed for proprietary mobile SDKs. The current source tree does not include Firebase, Google Play Services, Crashlytics, or another bundled proprietary analytics/tracking SDK. Detour connects only to endpoints configured or selected by the user and includes optional protocol/provider presets such as WARP; the app is not dependent on a single non-free network service to function.

No Anti-Feature is currently identified by the upstream review. The final Anti-Features classification remains subject to F-Droid maintainer review.

## Release metadata

F-Droid can consume the upstream Fastlane metadata under `fastlane/metadata/android/`. Each release intended for F-Droid must include the changelog matching its Android versionCode before the release tag is created.

For the first F-Droid-ready release candidate, the proposed version is `0.1.2` / versionCode `1002`, with `fastlane/metadata/android/en-US/changelogs/1002.txt` and `fastlane/metadata/android/ru/changelogs/1002.txt` committed before tagging.
