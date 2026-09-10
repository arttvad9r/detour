# Screenshot and media guide

The root README uses **real Detour captures from an Android device**. Product screenshots live under `docs/assets/screenshots/` and must represent the current app without exposing a contributor's real VPN setup.

## Current product gallery

The primary README and Fastlane gallery is `product-gallery-hq.png`, a two-by-two montage assembled only from real device captures. It is ordered to explain the product from the main workflow outward:

1. **Connected home** — active Detour tunnel and the main connection overview.
2. **Per-app routes** — the core feature: different apps assigned to Direct, VPN or DPI routes.
3. **Settings** — routing, VPN profiles, DPI bypass, DNS and application controls.
4. **Themes** — Detour Light/Dark and the additional appearance choices.

The existing individual captures such as `settings.jpg`, `dns.jpg`, `dpi-bypass.jpg` and `appearance.jpg` remain useful for feature documentation, but the landing-page gallery should prioritize the connected state and per-app routing.

These are optimized copies of real device screenshots supplied for the project. They are not generated or reconstructed product mockups.

## Privacy rules

Public screenshots must not expose:

- VPN profile names tied to a real setup;
- subscription URLs, provider names or server hostnames from a private configuration;
- VLESS UUIDs, WireGuard private keys, Amnezia invitation payloads or authentication secrets;
- exported backup contents or personal notification content;
- personal app inventory when it is not required to demonstrate a feature.

For screenshots of VPN profiles, subscriptions, servers or per-app routing, populate the **real app** with clearly synthetic demo data where practical, then capture the rendered UI. Do not redraw or fabricate the interface after capture. App inventory may be visible when it is necessary to demonstrate per-app routing, but it must not reveal sensitive account or configuration data.

## Capture consistency

- Prefer one phone/emulator form factor for a gallery set.
- Keep portrait orientation, matching dimensions and a consistent font scale.
- Use a consistent Android/system-bar style where practical.
- Keep one language within a gallery set.
- Crop only external framing; do not remove or alter app content to hide secrets.

## README and Fastlane layout

`README.md` and `README.ru.md` reference the same `product-gallery-hq.png`. The landing page uses a single two-by-two montage so GitHub mobile does not squeeze four portrait screenshots into narrow columns.

Fastlane `en-US` and `ru` phone screenshot metadata reuses the same lossless gallery image. This keeps the public store view aligned with the repository landing page and makes per-app routing visible in both places.

When replacing the gallery, preserve the canonical `product-gallery-hq.png` filename where possible and update both README alt texts in the same pull request.

## Review checklist

- [ ] Every image is a real Detour render.
- [ ] The gallery visibly demonstrates per-app Direct / VPN / DPI routing.
- [ ] No personal profile, subscription, endpoint or credential data is visible.
- [ ] Any visible app inventory is appropriate for demonstrating the routing feature.
- [ ] The image file opens as a valid JPEG/PNG from the repository.
- [ ] Alt text describes the represented features.
- [ ] README and Fastlane references point to files that exist.
- [ ] Screens still match the current release UI.
