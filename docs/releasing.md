# Releasing

Signed distribution builds are created only from semantic-version tags in the form `vMAJOR.MINOR.PATCH`.

## Licensing

Detour-authored code is licensed under the repository root MIT `LICENSE`. Bundled third-party components retain their own licenses; see `THIRD_PARTY_NOTICES.md` and `docs/pins.md` for the exact pinned revisions and integration details.

The MIT license for Detour-authored code does not replace the GPL-3.0 terms of the embedded Mihomo engine. Every binary distribution must continue to satisfy all applicable third-party obligations and keep the corresponding license/source/build material available as required.

The release workflow validates that both `LICENSE` and `THIRD_PARTY_NOTICES.md` are present before building distributable APK/AAB artifacts. With the root MIT license now selected, this licensing-file preflight is satisfied as long as both files remain present.

## Version mapping

The tag controls both Android version fields for the release build:

- `versionName = MAJOR.MINOR.PATCH`;
- `versionCode = MAJOR * 1,000,000 + MINOR * 1,000 + PATCH`.

`MAJOR` must be at most 2100, while `MINOR` and `PATCH` must be at most 999. The computed `versionCode` must still be a positive Android-compatible integer no greater than 2,100,000,000, so not every `MAJOR = 2100` combination is valid. Normal development builds keep the fallback `0.1.0` / `1` values unless Gradle overrides are supplied explicitly.

A release tag must point to a commit contained in `main`, and that exact commit must already have a successful `Android` push workflow on `main`. The workflow refuses to publish unrelated or unverified revisions.

## Signing secrets

The signing key is never committed to the repository. Configure these GitHub Actions repository secrets before creating a release tag:

- `DETOUR_RELEASE_KEYSTORE_B64` — base64 encoding of the complete JKS/PKCS12 keystore file;
- `DETOUR_RELEASE_STORE_PASSWORD` — keystore password;
- `DETOUR_RELEASE_KEY_ALIAS` — signing key alias;
- `DETOUR_RELEASE_KEY_PASSWORD` — signing key password;
- `DETOUR_RELEASE_CERT_SHA256` — expected SHA-256 fingerprint of the release/upload signing certificate, with or without colons.

The certificate fingerprint can be obtained from a known-good signed APK with:

```bash
apksigner verify --verbose --print-certs detour.apk
```

or from a signed App Bundle with:

```bash
keytool -printcert -jarfile detour.aab
```

The workflow decodes the keystore into the runner temporary directory with restrictive file permissions. Gradle reads the temporary path and passwords from environment variables. Partial signing configuration is rejected, and both produced artifacts must match `DETOUR_RELEASE_CERT_SHA256` before publication.

For Google Play with Play App Signing, this certificate is the certificate used to sign the uploaded AAB (normally the upload key). Google Play can use a separate app-signing key for APKs delivered to users.

Keep an offline backup of the release/upload keystore and its passwords. Losing the required upload/signing key can prevent normal release updates and requires the applicable key-recovery process.

## Creating a release

After the intended commit is merged to `main` and the `main` CI is green, create and push a tag such as:

```bash
git checkout main
git pull --ff-only
git tag v0.1.0
git push origin v0.1.0
```

The `Release` workflow then:

1. validates distribution licensing files;
2. validates the tag and derives `versionName` / `versionCode`;
3. verifies that the commit belongs to `main` and has successful Android CI;
4. validates all signing secrets and the expected certificate fingerprint;
5. rebuilds the pinned native engine and ByeDPI binaries;
6. runs debug unit tests and lint, builds the debug APK, and compiles the instrumentation-test APK;
7. builds signed `arm64-v8a` release APK and Android App Bundle (`.aab`) artifacts;
8. runs the pinned engine tests plus source and shipped-binary `govulncheck` scans against the exact Mihomo AAR produced for the release;
9. verifies the APK signature and signing-certificate identity;
10. verifies the AAB JAR signature and signing-certificate identity;
11. enforces the exact `arm64-v8a` ABI set for both distribution paths, plus 16 KB ELF alignment and the 30 MiB budget for the APK;
12. writes separate SHA-256 checksums for the APK and AAB;
13. uploads both artifacts, both checksums and the APK size report as a workflow artifact;
14. creates or updates the matching GitHub Release and attaches the APK, AAB and their checksums without overwriting existing release assets.

The published files are named:

- `detour-MAJOR.MINOR.PATCH-arm64.apk` — direct-install/GitHub distribution artifact;
- `detour-MAJOR.MINOR.PATCH-arm64.aab` — Google Play upload artifact.

## Local signed build

The same Gradle paths can be exercised locally without putting credentials in Gradle files:

```bash
export DETOUR_RELEASE_KEYSTORE=/absolute/path/to/release.keystore
export DETOUR_RELEASE_STORE_PASSWORD='...'
export DETOUR_RELEASE_KEY_ALIAS='...'
export DETOUR_RELEASE_KEY_PASSWORD='...'

./gradlew :app:assembleRelease :app:bundleRelease \
  -PdetourReleaseAbi=arm64-v8a \
  -PdetourVersionName=0.1.0 \
  -PdetourVersionCode=1000
```

Run `bash engine/vulnscan.sh` with the pinned `govulncheck` tool available. Verify the APK with `apksigner verify --verbose --print-certs` and the AAB with `jarsigner -verify` plus `keytool -printcert -jarfile`, then compare both SHA-256 certificate fingerprints with the expected release/upload identity before distributing locally produced artifacts. Android signing certificates are normally self-signed, so `jarsigner -strict` is intentionally not used as a trust-chain policy; signer identity is enforced by the exact SHA-256 certificate fingerprint instead.
