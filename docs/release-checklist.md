# Public release checklist

Use this checklist for each public Detour release.

## Code and product

- [ ] The intended release commit is merged to `main`.
- [ ] Android CI is fully green, including Android 16 and Android 17 instrumentation.
- [ ] Unit tests, debug/release lint, native race checks and vulnerability scans are green.
- [ ] The selected VPN profiles were smoke-tested on a physical Android device.
- [ ] Profile import/add/select/edit/delete flows do not silently change unrelated profiles.
- [ ] Light and dark themes, large font scale, keyboard/insets and destructive dialogs were spot-checked.

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
- [ ] APK signature, ABI set, 16 KB ELF alignment and size budget pass the release workflow.

For the mechanics and required GitHub secrets, see `docs/releasing.md`.
