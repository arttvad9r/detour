# Screenshot and media guide

The root README now uses **real Detour captures from an Android device**. Product screenshots are stored under `docs/assets/screenshots/` and should stay representative of the current app rather than being replaced by fabricated UI mockups.

## Current product gallery

| File | What it shows |
| --- | --- |
| `home-connected.jpg` | Connected Home screen and active route summary |
| `app-routes.jpg` | Per-app Direct / VPN / DPI routing |
| `vpn-profiles.jpg` | VLESS, subscription and WireGuard-family profiles in one screen |
| `vpn-add-profile.jpg` | Supported profile/import entry points |
| `dpi-proxy-test.jpg` | ByeDPI strategy-test configuration |
| `appearance.jpg` | Detour and community theme choices |

These files are optimized copies of real device screenshots supplied for the project. They are not generated product mockups.

## What the gallery should communicate

A visitor should understand the product without reading the engineering documentation:

1. Detour has a clear connection state and primary action.
2. Routing is explicit per app: Direct / VPN / DPI.
3. Multiple VPN profile families can coexist.
4. Profiles can be added from several supported formats.
5. ByeDPI has a dedicated strategy-testing workflow.
6. The app has a deliberate, coherent visual system.

## Safety rules for future captures

Before committing a new screenshot, inspect it at 100% zoom. Never expose private VPN credentials, VLESS UUIDs, WireGuard private keys, complete Amnezia invitation payloads, authentication secrets, exported backup contents, or personal notification content.

If a public-facing label or endpoint name is intentionally shown, make sure it is approved for public display. Prefer synthetic demo data when preparing future marketing captures.

Do not create a screenshot by redrawing the product UI after capture. Populate safe state in the real app and capture the rendered screen instead.

## Capture consistency

For a future gallery refresh:

- use one phone/emulator form factor for the primary set;
- keep portrait orientation and matching dimensions;
- use a consistent Android version, system-bar style and font scale;
- keep the language consistent within the gallery;
- choose light/dark screens intentionally rather than mixing them randomly;
- crop only external empty framing, never app content or system insets inconsistently.

GitHub does not need decorative phone frames around every image. Clean real screenshots keep the UI larger and easier to inspect.

## README layout

The public gallery is defined directly in `README.md` and `README.ru.md`. Both language versions should reference the same screenshot files so the two landing pages stay visually synchronized.

When replacing a screenshot, preserve the filename where possible. If a filename changes, update both READMEs in the same pull request to avoid broken product images.

## Review checklist

- [ ] Every product image is a real Detour render.
- [ ] No private credential payload is visible.
- [ ] Public labels/endpoints shown in the capture are approved for publication.
- [ ] Primary captures have a consistent form factor.
- [ ] Alt text describes the represented feature.
- [ ] Both language READMEs reference files that exist.
- [ ] Screens still match the current release UI.
