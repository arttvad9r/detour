# WARP profiles

Detour imports WARP/AmneziaWG WireGuard outbounds from Clash/Mihomo YAML files.

## Supported in the first version

- `type: wireguard`
- `amnezia-wg-option`
- YAML anchors and merge keys used by generated configs
- multiple compatible endpoints; Detour builds its own `url-test` group
- switching the active VPN profile between VLESS and WARP

## Intentionally ignored

The imported file does not control Detour routing. Proxy groups, rules, listeners, DNS policy and MASQUE entries from the source YAML are ignored. Detour extracts only compatible VPN outbound credentials and continues to own app routing (`Direct`, `VPN`, `DPI`).

MASQUE can be added independently after the WireGuard/AmneziaWG path is proven stable.
