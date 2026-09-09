# Getting started with Detour

Detour is designed around per-app routing. You add one or more VPN profiles, decide which Android apps should use `Direct`, `VPN`, or `DPI`, and then start a single Android VPN session that enforces those choices.

## Requirements

- Android 10 / API 29 or newer.
- The public GitHub APK release currently targets `arm64-v8a` devices.
- A supported VPN profile is optional if you only want the `DPI` route, but the `VPN` route requires a configured profile.
- Android must grant Detour VPN permission before the tunnel can start.

## 1. Install Detour

Open the [latest GitHub release](https://github.com/arttvad9r/detour/releases/latest), download the Detour APK, and install it.

Android may ask you to allow installation from the browser or file manager you used to open the APK. That is an Android package-install permission; Detour does not need it after installation.

### Verify the downloaded APK

Each public release also includes a `.sha256` file produced from the signed release APK. Download both files into the same directory and verify them before installation:

```bash
sha256sum -c detour-<version>-arm64.apk.sha256
```

A successful check should report `OK`. If the checksum does not match, do not install that file; download the release again from this repository.

### Google Play Protect warning or block

Detour is currently distributed through GitHub Releases rather than Google Play. Google Play Protect checks apps installed from other sources and may ask to scan an app it has not seen before, warn about it, or block installation.

Keep Play Protect enabled. If Android offers to scan the APK, allow the scan. Do not disable Play Protect globally just to install Detour.

If Play Protect explicitly blocks a Detour release:

1. Confirm that the APK came from this repository's **GitHub Releases** page and that its SHA-256 checksum matches.
2. Record the Detour version, Android version, Play Protect message, and a screenshot of the warning.
3. Do not force-install a file that failed verification or that Play Protect identifies as harmful.
4. Report a reproducible false positive to the project. Maintainers can use Google's official [Play Protect appeal form](https://support.google.com/googleplay/android-developer/contact/protectappeals) when a release is incorrectly flagged.

Google's general Play Protect documentation is available in [Google Play Help](https://support.google.com/googleplay/answer/2812853).

## 2. Add a VPN profile

Open **Settings → VPN profiles** and tap **Add**.

Detour currently supports these profile paths:

### VLESS Reality

Paste a complete supported VLESS Reality link. Detour validates the link before saving it.

### HTTPS subscription

Paste an HTTPS subscription URL in the VLESS add flow. Detour loads the subscription through the embedded Mihomo engine and exposes its servers for explicit selection.

After the catalog loads you can:

- search servers;
- sort by default order, latency, or name;
- run a ping test;
- select the server you want to use;
- refresh the subscription later.

### WARP / AmneziaWG

Choose **WARP / AmneziaWG** and import a supported YAML or WireGuard-style CONF file. Multiple WireGuard-family profiles can coexist; importing another profile does not replace unrelated profiles.

### Amnezia `vpn://`

Supported Amnezia invitation links can be imported for XRay VLESS Reality and AmneziaWG configurations handled by Detour.

> Keep profile links, private keys, subscription URLs, and imported configuration files private. They can contain credentials.

## 3. Choose a route for each app

Open **Settings → App routes**.

Use search to find an app, then assign one of three routes:

| Route | What it means |
| --- | --- |
| `Direct` | The app uses the normal network path instead of a Detour proxy/VPN outbound. |
| `VPN` | The app uses the currently selected VLESS, WARP, or AmneziaWG profile. |
| `DPI` | The app is sent through Detour's local ByeDPI backend. |

System apps are hidden by default and can be included from the same screen when needed.

A practical setup is to leave most applications on `Direct` and route only the apps that actually need a VPN or DPI bypass.

## 4. Configure DNS if needed

Open **Settings → DNS**.

Available choices include:

- Cloudflare;
- Google DNS;
- AdGuard;
- a custom IP address;
- a custom `https://` DNS-over-HTTPS URL.

This resolver is used for the Detour tunnel. You do not need to change it just to start using the app.

## 5. Configure DPI bypass if needed

Open **Settings → DPI bypass**.

You can use the recommended strategy or provide supported custom ByeDPI arguments. Changes are persisted and are applied to the DPI route.

For a more systematic approach, open **Proxy Test**. It can test built-in reference strategies and a custom strategy against selected hosts, keep result history, compare coverage/latency, and apply a successful strategy manually.

Do not run Proxy Test through another active system VPN. Detour rejects runs when a VPN appears because the result would no longer represent the direct network accurately.

## 6. Connect

Return to Home and tap **Connect**.

The first time, Android displays its standard VPN consent dialog. Approve it to let Detour create the TUN interface. Once connected, Home shows the active profile/protocol and relevant connection information.

Use **Disconnect** on Home, the foreground notification action, or the Detour Quick Settings tile to stop the session.

## Optional convenience settings

### Connect on launch

Enable **Settings → Connect on launch** if Detour should attempt to start the configured session when the app opens.

### Quick Settings tile

Add the Detour tile from Android's Quick Settings editor. The tile exposes connection state and lets you control the tunnel without opening the full app.

### Appearance

Detour includes light/dark product themes plus additional palettes. Theme selection is under **Settings → Appearance**.

## Move settings to another device

Open **Settings → Export / import**.

The exported backup includes configuration such as:

- VPN profiles;
- app routes;
- DPI strategy;
- theme/settings data.

The file contains secrets when your VPN profiles contain secrets. Store it accordingly and do not attach it to a public issue.

## Troubleshooting

### “VPN permission was not granted”

Start the connection again and approve Android's VPN permission prompt. Android allows only one active VPN owner in the normal VPN slot, so another VPN application may need to be disconnected first.

### “Select or import a VPN profile first”

At least one app is configured for `VPN`, but no usable VPN profile is currently selected. Add or select a profile in **VPN profiles**.

### Subscription servers do not appear

Check that the subscription URL is complete and uses HTTPS, then refresh it. A failed refresh does not intentionally replace the currently active tunnel configuration.

### DPI bypass does not work on my network

DPI behavior is network-specific. Open **Proxy Test**, select representative hosts and strategies, run the comparison without another active VPN, and manually apply a result that has useful coverage.

### An app sharing a UID cannot be routed

Android can assign the same UID to multiple packages. Detour refuses ambiguous cases rather than silently applying a route that could affect a different app.

### I changed a route/profile/DNS option while connected

Some configuration changes require the active tunnel to restart before the new routing state is in effect. The UI indicates when reconnect/restart behavior is required.

## Next

- [Feature guide](features.md) — full capability map.
- [WARP profiles](warp-profiles.md) — WARP / AmneziaWG import details.
- [Privacy](../PRIVACY.md) — what Detour stores and what configured third parties can see.
- [Security](../SECURITY.md) — reporting and credential-handling rules.
