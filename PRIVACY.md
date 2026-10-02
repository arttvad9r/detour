# Privacy

Detour is a local Android VPN/network client. It does not require a Detour account and the project does not operate an analytics or crash-reporting backend for the app.

## Data stored on the device

Detour stores application settings, per-app routing choices, VPN profiles and related profile state locally. Sensitive profile material is stored in encrypted DataStore values backed by Android Keystore. Android application backup is disabled for the app.

## Network activity

Because Detour is a VPN/network client, it necessarily sends network traffic to services selected or configured by the user. Depending on configuration, this can include:

- VPN / proxy servers;
- DNS resolvers;
- subscription URLs;
- endpoints used by configured transports and connectivity/latency checks.

Those third-party services have their own privacy practices. Detour cannot prevent a chosen VPN, DNS or subscription provider from observing requests that reach it.

## Installed-app information

Detour reads the launchable application inventory needed to present per-app routing controls. It does not request Android's broad `QUERY_ALL_PACKAGES` permission.

## Diagnostics

Detour does not intentionally upload project analytics or automatic crash reports. If you voluntarily attach logs to a bug report, review and redact them first. Never publish VPN invitation strings, private keys, preshared keys, VLESS/Reality credentials or other secrets.

This document describes the open-source Detour application in this repository. A third-party distributor can modify the source or add services, so verify the source/release you install.
