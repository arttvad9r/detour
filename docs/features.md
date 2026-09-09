# Detour feature guide

This document describes the user-facing capabilities present on the current `main` product line. It is intentionally separated from roadmap work so the public documentation does not advertise features that are not in a release yet.

## Core routing model

Detour owns one Android `VpnService` and applies an explicit route choice to applications selected in the UI.

### Direct

Use the normal connection path for that app.

Good for:

- local services;
- apps that do not need a tunnel;
- latency-sensitive traffic you deliberately want outside the VPN path.

### VPN

Send the app through the selected VPN profile handled by the embedded Mihomo engine.

Supported profile families include VLESS Reality and WireGuard-family profiles such as Cloudflare WARP and AmneziaWG.

### DPI

Send the app through Detour's native ByeDPI backend.

This is separate from the VPN route: an app can use DPI bypass without forcing other routed apps to use the same outbound.

## App routing UI

The **App routes** screen provides:

- installed-app inventory;
- app search;
- optional system-app visibility;
- a Direct / VPN / DPI choice per app;
- persisted route selection;
- routed-app counts surfaced elsewhere in the app.

When Android exposes ambiguous package ownership through a shared UID, Detour refuses unsafe routing rather than guessing which package the user intended.

## VPN profiles

The profile screen provides one list and add flow for supported VPN sources.

### VLESS Reality links

Supported VLESS profiles use Reality with `xtls-rprx-vision`. Detour parses and validates the link before using it.

### HTTPS subscriptions

A supported HTTPS subscription is represented as a profile whose node catalog can be inspected and controlled from the app.

Subscription controls include:

- load/refresh;
- explicit node selection;
- search;
- sort by default order, latency, or name;
- ping testing;
- online/offline/not-tested state;
- per-node latency display;
- live provider availability while a tunnel is active.

Detour preserves the existing tunnel if a manual subscription refresh fails instead of replacing it with an invalid intermediate state.

### Cloudflare WARP

Detour can import supported WARP configuration material and expose it as a WireGuard-family VPN profile.

### AmneziaWG

Supported AmneziaWG configuration includes the fields handled by Detour's AWG 3.1 importer/runtime path.

### Multiple WireGuard-family profiles

WireGuard-family entries are identity-based. You can keep WARP and multiple AmneziaWG profiles at the same time, select one, edit/delete by profile identity, and import another without replacing unrelated profiles.

### Amnezia `vpn://` imports

Detour supports the Amnezia invitation shapes implemented for:

- XRay VLESS Reality;
- AmneziaWG.

Unsupported invitation contents are rejected instead of being partially imported as if they were valid.

## Subscription server controls

A subscription is not treated as an opaque URL once loaded. Detour provides a server catalog with:

- current selection;
- search;
- default / latency / name sorting;
- latency testing;
- availability states;
- refresh timestamp/status;
- failure states that leave the previous selection intact where possible.

This makes it possible to compare endpoints before committing the VPN route to one server.

## ByeDPI

Detour packages a local native `ciadpi` backend and exposes it to the routing engine through a loopback SOCKS endpoint.

### DPI bypass settings

The normal DPI settings screen supports:

- a recommended preset;
- a custom strategy;
- validation of the custom strategy arguments supported by that screen;
- persistence and application to the active DPI route.

### Proxy Test

Proxy Test is the advanced ByeDPI workspace. It is intended for discovering a strategy empirically instead of repeatedly editing one command by hand.

It supports:

- built-in reference strategies;
- selecting all, some, or none of the reference strategies;
- an additional custom strategy;
- selectable test domains/hosts;
- configurable requests per host;
- configurable parallel-host count;
- configurable timeout;
- start/stop controls;
- progress by strategy and host;
- detection/rejection of runs contaminated by another system VPN;
- persisted previous-run history;
- all-results inspection;
- full/partial coverage summaries;
- successful-request counts;
- median latency;
- manual application of a chosen result.

A stopped test does not automatically apply a strategy.

## DNS

Detour has a tunnel DNS policy separate from Android's general UI.

Built-in choices:

- Cloudflare;
- Google DNS;
- AdGuard.

Custom choices:

- IP address;
- HTTPS DNS-over-HTTPS URL.

Invalid custom values are rejected by the UI before they become the configured resolver.

## Backup and restore

The **Export / import** screen can move Detour configuration between devices.

The versioned backup model covers product settings including:

- VPN profiles;
- multiple WireGuard-family profiles;
- route assignments;
- DPI strategy;
- DNS/settings data covered by the backup schema;
- appearance/theme state.

Because a backup can contain VPN credentials, it should be handled as secret material.

## Local protection of credentials

Sensitive persisted profile values are encrypted locally with key material backed by Android Keystore before being written to DataStore.

This does not make exported credentials or third-party service credentials public-safe. It protects the app's local persisted values from being stored as ordinary plaintext preferences.

## Android integration

### VPN service

Detour uses Android's `VpnService` for the TUN interface and application routing boundary.

### Foreground notification

The active service has a foreground notification with connection state and a disconnect action.

### Quick Settings tile

The Detour tile exposes:

- disconnected;
- connecting;
- connected;
- connection-error states;
- routed-app count when relevant;
- connection control without opening the full UI.

### Connect on launch

An optional setting can trigger the configured connection flow when Detour launches.

## Home status

Home surfaces the connection state and context such as:

- connected / disconnected / connecting / failed;
- selected profile;
- protocol combination;
- server/endpoints where relevant;
- DNS information;
- connection action.

The protocol presentation distinguishes combinations such as VLESS + DPI and WARP + DPI from single-route configurations.

## Appearance and localization

Detour includes:

- Detour Light;
- Detour Dark;
- Midnight;
- Ocean;
- Graphite and amber;
- Lavender;
- adaptive Compose layouts;
- English default resources;
- Russian resources.

The UI uses the shared Detour design components and theme system instead of each screen defining an unrelated visual style.

## Current Android baseline

The Android application currently builds with:

- minimum SDK 29 (Android 10);
- compile SDK 37;
- target SDK 36;
- Java 17;
- Kotlin with AGP built-in Kotlin;
- Jetpack Compose;
- Material 3 and Material 3 Adaptive;
- Navigation 3;
- lifecycle-aware Compose integration;
- DataStore;
- Coroutines;
- app-specific Baseline Profiles.

Normal development builds package `arm64-v8a` and `x86_64`; the public release workflow can narrow a distribution build to one supported ABI.

## Verification and release quality

The repository contains checks for several layers of the application rather than relying only on a debug build:

- JVM unit tests;
- ViewModel/state tests;
- parser/configuration tests;
- Compose/instrumentation smoke tests;
- accessibility-related instrumentation;
- font-scale layout checks;
- Android 16 emulator coverage;
- Android 17 / 16 KB environment coverage in CI;
- lint for debug and release;
- release assembly;
- dependency verification;
- embedded Go tests and race checks;
- vulnerability scanning;
- APK size and ABI verification;
- 16 KB ELF alignment checks;
- Baseline Profile generation/integration.

See [testing.md](testing.md) for the commands and CI details.

## Explicit non-goals of this page

This feature guide does **not** document unmerged roadmap branches or draft pull requests. A feature should move into this page when it is part of the product line being presented to users, not merely because experimental code exists in the repository history.
