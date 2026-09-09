# Screenshot and media plan

The public gallery should show the real Detour UI. Do not use fabricated connection states, real subscription URLs, private keys, VPN invitations, or production account data.

This document defines the capture set so screenshots can be added later without redesigning the README.

## Gallery goals

A visitor should understand these ideas within a few images:

1. Detour has a clear connection Home screen.
2. Routing is per app: Direct / VPN / DPI.
3. Several VPN profile types can coexist.
4. Subscription servers are visible and testable.
5. ByeDPI has a real strategy-testing workflow rather than one hidden text field.
6. The product has a coherent light/dark visual system.

## Required screenshots

Store final captures under `docs/assets/screenshots/` using these exact filenames.

| File | Screen | What the image should communicate |
| --- | --- | --- |
| `home-connected.png` | Home, connected | Connection state, selected profile/protocol, clean primary action hierarchy |
| `app-routes.png` | App routes | Several apps assigned across Direct / VPN / DPI |
| `vpn-profiles.png` | VPN profiles | VLESS plus multiple WireGuard-family profiles in one list |
| `subscription-servers.png` | Subscription servers | Search/sort, selected server, latency/availability information |
| `dpi-proxy-test.png` | Proxy Test results | Strategy comparison, coverage and latency, Apply action |
| `settings-dark.png` | Settings in dark theme | Product navigation and visual consistency |

Optional follow-up captures:

| File | Screen |
| --- | --- |
| `dns.png` | DNS choices including custom DoH |
| `backup.png` | Export / import |
| `appearance.png` | Theme selection |
| `home-disconnected.png` | Home before first connection |

## Safe demo data

Use only synthetic credentials and non-sensitive labels.

Recommended profile names:

- `Amsterdam — Reality`
- `Personal subscription`
- `WARP`
- `Lab AWG`

Recommended subscription node names:

- `Amsterdam 01`
- `Frankfurt 01`
- `Stockholm 01`
- `Warsaw 01`

Use representative but synthetic latency values. Do not create a screenshot by editing the UI image after capture; populate test/demo state in the app or a safe fixture and capture the real rendered screen.

For the app-routing screenshot, prefer common non-sensitive applications or dedicated demo package labels. Avoid displaying personal messaging accounts, work apps, banking apps, device owner names, or notification content.

## Never expose

Before committing a screenshot, inspect it at 100% zoom for:

- VLESS UUIDs;
- subscription URLs;
- WireGuard private keys;
- Amnezia invitation payloads;
- server credentials;
- exported backup filenames containing personal information;
- notification content from other apps;
- status-bar information that identifies the device owner or organization.

If sensitive material is visible, recapture with safe data. Do not rely on a blur that could be missed or reversed from another asset.

## Capture profile

For a consistent first gallery:

- use one recent Pixel-style phone/emulator size for the compact layout;
- use portrait orientation for the six primary images;
- keep system font scale at 100% for marketing captures;
- use the same Android version and system-bar style across the set;
- capture both light and dark UI through intentional screen selection rather than mixing themes randomly;
- keep the app language consistent within one gallery (English is the default recommendation for the GitHub landing page).

Accessibility and expanded-layout screenshots are valuable for engineering documentation but should be a separate gallery rather than mixed into the first product strip.

## Image preparation

Prefer lossless PNG for UI captures.

Recommended workflow:

1. Capture the real device/emulator screen.
2. Crop only empty emulator/device-frame surroundings if present; do not crop app content or system insets inconsistently.
3. Keep all primary captures at the same pixel dimensions.
4. Run a lossless PNG optimizer if desired.
5. Commit the files under `docs/assets/screenshots/`.
6. Enable the README gallery block shown below.

Avoid heavy mock phone frames. GitHub already provides enough surrounding context, and large decorative frames make text inside screenshots harder to inspect.

## README gallery layout

Once all six primary files exist, add this block after the opening product explanation in `README.md`:

```html
<table>
  <tr>
    <td width="33%"><img src="docs/assets/screenshots/home-connected.png" alt="Detour connected Home screen"></td>
    <td width="33%"><img src="docs/assets/screenshots/app-routes.png" alt="Per-app Direct VPN and DPI routing"></td>
    <td width="33%"><img src="docs/assets/screenshots/vpn-profiles.png" alt="Detour VPN profiles"></td>
  </tr>
  <tr>
    <td align="center"><b>One connection, clear state</b></td>
    <td align="center"><b>Route every app independently</b></td>
    <td align="center"><b>Keep multiple VPN profiles</b></td>
  </tr>
  <tr>
    <td width="33%"><img src="docs/assets/screenshots/subscription-servers.png" alt="Subscription server list and latency"></td>
    <td width="33%"><img src="docs/assets/screenshots/dpi-proxy-test.png" alt="ByeDPI Proxy Test results"></td>
    <td width="33%"><img src="docs/assets/screenshots/settings-dark.png" alt="Detour settings dark theme"></td>
  </tr>
  <tr>
    <td align="center"><b>Inspect subscription servers</b></td>
    <td align="center"><b>Test ByeDPI strategies</b></td>
    <td align="center"><b>Native, coherent Android UI</b></td>
  </tr>
</table>
```

Do not enable this block while any referenced file is missing; broken image boxes make the repository look unfinished.

## Optional hero artwork

If the project later gets a dedicated hero/banner, keep it secondary to real screenshots. A useful hero should communicate the routing concept rather than being generic VPN stock art.

Preferred concept:

```text
Android apps
    ↓
Detour routing decision
    ├── Direct
    ├── VPN
    └── DPI
```

A static repository asset should be committed to `docs/assets/brand/` and have a source/license note in `docs/assets/README.md` when it is not authored specifically for Detour.

## Review checklist before merge

- [ ] Every screenshot is a real Detour render.
- [ ] No real credentials or personal information are visible.
- [ ] Primary screenshots have matching dimensions.
- [ ] Alt text describes the feature rather than saying only “screenshot”.
- [ ] Light/dark usage looks intentional.
- [ ] README references only files that actually exist.
- [ ] Captures still match the current release UI.
