# Distribution

This document tracks how Detour reaches users and what remains before each channel is considered production-ready.

## Current channels

| Channel | Status | Notes |
| --- | --- | --- |
| GitHub Releases | Live | Canonical upstream distribution. Signed `arm64-v8a` APK plus SHA-256 checksum. |
| Obtainium | Compatible | Add `https://github.com/arttvad9r/detour` as a GitHub app source. Obtainium can follow GitHub Releases directly. |
| F-Droid.org | Preparation in progress | Upstream Fastlane metadata is present, but the native build still needs an offline/source-prefetch path before submission. |

## Obtainium

Detour uses canonical `vMAJOR.MINOR.PATCH` tags and publishes APK files in GitHub Releases, which makes the repository a straightforward Obtainium source.

1. Install Obtainium from its official project.
2. Add `https://github.com/arttvad9r/detour` as the app source URL.
3. Let Obtainium detect GitHub Releases and install the matching APK.

The public Detour release is currently `arm64-v8a` only. Keep the release asset naming stable so update clients can continue to identify APK assets reliably.

## F-Droid upstream metadata

Store metadata lives under `fastlane/metadata/android/` with English fallback metadata and a Russian translation. Changelog `1001.txt` corresponds to release `0.1.1`, whose release version-code formula yields `1001`.

F-Droid accepts PNG and JPEG screenshots from upstream metadata. The current metadata reuses the repository's lossless public product gallery so no private VPN profiles, subscription URLs or server credentials are exposed.

## F-Droid blocker: offline native build

Do not submit Detour to `fdroiddata` yet.

The current build is source-based, but it still performs network fetches during native compilation:

- `engine/mihomo/build.sh` clones and fetches the pinned Mihomo revision at build time.
- `engine/byedpi/build.sh` clones and fetches the pinned ByeDPI revision at build time.
- release CI installs the pinned `gomobile` tool before the Gradle build.
- Go module dependencies also need a build-server-compatible prefetch/vendor strategy.

Before opening an F-Droid inclusion merge request:

1. Convert Mihomo and ByeDPI inputs to declared/prefetched source inputs or committed vendor/source snapshots without relying on network access during the app build.
2. Make the pinned Go/gomobile toolchain reproducible in the F-Droid build environment.
3. Verify every dependency is FLOSS and disclose any F-Droid anti-features if applicable.
4. Test a proposed `fdroiddata` recipe with `fdroid readmeta`, `fdroid rewritemeta`, `fdroid lint` and `fdroid build` in the official build-server environment.
5. Tag a new release whose commit already contains the Fastlane metadata; F-Droid reads upstream metadata from the release it knows.

Only after the build passes should `dev.detour.app` be proposed to the official F-Droid repository.

## Release trust

The GitHub release workflow requires release tags to point to `main`, requires successful Android CI, verifies signing material, validates the APK signature, checks alignment and size, and publishes a SHA-256 checksum next to the APK.

For public communication, link to the GitHub Releases page rather than re-uploading APKs to file hosts.
