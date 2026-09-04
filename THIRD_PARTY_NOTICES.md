# Third-party notices

Detour embeds and distributes native code built from exact upstream revisions. The authoritative revisions are also recorded in `docs/pins.md`.

## Detour-authored code

Code authored for Detour is licensed under the MIT License in the repository root `LICENSE`, except where a file or directory explicitly states different terms. The root MIT License does not replace, relicense, or override the licenses of bundled third-party components.

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

Binary distribution of Detour must comply with the licenses of all bundled components. In particular, the root MIT License applies to Detour-authored code and does not remove the GPL-3.0 obligations associated with the embedded Mihomo engine. `LICENSE`, this notice, the pinned upstream revisions, and the corresponding source/build material must remain available as required by the applicable licenses.
