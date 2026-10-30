# Contributing to Detour

Contributions are welcome. Keep changes focused, testable and compatible with Detour's security boundaries.

## Development

- JDK 17 and the Android/Go toolchain versions are documented in `docs/pins.md`.
- Build with `./gradlew :app:assembleDebug`.
- Choose validation from `docs/testing.md` according to the affected behavior. Documentation and housekeeping need only relevant file/configuration checks.
- Do not commit generated native binaries, local SDK paths, keystores or credentials.
- Keep installed AI skills, local assistant settings and disposable execution logs out of Git. Useful design decisions, plans and audit findings may live in maintained documentation under `docs/`.

## Changes and integration

Application work starts in a branch from `dev` and is merged into `dev` after relevant checks and the owner's approval. A request to merge provides that approval. Branch names are unrestricted and pull requests are optional; use one when it helps review or outside contributions. `main` tracks the latest released application code, with documentation and housekeeping allowed between releases. Keep those maintenance changes in `dev` too. See `AGENTS.md` for the branch/release policy.

1. Explain the change and any relevant user-visible impact.
2. Add useful regression coverage for significant behavior changes or bug fixes; use focused manual checks where automated tests would add little value.
3. Keep real VPN credentials out of fixtures; use synthetic material only.
4. Preserve fail-closed routing behavior and explicit profile selection.
5. Prefer shared Compose components; extend the design system when the task calls for a new control or treatment.
6. Update documentation when import formats, security boundaries, release behavior or public APIs change.

## UI changes

Detour's current design uses semantic colors and shared components under `app/src/main/java/dev/detour/app/ui`, with accent color mainly for actions and compact state indicators. Treat this as the current design direction, which can be revised in a design task. Check affected screens in light/dark themes and at large font sizes when layout or accessibility changes; use device tests when they exercise the changed behavior.

## Security reports

Do not file exploitable vulnerabilities or credentials in a normal public issue. Follow `SECURITY.md`.
