# Public release checklist

Use this checklist for each public Detour release.

## Code and product

- [ ] The intended release commit is merged to `main`.
- [ ] The owner approved the release (a request to release is approval).
- [ ] Full Android validation passed for the exact release revision, including unit/lint/build, native race/vulnerability checks and Android 16/17 instrumentation.
- [ ] Relevant runtime checks were performed for the changed behavior; native/routing changes include connection and per-app routing smoke on the produced build.
- [ ] Affected UI/profile flows were checked where applicable; material coverage gaps are recorded.

## Security and privacy

- [ ] No real VPN invitation strings, private keys, preshared keys, Reality credentials, keystores or passwords are present in the release diff/history.
- [ ] `SECURITY.md`, `PRIVACY.md`, `LICENSE` and `THIRD_PARTY_NOTICES.md` are current.
- [ ] Release signing secrets are configured in GitHub and the expected certificate SHA-256 fingerprint is verified.
- [ ] The release keystore has an offline backup.

## Distribution

- [ ] `CHANGELOG.md` / generated release notes describe user-visible changes.
- [ ] Tag uses canonical `vMAJOR.MINOR.PATCH`.
- [ ] The tag points to the exact verified commit on `main`.
- [ ] GitHub Release contains the signed arm64 APK and SHA-256 checksum.
- [ ] APK signature, ABI set and 16 KB ELF alignment pass; the size report was reviewed for unexpected growth.

For the mechanics and required GitHub secrets, see `docs/releasing.md`.
