# Architecture

Detour is currently an Android-only application. The repository contains one Android application module (`:app`) plus native engine build sources under `engine/`.

## Runtime layers

### Android application

`MainActivity` and the Compose UI own presentation, navigation, settings editing and user interaction. `RoutesStore` persists profiles, DNS/DPI choices, app routes, theme state and auto-connect preferences in DataStore.

`TriVpnService` is the runtime owner of the VPN session. It:

1. resolves effective per-app routes;
2. starts the local DPI backend when DPI-routed applications exist;
3. creates and configures the Android TUN interface;
4. supplies the TUN file descriptor to the embedded engine;
5. generates the engine configuration from Detour-owned settings;
6. starts/stops the engine and probes readiness;
7. rebuilds the session when network/package/profile changes require it.

The Android layer deliberately remains the policy owner. Imported external configs may provide credentials/endpoints, but do not replace Detour DNS, app-routing or listener policy.

## Per-app routing

Each application is assigned one of three routes:

- `DIRECT` — excluded from Detour's routed path;
- `VPN` — sent through the selected VLESS or WARP/AmneziaWG VPN profile;
- `DPI` — sent through the local ByeDPI SOCKS backend.

Android package/UID information is resolved by the host app. The engine also receives a host-side process resolver bridge based on `ConnectivityManager.getConnectionOwnerUid` for UID attribution where engine rules need it.

IPv6 selected traffic is captured and rejected explicitly while the current routing path is IPv4-only, preventing selected applications from escaping over the physical IPv6 interface.

## Embedded Mihomo

The current Mihomo integration is intentionally narrow at the Android boundary but broad in responsibility inside the data plane. The generated AAR exposes the operations Detour needs to:

- install the host process resolver;
- start from generated YAML;
- report readiness;
- stop the engine;
- forward engine logs.

Mihomo currently provides TUN/gVisor handling, TCP/UDP forwarding, DNS hijack, UID rules, VLESS/Reality, WireGuard/AmneziaWG, WARP fallback groups, local SOCKS chaining and listener/probe infrastructure.

Detour patches the pinned Mihomo source during build for Android app restrictions and host-side UID resolution. Exact revision and patch rationale live in `docs/pins.md`.

## ByeDPI

ByeDPI is built as the `ciadpi` executable for Android arm64-v8a and x86_64 and packaged through `jniLibs`. `DpiBackend` runs it as a child process bound to loopback. Mihomo reaches it through a local SOCKS endpoint.

This is a comparatively narrow integration boundary: replacing ByeDPI can preserve the same local backend contract without redesigning the rest of the tunnel.

## Profiles

VLESS profiles are parsed and validated by Detour before storage. WARP/AmneziaWG imports extract compatible WireGuard outbounds from source YAML/CONF; source routing, DNS, listeners and proxy groups are not adopted.

Sensitive VLESS and WARP profile payloads are encrypted before they are persisted in DataStore. Detour uses AES-256-GCM with a non-exportable Android Keystore key and binds each ciphertext to its preference slot with authenticated additional data. If authenticated decryption fails, that profile is treated as unavailable rather than falling back to another endpoint. Non-secret preferences such as routes, theme and DNS selection remain ordinary DataStore values.

Export/import is a separate user-controlled portability boundary: the exported JSON intentionally contains the decrypted VPN profile material needed on another device. The Backup screen therefore treats the exported file as sensitive and warns the user to store it securely.

Profile mutation policy is fail-closed:

- changing an inactive profile does not disturb an active tunnel;
- changing/replacing the active profile restarts the tunnel;
- deleting the active profile stops the tunnel instead of silently selecting another endpoint.

Backup import also disables auto-connect and stops an active stale tunnel before replacing settings.

## App inventory

Installed launcher applications are discovered off the main thread and cached process-locally. Route-screen metadata and small icons are warmed before the screen is normally opened, avoiding PackageManager work and placeholder/icon swaps during navigation. Package add/remove broadcasts mark the snapshot stale and trigger the required VPN rebuild when a routed package changed.

## UI motion and refresh rate

Navigation uses opaque single-moving-surface page transitions: the incoming page moves over a stationary previous page; Back moves only the current page away. Dense Settings and App Routes entries use slightly longer nominal durations than lighter child screens.

Animation durations are time-based and respect Android's animator-duration scale. On supported Android 15+ devices Detour keeps platform adaptive-refresh and touch boost enabled. Compose `FrameRateCategory.High` is requested only for bounded navigation, scrolling/fling, list motion and expansion/collapse windows; static UI returns to the default scheduler policy. The app does not pin a concrete 120 Hz display mode.

## Build layout

- `app/` — Android application, Compose UI, persistence, VPN service, routing/config generation and tests.
- `engine/mihomo/` — pinned Mihomo source/build wrapper and Android embedding.
- `engine/byedpi/` — pinned ByeDPI build wrapper.
- `engine/libs/` — generated native AAR destination; binaries are ignored by git.
- `docs/` — current architecture, verification, pinning and profile-import documentation.

There is no cross-platform abstraction layer today. Android APIs such as `VpnService`, `Window` refresh policy, PackageManager/UID resolution and the NDK/gomobile packaging path are first-class implementation choices.
