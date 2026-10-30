# Releasing

Signed distribution builds are created only from semantic-version tags in the form `vMAJOR.MINOR.PATCH`.

## Licensing

Detour-authored code is licensed under the repository root MIT `LICENSE`. Bundled third-party components retain their own licenses; see `THIRD_PARTY_NOTICES.md` and `docs/pins.md` for the exact pinned revisions and integration details.

The MIT license for Detour-authored code does not replace the GPL-3.0 terms of the embedded Mihomo engine. Every binary distribution must continue to satisfy all applicable third-party obligations and keep the corresponding license/source/build material available as required.

The release workflow validates that both `LICENSE` and `THIRD_PARTY_NOTICES.md` are present before building a distributable APK. With the root MIT license now selected, this licensing-file preflight is satisfied as long as both files remain present.

## Version mapping

The tag controls both Android version fields for the release build:

- `versionName = MAJOR.MINOR.PATCH`;
- `versionCode = MAJOR * 1,000,000 + MINOR * 1,000 + PATCH`.

`MAJOR` must be at most 2100, while `MINOR` and `PATCH` must be at most 999. The computed `versionCode` must still be a positive Android-compatible integer no greater than 2,100,000,000, so not every `MAJOR = 2100` combination is valid. Normal development builds keep the fallback `0.1.0` / `1` values unless Gradle overrides are supplied explicitly.

A release tag must point to a commit contained in `main`. That exact commit must have successful full `Android` validation: a manual workflow run on `dev` or `main`, including Android 16 and Android 17 instrumentation. Legacy successful `main` push runs are also accepted because they included the same instrumentation. An automatic `dev` push without runtime tests is not sufficient.

Before tagging, complete [the public release checklist](release-checklist.md).

## Signing secrets

The signing key is never committed to the repository. Configure these GitHub Actions repository secrets before creating a release tag:

- `DETOUR_RELEASE_KEYSTORE_B64` — base64 encoding of the complete JKS/PKCS12 keystore file;
- `DETOUR_RELEASE_STORE_PASSWORD` — keystore password;
- `DETOUR_RELEASE_KEY_ALIAS` — signing key alias;
- `DETOUR_RELEASE_KEY_PASSWORD` — signing key password;
- `DETOUR_RELEASE_CERT_SHA256` — expected SHA-256 fingerprint of the release signing certificate, with or without colons.

The certificate fingerprint can be obtained from a known-good signed APK with:

```bash
apksigner verify --verbose --print-certs detour.apk
```

The workflow decodes the keystore into the runner temporary directory with restrictive file permissions. Gradle reads the temporary path and passwords from environment variables. Partial signing configuration is rejected, and the produced APK certificate must match `DETOUR_RELEASE_CERT_SHA256` before publication.

Keep an offline backup of the release keystore and its passwords. Losing the signing key prevents compatible updates to installations signed with that key.

## Creating a release

The owner approves the release; a request to release is that approval and authorizes the merge/tag/push. A pull request is optional.

1. Bring any `main` documentation/housekeeping changes into `dev` first, so `main` is an ancestor of the candidate.
2. Run the `Android` workflow manually on the candidate in `dev`, and verify that the successful run's commit SHA matches the intended release revision. Perform focused runtime smoke where the changed behavior needs it.
3. Fast-forward `main` to that tested revision, create the version tag on it and push `main` before the tag. If either branch has advanced, reconcile it and validate any changed app/build inputs before releasing. Never force-push to make the release fit.

For example, after the candidate is approved and fully validated (replace the example version):

```bash
git fetch origin
git switch main
git merge --ff-only origin/main
git merge --ff-only origin/dev
git tag v0.1.0
git push origin main
git push origin v0.1.0
```

Continue development from `dev`; no separate release PR is required. Documentation and repository maintenance can be applied to both `main` and `dev` between app releases without a tag or version bump. Do not bring unreleased app, dependency or packaging changes into `main` through that exception.

The `Release` workflow then:

1. validates distribution licensing files;
2. validates the tag and derives `versionName` / `versionCode`;
3. verifies that the commit belongs to `main` and has successful full Android validation;
4. validates all signing secrets and the expected certificate fingerprint;
5. rebuilds the pinned native engine and ByeDPI binaries;
6. runs debug unit tests and lint, builds the debug APK, and compiles the instrumentation-test APK;
7. builds a signed `arm64-v8a` release APK;
8. runs the pinned engine tests plus source and shipped-binary `govulncheck` scans against the exact Mihomo AAR produced for the release;
9. verifies the APK signature and signing-certificate identity;
10. enforces the exact `arm64-v8a` ABI set and 16 KB ELF alignment, and reports APK size;
11. writes a SHA-256 checksum;
12. uploads the APK, checksum and size report as a workflow artifact;
13. creates or updates the matching GitHub Release and attaches the APK and checksum without overwriting existing release assets.

The published APK is named `detour-MAJOR.MINOR.PATCH-arm64.apk`.

## Local signed build

The same Gradle path can be exercised locally. Supply signing material through environment variables; keep the key/passwords outside tracked files and do not print them in logs:

```bash
export DETOUR_RELEASE_KEYSTORE=/absolute/path/to/release.keystore
export DETOUR_RELEASE_STORE_PASSWORD='...'
export DETOUR_RELEASE_KEY_ALIAS='...'
export DETOUR_RELEASE_KEY_PASSWORD='...'

./gradlew :app:assembleRelease \
  -PdetourReleaseAbi=arm64-v8a \
  -PdetourVersionName=0.1.0 \
  -PdetourVersionCode=1000
```

Run `bash engine/vulnscan.sh` with the pinned `govulncheck` tool available, then run `apksigner verify --verbose --print-certs` on the resulting APK and compare the SHA-256 certificate fingerprint with the expected release identity before distributing a locally produced build.
