# WARP / AmneziaWG profiles

Detour imports compatible WireGuard outbounds from Clash/Mihomo YAML and AmneziaWG-style configuration, then stores them as Detour-owned VPN profiles.

## Supported

- `type: wireguard` outbounds;
- `amnezia-wg-option` parameters;
- YAML anchors and merge keys used by generated configs;
- multiple compatible endpoints, combined into Detour's own fallback/url-test policy;
- switching the active VPN profile explicitly between VLESS and WARP/AmneziaWG;
- replacing an imported WARP/AmneziaWG profile from its validated source configuration.

## Ownership boundary

The imported file provides endpoint/credential material only. It does **not** take ownership of Detour routing.

Detour ignores source proxy groups, routing rules, listeners, DNS policy and unrelated proxy types. The app continues to own:

- per-app `Direct` / `VPN` / `DPI` selection;
- Android VPN allow-list construction;
- DNS policy;
- TUN/listener configuration;
- fail-closed behavior when the selected profile becomes unavailable.

Editing or replacing the active WARP/AmneziaWG profile restarts the tunnel. Deleting the active profile stops the tunnel rather than silently switching to a different endpoint.

Unsupported source entries are not used as implicit fallbacks. Additional transport families such as MASQUE should be added as separate, explicitly tested profile types rather than inherited from imported Mihomo routing configuration.
