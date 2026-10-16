<p align="center">
  <a href="getting-started.md">Русский</a> · <strong>English</strong>
</p>

# Using Detour

## Install

1. Open the [latest release](https://github.com/arttvad9r/detour/releases/latest) and download `detour-…-arm64.apk`.
2. Open it on your phone. Android asks you to allow installs from your browser or file manager; allow it.

You need Android 10 or newer and a 64-bit phone.

**Play Protect** may warn that the app is unknown, because Detour is not published on Google Play. Keep Play Protect on, and if it offers to scan the app, accept. Install only APKs from this releases page.

<details>
<summary>How to check that the APK was not tampered with</summary>

Each release has a `.sha256` file next to the APK. Download both into one folder on a computer and run:

```bash
sha256sum -c detour-<version>-arm64.apk.sha256
```

It should print `OK`. If it does not, do not install that file.

</details>

## Add a VPN

You only need this if some apps should go through a VPN. Skip it if you only use DPI.

Go to **Settings → VPN profiles → Add profile** and pick what you have:

| What you have | What to tap |
| --- | --- |
| A key starting with `vless://` | **Add VLESS** |
| A subscription URL `https://…` | **Add subscription** |
| An Amnezia key `vpn://…` | **Paste Amnezia key** |
| A WARP or AmneziaWG file (`.yaml` or `.conf`) | **Import configuration file** |

A plain WireGuard file without AmneziaWG parameters is not accepted. Details: [WARP and AmneziaWG](warp-profiles.md).

For a subscription you can pick a specific server, search the list and test ping.

> Keys, subscriptions and config files are your VPN passwords. Do not post them publicly.

## Quick setup

On a clean installation, choose DPI-only, a VLESS key, a subscription or WARP/Amnezia.
The guide opens the matching profile form and app routes. After importing, select the
profile in the list, assign VPN or DPI to your apps and tap **Continue** to return to
the guide. Tap **Connect**, approve Android VPN permission and use **Go to Home** once
connected to finish.

You can skip the guide and reopen it from **Settings → Quick setup**.
It does not open automatically when updating an already configured installation.

## Choose routes

Open **App routes** (on Home or in Settings) and pick a route for each app:

- **Direct**: the normal internet, no VPN.
- **VPN**: through the selected VPN profile.
- **DPI**: bypass blocking and throttling without a server.

A good start is to leave everything direct and turn on VPN or DPI only for the apps that need it. System apps are hidden by default; the switch at the top shows them.

Use search and the **All / Direct / VPN / DPI** filters to find apps.
To assign a route to several apps, tap **Select**, check individual apps or tap
**Select all in list**, then choose the route at the bottom of the screen.
Only apps matching the current search and filters are selected. Changing the search,
route filter or system-app visibility clears the selection.

Tap an app name to see its assigned route, Detour connection state and anything
that prevents connecting. This checks configuration; it does not monitor live traffic.

## Connect

Tap **Connect** on Home. The first time, Android asks for VPN permission; allow it.

To disconnect, use the button on Home, the notification, or the Quick Settings tile. You can add the Detour tile by editing your Quick Settings panel.

If you change something while connected, Detour restarts the tunnel with the new settings by itself.

## If DPI bypass does not help

DPI bypass behaves differently with every ISP. To find a strategy that works for you:

1. Turn off any other VPN, or the test results will be wrong.
2. Open **Settings → DPI bypass → Proxy Test**.
3. Pick the sites you want to reach (for example, YouTube) and start the test.
4. Apply the strategy that opened the most sites.

## Other settings

- **DNS**: Cloudflare, Google, AdGuard, a custom IP or a DoH URL. Changing it is optional.
- **Connect on launch**: the tunnel starts when you open Detour.
- **Export / import**: move all profiles and routes to another phone. The file contains your keys, so keep it like a password.
- **Appearance**: light, dark and color themes.

## Problems

**"VPN permission was not granted".** Tap Connect again and allow it. If another VPN is on, turn it off first: Android allows only one VPN at a time.

**"Select or import a VPN profile first".** An app is set to VPN, but there is no profile. Add one, or switch that app to Direct or DPI.

**"Apps sharing a UID cannot be routed safely".** Some Android system apps share one identity. Detour cannot route one of them without affecting the others, so it refuses. Set those apps back to Direct.

**Subscription servers do not show up.** Check that the URL is complete and starts with `https://`, then refresh the subscription.

Still stuck? Open an [issue](https://github.com/arttvad9r/detour/issues). Do not attach keys, subscriptions or export files.
