# WireGuard / AmneziaWG profiles

Detour imports compatible WireGuard-family outbounds from Clash/Mihomo YAML, native WireGuard/AmneziaWG configuration, and supported Amnezia `vpn://` invitations. Imported credentials become Detour-owned VPN profiles.

## Supported

- `type: wireguard` outbounds;
- AmneziaWG options including the supported AWG 3.1 fields;
- YAML anchors and merge keys used by generated configs;
- multiple compatible endpoints inside one imported profile;
- multiple independent WireGuard-family profiles stored side by side;
- Cloudflare WARP and AmneziaWG profiles coexisting in the same WireGuard section;
- explicit selection of one active VPN profile;
- replacing one profile from a validated source configuration without overwriting its neighbors.

Importing a new WireGuard/AmneziaWG profile adds it to the collection and does not silently change the current VPN selection. Profiles are selected, edited and deleted by stable profile id.

## Ownership boundary

The imported file provides endpoint and credential material only. It does **not** take ownership of Detour routing.

Detour ignores source proxy groups, routing rules, listeners, DNS policy and unrelated proxy types. The app continues to own:

- per-app `Direct` / `VPN` / `DPI` selection;
- Android VPN allow-list construction;
- DNS policy;
- TUN/listener configuration;
- fail-closed behavior when the selected profile becomes unavailable.

Editing/replacing the active WireGuard-family profile restarts the tunnel. Deleting the active profile stops the tunnel rather than silently switching to another endpoint. Mutating an inactive WireGuard profile does not restart or stop the active tunnel.

Unsupported source entries are not used as implicit fallbacks. Additional transport families should be added as separate, explicitly tested profile types rather than inherited from imported Mihomo routing configuration.
