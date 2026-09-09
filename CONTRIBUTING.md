# Contributing to Detour

Contributions are welcome. Keep changes focused, testable and compatible with Detour's security boundaries.

## Development

- JDK 17 and the Android/Go toolchain versions are documented in `README.md` and `docs/pins.md`.
- Build with `./gradlew :app:assembleDebug`.
- Run the core gate described in `docs/testing.md` before submitting a pull request.
- Do not commit generated native binaries, local SDK paths, keystores or credentials.

## Pull requests

1. Explain the user-visible behavior and why the change is needed.
2. Add or update unit/instrumentation tests for behavioral changes.
3. Keep real VPN credentials out of fixtures; use synthetic material only.
4. Preserve fail-closed routing behavior and explicit profile selection.
5. Reuse the shared Compose design system instead of introducing one-off buttons, fields or selection treatments.
6. Update documentation when import formats, security boundaries, release behavior or public APIs change.

## UI changes

Detour uses semantic colors and shared components under `app/src/main/java/dev/detour/app/ui`. Accent color is reserved primarily for actions and compact state indicators; large input/list surfaces should remain neutral unless a semantic warning/success state requires otherwise. Verify light/dark themes, large font scale and Android 16/17 instrumentation where applicable.

## Security reports

Do not file exploitable vulnerabilities or credentials in a normal public issue. Follow `SECURITY.md`.
