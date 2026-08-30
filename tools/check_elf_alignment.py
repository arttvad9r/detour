#!/usr/bin/env python3
from __future__ import annotations

import argparse
import subprocess
import sys
import tempfile
from pathlib import Path
from zipfile import ZipFile

MIN_ALIGNMENT = 16 * 1024


def locate_apk(raw: str) -> Path:
    path = Path(raw)
    if path.is_file():
        return path
    if not path.is_dir():
        raise SystemExit(f"APK path does not exist: {path}")
    candidates = sorted(path.glob("*.apk"))
    if len(candidates) != 1:
        names = ", ".join(item.name for item in candidates) or "none"
        raise SystemExit(
            f"Expected exactly one APK in {path}, found {len(candidates)}: {names}"
        )
    return candidates[0]


def load_alignments(readelf_output: str) -> list[int]:
    alignments: list[int] = []
    for line in readelf_output.splitlines():
        fields = line.split()
        if not fields or fields[0] != "LOAD":
            continue
        try:
            alignments.append(int(fields[-1], 0))
        except ValueError as exc:
            raise ValueError(f"Could not parse LOAD alignment from: {line}") from exc
    return alignments


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Verify 16 KiB PT_LOAD alignment for every native library in an APK"
    )
    parser.add_argument("apk", help="APK file or directory containing exactly one APK")
    parser.add_argument(
        "--readelf",
        default="llvm-readelf",
        help="llvm-readelf/readelf executable to inspect extracted shared libraries",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    apk = locate_apk(args.apk).resolve()
    failures: list[str] = []
    checked = 0

    with ZipFile(apk) as archive, tempfile.TemporaryDirectory(prefix="detour-elf-") as temp:
        native_entries = sorted(
            entry for entry in archive.infolist()
            if not entry.is_dir()
            and entry.filename.startswith("lib/")
            and entry.filename.endswith(".so")
        )
        if not native_entries:
            print("ERROR: APK contains no native shared libraries", file=sys.stderr)
            return 1

        root = Path(temp)
        for index, entry in enumerate(native_entries):
            extracted = root / f"{index}.so"
            with archive.open(entry) as source, extracted.open("wb") as target:
                target.write(source.read())

            result = subprocess.run(
                [args.readelf, "--program-headers", "--wide", str(extracted)],
                check=False,
                capture_output=True,
                text=True,
            )
            if result.returncode != 0:
                failures.append(
                    f"{entry.filename}: readelf failed ({result.returncode}): "
                    f"{result.stderr.strip()}"
                )
                continue

            try:
                alignments = load_alignments(result.stdout)
            except ValueError as exc:
                failures.append(f"{entry.filename}: {exc}")
                continue
            if not alignments:
                failures.append(f"{entry.filename}: no PT_LOAD segments found")
                continue

            checked += 1
            minimum = min(alignments)
            if minimum < MIN_ALIGNMENT:
                failures.append(
                    f"{entry.filename}: minimum PT_LOAD alignment "
                    f"0x{minimum:x} is below 0x{MIN_ALIGNMENT:x}"
                )
            else:
                print(f"ALIGNED {entry.filename}: min PT_LOAD=0x{minimum:x}")

    if failures:
        for failure in failures:
            print(f"ERROR: {failure}", file=sys.stderr)
        return 1

    print(f"Verified {checked} native libraries in {apk.name} for 16 KiB ELF alignment")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
