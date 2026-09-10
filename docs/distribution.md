# Distribution

This document tracks how Detour reaches users and the current readiness of each channel.

## Current channels

| Channel | Status | Notes |
| --- | --- | --- |
| GitHub Releases | Live | Canonical upstream distribution. Signed `arm64-v8a` APK plus SHA-256 checksum. |
| Obtainium | Compatible | Add `https://github.com/arttvad9r/detour` as a GitHub app source. Obtainium can follow GitHub Releases directly. |
| F-Droid.org | Ready for submission | `v0.2.1` has a validated offline/source build and upstream metadata. The remaining step is the normal `fdroiddata` inclusion/review process. |

## Obtainium

Detour uses canonical `vMAJOR.MINOR.PATCH` tags and publishes APK files in GitHub Releases, which makes the repository a straightforward Obtainium source.

1. Install Obtainium from its official project.
2. Add `https://github.com/arttvad9r/detour` as the app source URL.
3. Let Obtainium detect GitHub Releases and install the matching APK.

The public Detour release is currently `arm64-v8a` only. Keep release asset naming stable so update clients can continue to identify APK assets reliably.

## F-Droid upstream metadata

Store metadata lives under `fastlane/metadata/android/` with English fallback metadata and a Russian translation. Changelog `2001.txt` corresponds to release `0.2.1` / `versionCode 2001`.

F-Droid accepts PNG and JPEG screenshots from upstream metadata. The current metadata reuses the repository's lossless public product gallery so no private VPN profiles, subscription URLs or server credentials are exposed.

## F-Droid source-build status

The upstream reproducible-build blocker is resolved as of `v0.2.1`.

- Mihomo and ByeDPI are pinned as source submodules.
- The Go module source graph required by the native build is committed under `engine/mihomo/go/vendor/`.
- `gobind` is compiled from vendored `golang.org/x/mobile` source.
- Android CI verifies the native engines build with module/network resolution disabled.
- `.fdroid.yml` pins the Go source toolchain and NDK and contains path-specific scanner handling for reviewed FLOSS dependency data.
- The recipe passed metadata checks, scanner validation and a full `fdroid build --on-server` in the official F-Droid buildserver image.

See [F-Droid readiness](fdroid-readiness.md) for the exact inputs, scanner rationale and validation history.

Detour can now be proposed to the official `fdroiddata` repository. Acceptance is still subject to F-Droid maintainer review; upstream validation does not imply listing approval.

## Release trust

Release `v0.2.1` points at a commit contained in `main` that passed the full Android push workflow. The published APK is signed with the project release key, certificate-checked, 16 KB alignment-checked, size-checked and accompanied by a SHA-256 checksum.

For public communication, link to the GitHub Releases page rather than re-uploading APKs to file hosts.
