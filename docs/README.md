# Detour documentation

Documentation is grouped by audience so the repository landing page can stay product-focused without hiding implementation and release details.

## For users

| Document | Purpose |
| --- | --- |
| [Getting started](getting-started.md) | Install Detour, add a profile, assign app routes, connect, and troubleshoot common setup issues |
| [Feature guide](features.md) | Complete map of the user-facing capabilities currently presented as part of Detour |
| [WARP profiles](warp-profiles.md) | Cloudflare WARP / AmneziaWG profile import details |
| [Screenshot and media plan](screenshots.md) | Safe real-capture specification for the public repository gallery |
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

Public user documentation should describe the released/current product line, not draft roadmap branches. When a capability is still experimental or exists only in an unmerged pull request, keep it in the relevant design/PR discussion until it becomes part of the product being shipped.

Examples, tests, screenshots, and issue reports must use synthetic VPN credentials. Never publish real VLESS links, subscription URLs, WireGuard private keys, Amnezia invitation payloads, or Detour backup files containing secrets.
