# Distribution

Detour is distributed only through GitHub Releases.

## Current channels

| Channel | Status | Notes |
| --- | --- | --- |
| GitHub Releases | Live | Canonical upstream distribution. Signed `arm64-v8a` APK plus SHA-256 checksum. |
| Obtainium | Compatible | Add `https://github.com/arttvad9r/detour` as a GitHub app source. Obtainium can follow GitHub Releases directly. |

## Obtainium

Detour uses canonical `vMAJOR.MINOR.PATCH` tags and publishes APK files in GitHub Releases, which makes the repository a straightforward Obtainium source.

1. Install Obtainium from its official project.
2. Add `https://github.com/arttvad9r/detour` as the app source URL.
3. Let Obtainium detect GitHub Releases and install the matching APK.

The public Detour release is currently `arm64-v8a` only. Keep release asset naming stable so update clients can continue to identify APK assets reliably.

## Release trust

Each release tag points at a commit contained in `main` that passed the full Android push workflow. The published APK is signed with the project release key, certificate-checked, 16 KB alignment-checked, size-checked and accompanied by a SHA-256 checksum.

For public communication, link to the GitHub Releases page rather than re-uploading APKs to file hosts.
