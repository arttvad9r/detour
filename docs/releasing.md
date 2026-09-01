# Releasing

Signed distribution builds are created only from semantic-version tags in the form `vMAJOR.MINOR.PATCH`.

## Version mapping

The tag controls both Android version fields for the release build:

- `versionName = MAJOR.MINOR.PATCH`;
- `versionCode = MAJOR * 1,000,000 + MINOR * 1,000 + PATCH`.

`MAJOR` must be at most 2100, while `MINOR` and `PATCH` must be at most 999. The computed `versionCode` must still be a positive Android-compatible integer no greater than 2,100,000,000, so not every `MAJOR = 2100` combination is valid. Normal development builds keep the fallback `0.1.0` / `1` values unless Gradle overrides are supplied explicitly.

A release tag must point to a commit that is contained in `master`. The workflow refuses to publish tags from an unrelated branch.

## Distribution artifacts

Each release produces two signed artifacts from the same source revision and version:

- `detour-MAJOR.MINOR.PATCH-arm64.apk` — direct-install APK restricted to `arm64-v8a`;
- `detour-MAJOR.MINOR.PATCH.aab` — Android App Bundle containing the supported `arm64-v8a` and `x86_64` native ABIs for Play-compatible distribution.

The APK is verified with Android `apksigner`. The AAB is verified with `tools/verify_aab_signature.sh`, which uses `jarsigner -verify` for signature/integrity validation and additionally requires the positive `jar verified.` result because `jarsigner` alone exits successfully for unsigned JARs. Android application signing certificates are normally self-signed, so the verifier deliberately does not use `jarsigner -strict`, which treats a self-signed signer as an error.

Both artifacts must contain a compiled Baseline Profile. CI also rejects a compiled `baseline.prof` larger than the Android Baseline Profile size limit enforced by `tools/verify_baseline_profile.py`.

The expected compiled profile paths are:

- APK: `assets/dexopt/baseline.prof`;
- AAB: `BUNDLE-METADATA/com.android.tools.build.profiles/baseline.prof`.

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
4. runs debug unit tests and lint, builds the debug APK, and compiles the instrumentation-test APK;
5. builds a signed `arm64-v8a` release APK and a signed AAB containing the supported `arm64-v8a` and `x86_64` ABIs;
6. runs the pinned engine tests plus source and shipped-binary `govulncheck` scans against the exact Mihomo AAR produced for the release;
7. verifies the APK signature with Android `apksigner` and the AAB signature/integrity with the `jarsigner`-based AAB verifier;
8. verifies the Baseline Profile is packaged in both artifacts and remains within its compiled-size budget;
9. enforces the exact APK/AAB ABI sets, APK 16 KB alignment checks, and the 30 MiB direct-install APK budget;
10. writes SHA-256 checksums for both artifacts;
11. uploads the APK, AAB, checksums and size report as a workflow artifact;
12. creates or updates the matching GitHub Release and attaches both signed distribution artifacts and their checksums.

## Local signed build

The same Gradle paths can be exercised locally without putting credentials in Gradle files:

```bash
export DETOUR_RELEASE_KEYSTORE=/absolute/path/to/release.keystore
export DETOUR_RELEASE_STORE_PASSWORD='...'
export DETOUR_RELEASE_KEY_ALIAS='...'
export DETOUR_RELEASE_KEY_PASSWORD='...'

./gradlew :app:assembleRelease \
  -PdetourReleaseAbi=arm64-v8a \
  -PdetourVersionName=0.1.0 \
  -PdetourVersionCode=1000

./gradlew :app:bundleRelease \
  -PdetourVersionName=0.1.0 \
  -PdetourVersionCode=1000
```

Run `bash engine/vulnscan.sh` with the pinned `govulncheck` tool available. Verify the resulting APK with `apksigner verify --verbose --print-certs`, the AAB with `bash tools/verify_aab_signature.sh <bundle.aab>`, and both artifacts with `python3 tools/verify_baseline_profile.py <artifact>` before distributing a locally produced build.
