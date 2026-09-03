# Third-party notices

Detour embeds and distributes native code built from exact upstream revisions. The authoritative revisions are also recorded in `docs/pins.md`.

## Mihomo

- Project: MetaCubeX/mihomo
- Version: v1.19.30
- Commit: `ac017cdd246ce8bd547653d927e7bf77d7ee73d5`
- Upstream license: GNU General Public License v3.0 (GPL-3.0)
- Integration: built as the Android Go engine/AAR and linked into the Detour application.

The upstream source and license for the pinned revision must remain available with every distributed Detour binary in the manner required by GPL-3.0. Detour also applies Android embedding patches during its reproducible native build; the corresponding source/build scripts are part of this repository.

## ByeDPI

- Project: hufrea/byedpi
- Version: v0.17.3
- Commit: `7efde1b1296eaaa187b70e951894dde17527489c`
- Upstream license: MIT
- Integration: built as the `ciadpi` child-process binary packaged in the APK.

Detour applies a build-time Android authentication patch to the pinned ByeDPI source. The corresponding patch/build logic is part of this repository.

## Distribution requirement

This notice describes third-party components; it does not select a license for Detour itself. Before publishing a Detour APK, the repository must contain an explicit root `LICENSE` chosen by the project owner and compatible with all distribution obligations, including the GPL-3.0 obligations of the embedded Mihomo engine.
