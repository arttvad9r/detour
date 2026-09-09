# Screenshot and media guide

The root README uses **real Detour captures from an Android device**. Product screenshots live under `docs/assets/screenshots/` and must represent the current app without exposing a contributor's real VPN setup.

## Current product gallery

| File | What it shows |
| --- | --- |
| `settings.jpg` | Main settings and feature entry points |
| `dns.jpg` | Tunnel DNS choices |
| `dpi-bypass.jpg` | ByeDPI strategy configuration |
| `appearance.jpg` | Detour and community theme choices |

These are optimized copies of real device screenshots supplied for the project. They are not generated or reconstructed product mockups.

## Privacy rules

Public screenshots must not expose:

- VPN profile names tied to a real setup;
- subscription URLs, provider names or server hostnames from a private configuration;
- VLESS UUIDs, WireGuard private keys, Amnezia invitation payloads or authentication secrets;
- exported backup contents or personal notification content;
- personal app inventory when it is not required to demonstrate a feature.

For future screenshots of VPN profiles, subscriptions, servers or per-app routing, populate the **real app** with clearly synthetic demo data first, then capture the rendered UI. Do not redraw or fabricate the interface after capture.

## Capture consistency

- Prefer one phone/emulator form factor for a gallery set.
- Keep portrait orientation, matching dimensions and a consistent font scale.
- Use a consistent Android/system-bar style where practical.
- Keep one language within a gallery set.
- Crop only external framing; do not remove or alter app content to hide secrets.

## README layout

`README.md` and `README.ru.md` reference the same screenshot files. The landing page intentionally uses a simple two-by-two image layout instead of a fixed three-column table so GitHub mobile does not squeeze captions or alt text into narrow cells.

When replacing a screenshot, preserve the filename where possible. If a filename changes, update both READMEs in the same pull request.

## Review checklist

- [ ] Every image is a real Detour render.
- [ ] No personal profile, subscription, endpoint, credential or app-inventory data is visible.
- [ ] The image file opens as a valid JPEG/PNG from the repository.
- [ ] Alt text describes the represented feature.
- [ ] Both language READMEs reference files that exist.
- [ ] Screens still match the current release UI.
