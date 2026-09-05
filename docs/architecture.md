# Architecture

Detour is an Android-only application. The repository contains one Android application module (`:app`) plus native engine build sources under `engine/`.

## Runtime layers

### Composition and presentation

`MainActivity` is the Android composition root. It owns theme composition, Navigation 3 wiring, external-import handling and the Android-specific callbacks needed to construct presentation state holders. Platform objects such as `PackageManager`, VPN consent and application `Context` stay at this boundary rather than being stored in screen ViewModels.

Stateful screens consume immutable `StateFlow` UI state with lifecycle-aware collection. Home, Settings, App Routes, Destination Rules, Profiles, DNS, DPI, Diagnostics and Backup use dedicated ViewModels for screen state and action orchestration. These ViewModels receive state flows, suspend persistence functions and narrow resolver/session callbacks; they do not own an Activity, Fragment or lifecycle-bound `Context`.

Compose remains responsible for UI-only concerns such as navigation callbacks, activity-result/document pickers, QR scanning, clipboard access, haptics, transient editor/sheet state and animation. Sensitive profile editor content stays process-memory-only and uses `FLAG_SECURE` while visible.

`RoutesStore` is the persistence source for profiles, DNS/DPI choices, per-app routes, destination rules, theme state, auto-connect preferences, subscription metadata/update policy, multi-hop entry selection and session metadata. It is backed by DataStore and exposes a committed settings flow/snapshot. Sensitive profile payloads are encrypted before storage.

Foreground auto-connect is coordinated by `AutoConnectCoordinator`. While `MainActivity` is visible, a default-network callback waits for an Internet-capable, validated, non-VPN network and invokes the same fail-closed coordinator used by startup policy. It does not attempt to bypass Android background foreground-service restrictions. Persistent background startup is delegated to Android system Always-on VPN when the user enables it.

### VPN session runtime

`TriVpnService` is the runtime owner of the VPN session. It:

1. resolves current Android package/UID ownership and effective per-app routes;
2. determines whether destination overrides require VPN and/or DPI backends;
3. resolves and validates an optional multi-hop entry profile;
4. starts the local DPI backend when required;
5. creates a dual-stack Android TUN interface;
6. generates the Mihomo configuration from Detour-owned settings;
7. supplies the TUN file descriptor to the embedded engine;
8. starts/stops the engine and probes the effective VPN/DPI paths;
9. publishes session state and live Mihomo traffic statistics;
10. rebuilds the session when network/package/profile changes require it.

The service accepts both Detour user commands and Android Always-on system starts. User starts carry an explicit app marker; system Always-on starts are handled through the platform VPN lifecycle rather than sticky null-intent restarts.

`VpnController` is the thin application-side state/start/stop bridge to the service. Presentation code does not own the actual tunnel lifecycle.

The Android layer deliberately remains the policy owner. Imported profiles/subscriptions provide endpoints and credentials, but do not replace Detour DNS, app-routing, destination-routing or listener policy.

## Routing

### Per-app routes

Each configured application is assigned one of three base routes:

- `DIRECT` — outside Detour's TUN allow-list;
- `VPN` — sent through the selected VPN exit;
- `DPI` — sent through the local ByeDPI SOCKS backend.

Persisted routes are resolved against current Android package/UID ownership before they are used. Ambiguous shared-UID ownership fails closed before TUN side effects.

Android package/UID information is resolved by the host app. The engine also receives a host-side process resolver bridge based on `ConnectivityManager.getConnectionOwnerUid` for UID attribution where engine rules need it.

### Destination rules

Domain/IP rules are Detour-owned overrides for applications that are already inside the routed VPN/DPI allow-list. Supported matchers are exact domain, domain suffix and IPv4/IPv6 CIDR, with Direct/VPN/DPI targets.

LAN/local safety rules remain above user overrides. Destination overrides are compiled before the normal UID base routes, and the final unknown-owner rule remains `MATCH,REJECT`. DPI-targeted QUIC/UDP 443 is rejected consistently with normal DPI routes.

Applications assigned Direct remain outside the TUN and are not silently captured by destination rules. This is also why Android Lockdown's "Block connections without VPN" option can block Detour Direct applications: Lockdown changes platform behavior outside Detour's own policy model.

### IPv6

Detour uses a real dual-stack TUN rather than allowing IPv6 to bypass the VPN. Android receives IPv4 and IPv6 TUN addresses plus default routes; generated Mihomo configuration enables IPv6 and includes `::/0`. Local/private IPv6 prefixes receive the same safety treatment as IPv4 local ranges.

The embedded engine sets Mihomo's supported system-IPv6-check override because the proxy path can carry IPv6 destinations even when the physical uplink itself is IPv4-only.

## Embedded Mihomo

The Android embedding exposes only the operations Detour needs while Mihomo remains responsible for the data plane. The bridge supports:

- host process resolution;
- start/readiness/stop lifecycle;
- log forwarding and redaction;
- live/session traffic statistics;
- subscription provider preparation and refresh;
- subscription catalog/state/selection;
- explicit per-node latency tests.

Mihomo provides TUN/gVisor handling, TCP/UDP forwarding, DNS hijack, UID and destination rules, VLESS/Reality, WireGuard/AmneziaWG, subscription proxy providers, URL-test/select groups, chaining and authenticated local probe listeners.

Detour patches the pinned Mihomo source during build for Android embedding restrictions, host-side UID resolution and compatibility fixes. Exact revision and patch rationale live in `docs/pins.md`.

### Subscriptions

Remote subscription URLs are HTTPS-only and fetched with bounded redirects/body size. The preparation path normalizes the body before writing an app-private file provider. It accepts:

- Mihomo/Clash-style YAML containing `proxies:`;
- URI/base64 subscription bodies through Mihomo's converter;
- a restricted sing-box JSON `outbounds[]` import path for mappings that can be translated unambiguously.

Only an explicit remote-proxy allow-list is retained, including VLESS, VMess, Trojan, Shadowsocks/SSR, Hysteria/Hysteria2, TUIC, AnyTLS and Mieru where the pinned Mihomo parser accepts the node. Direct/reject/DNS/local-proxy/WireGuard/provider/group types are not imported from subscriptions. Every retained mapping is parsed once by Mihomo before it can enter the provider, so one malformed unsupported node cannot invalidate the complete provider file.

The Android catalog UI and runtime provider share this same preparation path. Manual selection uses a Mihomo `select` group; automatic selection uses a bounded `url-test` group. Selected-node state remains owned per encrypted subscription profile in DataStore.

Background subscription refresh uses WorkManager for the active subscription and never places the secret URL in WorkManager input data.

## ByeDPI

ByeDPI is built as the `ciadpi` executable for Android arm64-v8a and x86_64 and packaged through `jniLibs`. `DpiBackend` runs it as a child process bound to loopback. Mihomo reaches it through an authenticated local SOCKS endpoint.

This is a narrow integration boundary: replacing ByeDPI can preserve the same local backend contract without redesigning the rest of the tunnel.

## Profiles, multi-hop and persistence

Direct VLESS profiles are parsed and validated by Detour before storage. Subscription profiles store HTTPS provider URLs plus per-profile runtime metadata. WARP/AmneziaWG imports extract compatible WireGuard outbounds from source YAML/CONF; source routing, DNS, listeners and proxy groups are not adopted.

Sensitive VLESS/subscription and WARP profile payloads are encrypted before they are persisted in DataStore. Detour uses AES-256-GCM with a non-exportable Android Keystore key and binds each ciphertext to its preference slot with authenticated additional data. If authenticated decryption fails, that profile is treated as unavailable rather than falling back to another endpoint. Non-secret preferences such as routes, theme, DNS selection and multi-hop profile references remain ordinary validated DataStore values.

Multi-hop is intentionally a two-hop model. The entry can be a saved direct VLESS profile or WARP profile; the exit remains the currently active direct VLESS, subscription or WARP profile. The generated configuration applies the entry with Mihomo `dialer-proxy`; subscription exits receive the same entry through provider `override.dialer-proxy`. Subscription-as-entry and WARP-to-WARP chains are rejected. Deleted, stale, corrupt or self-referencing entries fail closed instead of degrading to a one-hop tunnel.

Export/import is a separate user-controlled portability boundary. Backup v5 contains the decrypted VPN material needed on another device plus validated destination and multi-hop settings. The Backup screen therefore treats the exported file as sensitive. Older backup versions remain readable with newer-only fields disabled/defaulted safely.

Profile mutation policy remains explicit:

- changing an inactive profile does not disturb an active tunnel;
- changing/replacing an active exit or active multi-hop entry restarts the tunnel;
- deleting an active exit or entry stops the tunnel instead of silently selecting another endpoint;
- conflicting exit selection clears an incompatible entry explicitly.

Backup import disables auto-connect and stops an active stale tunnel before replacing settings.

## External import and permissions

`MainActivity` is the only exported activity. External text, `vless://` links and `detour://import?url=...` are bounded and validated before a confirmation dialog can save anything; raw secret URIs are not automatically persisted merely because an intent was received.

QR import uses Google Code Scanner, so Detour does not request the camera permission. The scanned value is placed into the same editor/validation path as pasted input.

`POST_NOTIFICATIONS` is requested only in user-driven VPN flows. Home owns a rationale-aware permission path. The Quick Settings consent activity can request notification permission during the first VPN-consent flow, but notification denial never prevents the foreground VPN service from starting and repeated tile connections do not nag the user.

Android platform backup is disabled because profile material is sensitive and Detour has an explicit user-controlled backup feature. Android cleartext HTTP traffic is disabled at the application manifest level; supported remote subscription/DoH URLs are HTTPS-only.

## App inventory

Installed launcher applications are discovered off the main thread and cached process-locally. Route-screen metadata and small icons are warmed before the screen is normally opened, avoiding PackageManager work and placeholder/icon swaps during navigation. Package add/remove broadcasts mark the snapshot stale and trigger the required VPN rebuild when a routed package changed.

The App Routes ViewModel owns inventory refresh, search state, route mutations and the tunnel-restart action associated with route changes. Compose owns rendering, icon loading for visible rows and interaction feedback.

## Adaptive UI and accessibility

Detour uses adaptive Navigation 3 list/detail behavior rather than maintaining a separate tablet application. Form/settings content is width-bounded on large windows, while Home can use a split layout on expanded widths. Edge-to-edge system-bar handling remains at the Activity root.

Shared controls treat fixed dimensions as minimums where text may grow. Buttons, headers, settings/action rows and segmented selectors can expand vertically for system font scaling, while interactive targets keep at least the Material minimum touch size.

Real-Activity Compose instrumentation covers primary semantics/touch targets, DNS input/error semantics, single-selection behavior and large-font/layout regressions.

## UI motion and refresh rate

Navigation uses opaque single-moving-surface page transitions: the incoming page moves over a stationary previous page; Back moves only the current page away. Navigation is compatible with predictive back through the Navigation 3 stack.

Animation durations are time-based and respect Android's animator-duration scale. On supported devices Detour keeps platform adaptive-refresh and touch boost enabled. Compose high-frame-rate hints are requested only for bounded navigation, scrolling/fling, list motion and expansion/collapse windows; static UI returns to the default scheduler policy. The app does not pin a concrete 120 Hz display mode.

## Verification and release performance

Every push compiles unit tests, debug/release lint, debug/release APKs, the Android-test APK, the release AAB and the Baseline Profile module under strict dependency verification. CI also runs the embedded Go bridge with the race detector, performs source/binary vulnerability scanning, reports APK size and verifies 16 KB ELF alignment.

The release path creates a disposable CI keystore and exercises both signed arm64 APK and signed arm64 AAB production packaging. APK signature/alignment/ABI/size checks and AAB JAR-signature/ABI checks must pass. Pull requests and `main` pushes additionally execute the instrumentation suite on Android 16 and Android 17 16-KB emulators.

Release code/resource optimization uses the AGP 9 optimization pipeline. The current package size is dominated by the embedded Go engine rather than Kotlin/Compose bytecode or resources, so size work should target engine feature/dependency composition rather than weakening binary vulnerability verification.

## Build layout

- `app/` — Android application, Compose UI, presentation state holders, persistence, VPN service, routing/config generation and tests.
- `engine/mihomo/` — pinned Mihomo source/build wrapper and Android embedding.
- `engine/byedpi/` — pinned ByeDPI build wrapper.
- `engine/libs/` — generated native AAR destination; binaries are ignored by git.
- `docs/` — current architecture, verification, pinning and profile-import documentation.

There is no cross-platform abstraction layer today. Android APIs such as `VpnService`, Navigation 3, PackageManager/UID resolution and the NDK/gomobile packaging path are first-class implementation choices.
