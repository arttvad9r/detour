#!/usr/bin/env python3
from __future__ import annotations

import json
import os
import re
import sys
from collections import defaultdict
from pathlib import Path
from zipfile import ZipFile, ZipInfo


def format_bytes(value: int) -> str:
    units = ("B", "KiB", "MiB", "GiB")
    amount = float(value)
    for unit in units:
        if amount < 1024.0 or unit == units[-1]:
            return f"{amount:.1f} {unit}" if unit != "B" else f"{value} B"
        amount /= 1024.0
    raise AssertionError("unreachable")


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


def component(name: str) -> str:
    if name.startswith("lib/"):
        return "Native libraries"
    if re.fullmatch(r"classes\d*\.dex", name):
        return "DEX"
    if name == "resources.arsc" or name.startswith("res/"):
        return "Resources"
    if name.startswith("assets/"):
        return "Assets"
    if name.startswith("META-INF/"):
        return "META-INF"
    return "Other"


def main() -> int:
    if len(sys.argv) != 2:
        raise SystemExit("usage: apk_size_report.py <apk-or-release-directory>")

    apk = locate_apk(sys.argv[1]).resolve()
    apk_size = apk.stat().st_size

    with ZipFile(apk) as archive:
        entries: list[ZipInfo] = [entry for entry in archive.infolist() if not entry.is_dir()]

    groups: dict[str, list[int]] = defaultdict(lambda: [0, 0])
    native: list[dict[str, object]] = []
    for entry in entries:
        bucket = groups[component(entry.filename)]
        bucket[0] += entry.compress_size
        bucket[1] += entry.file_size

        parts = entry.filename.split("/")
        if len(parts) == 3 and parts[0] == "lib" and entry.filename.endswith(".so"):
            native.append(
                {
                    "abi": parts[1],
                    "library": parts[2],
                    "compressed_bytes": entry.compress_size,
                    "uncompressed_bytes": entry.file_size,
                }
            )

    largest = sorted(entries, key=lambda entry: entry.compress_size, reverse=True)[:12]
    payload = {
        "apk": {"name": apk.name, "size_bytes": apk_size},
        "components": {
            name: {"compressed_bytes": values[0], "uncompressed_bytes": values[1]}
            for name, values in sorted(groups.items())
        },
        "native_libraries": sorted(
            native, key=lambda item: (str(item["abi"]), str(item["library"]))
        ),
        "largest_entries": [
            {
                "path": entry.filename,
                "compressed_bytes": entry.compress_size,
                "uncompressed_bytes": entry.file_size,
            }
            for entry in largest
        ],
    }

    report_path = apk.parents[3] / "reports" / "apk-size.json"
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")

    lines = [
        "## Release APK size",
        "",
        f"- APK: `{apk.name}` — **{format_bytes(apk_size)}** ({apk_size:,} bytes)",
        "- Component values below are ZIP entry sizes and exclude container/alignment overhead.",
        "",
        "| Component | APK bytes | ZIP uncompressed |",
        "| --- | ---: | ---: |",
    ]
    for name, values in sorted(groups.items(), key=lambda item: item[1][0], reverse=True):
        lines.append(
            f"| {name} | {format_bytes(values[0])} | {format_bytes(values[1])} |"
        )

    if native:
        lines.extend(
            [
                "",
                "### Native libraries",
                "",
                "| ABI | Library | APK bytes | ZIP uncompressed |",
                "| --- | --- | ---: | ---: |",
            ]
        )
        for item in payload["native_libraries"]:
            lines.append(
                f"| `{item['abi']}` | `{item['library']}` | "
                f"{format_bytes(int(item['compressed_bytes']))} | "
                f"{format_bytes(int(item['uncompressed_bytes']))} |"
            )

    lines.extend(
        [
            "",
            "### Largest APK entries",
            "",
            "| Entry | APK bytes | ZIP uncompressed |",
            "| --- | ---: | ---: |",
        ]
    )
    for entry in largest:
        lines.append(
            f"| `{entry.filename}` | {format_bytes(entry.compress_size)} | "
            f"{format_bytes(entry.file_size)} |"
        )

    report = "\n".join(lines) + "\n"
    print(report)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with Path(summary).open("a", encoding="utf-8") as handle:
            handle.write(report)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
