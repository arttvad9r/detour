# Repository media assets

This directory is reserved for public Detour media used by the repository documentation.

## Structure

```text
docs/assets/
├── screenshots/   # real app captures
└── brand/         # optional project-authored or properly licensed static artwork
```

Git does not preserve empty directories, so create the subdirectories when the first real asset is added.

The required screenshot filenames, capture rules, safe demo data, and README gallery markup are defined in [../screenshots.md](../screenshots.md).

## Rules

- Prefer real Detour screenshots over fabricated UI mockups.
- Never commit real VPN credentials, subscription URLs, private keys, invitation payloads, or personal notification content.
- Keep screenshots current with the product line described by the repository README.
- Record the source and license here for any third-party visual asset.
- Do not commit font files or unrelated design-source dumps as repository marketing assets.
