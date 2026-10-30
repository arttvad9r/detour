# Detour documentation

Documentation grouped by audience.

## For users

| Document | Purpose |
| --- | --- |
| [Getting started](getting-started.md) ([English](getting-started.en.md)) | Install, add a profile, assign app routes, connect, troubleshoot |
| [WARP profiles](warp-profiles.md) | Cloudflare WARP / AmneziaWG profile import details |
| [Privacy policy](../PRIVACY.md) | What Detour stores locally and how configured third-party services are involved |
| [Security policy](../SECURITY.md) | Security reporting and credential-handling guidance |

## For contributors

| Document | Purpose |
| --- | --- |
| [Contributing](../CONTRIBUTING.md) | Development and pull-request expectations |
| [Architecture](architecture.md) | Application/runtime boundaries, routing engine, and lifecycle |
| [Testing](testing.md) | Local verification and hosted CI coverage |
| [Native dependency pins](pins.md) | Exact revisions and provenance for embedded native components |
| [Third-party notices](../THIRD_PARTY_NOTICES.md) | License and binary-distribution obligations |

## For maintainers

| Document | Purpose |
| --- | --- |
| [Release process](releasing.md) | Signed semantic-tag release workflow |
| [Release checklist](release-checklist.md) | Pre-release verification gate |
| [Changelog](../CHANGELOG.md) | User-facing changes for the current product line |

## Documentation rules

Public user documentation should describe the released/current product line. Useful plans, design decisions and audit findings may be kept separately under `docs/`, clearly marked when they describe unshipped work. Disposable agent logs and installed skills stay local.

Examples, tests, screenshots, and issue reports must use synthetic VPN credentials. Never publish real VLESS links, subscription URLs, WireGuard private keys, Amnezia invitation payloads, or Detour backup files containing secrets.
