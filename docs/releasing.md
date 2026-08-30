# Releasing

Signed distribution builds are created only from semantic-version tags in the form `vMAJOR.MINOR.PATCH`.

## Version mapping

The tag controls both Android version fields for the release build:

- `versionName = MAJOR.MINOR.PATCH`;
- `versionCode = MAJOR * 1,000,000 + MINOR * 1,000 + PATCH`.

`MINOR` and `PATCH` must be at most 999. The computed `versionCode` must be a positive Android-compatible integer. Normal development builds keep the fallback `0.1.0` / `1` values unless Gradle overrides are supplied explicitly.

A release tag must point to a commit that is contained in `master`. The workflow refuses to publish tags from an unrelated branch.

## Signing secrets

The signing key is never committed to the repository. Configure these GitHub Actions repository secrets before creating a release tag:

- `DETOUR_RELEASE_KEYSTORE_B64` — base64 encoding of the complete JKS/PKCS12 keystore file;
- `DETOUR_RELEASE_STORE_PASSWORD` — keystore password;
- `DETOUR_RELEASE_KEY_ALIAS` — signing key alias;
- `DETOUR_RELEASE_KEY_PASSWORD` — signing key password.

The workflow decodes the keystore into the runner temporary directory with restrictive file permissions. Gradle reads the temporary path and passwords from environment variables. Partial signing configuration is rejected rather than falling back silently.

Keep an offline backup of the release keystore and its passwords. Losing the signing key prevents compatible updates to installations signed with that key.

## Creating a release

After the intended commit is merged to `master` and the `master` CI is green, create and push a tag such as:

```bash
git checkout master
git pull --ff-only
git tag v0.1.0
git push origin v0.1.0
```

The `Release` workflow then:

1. validates the tag and derives `versionName` / `versionCode`;
2. validates that all signing secrets are present;
3. rebuilds the pinned native engine and ByeDPI binaries;
4. builds a signed `arm64-v8a` release APK;
5. runs the pinned engine tests plus source and shipped-binary `govulncheck` scans against the exact Mihomo AAR produced for the release;
6. verifies the APK signature with Android `apksigner`;
7. enforces the exact `arm64-v8a` ABI set and the 30 MiB APK budget;
8. writes a SHA-256 checksum;
9. uploads the APK, checksum and size report as a workflow artifact;
10. creates or updates the matching GitHub Release and attaches the APK and checksum.

The published APK is named `detour-MAJOR.MINOR.PATCH-arm64.apk`.

## Local signed build

The same Gradle path can be exercised locally without putting credentials in Gradle files:

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

Run `bash engine/vulnscan.sh` with the pinned `govulncheck` tool available, then run `apksigner verify --verbose --print-certs` on the resulting APK before distributing a locally produced build.
