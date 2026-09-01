#!/usr/bin/env python3
from __future__ import annotations

import argparse
import sys
from pathlib import Path
from zipfile import BadZipFile, ZipFile

DEFAULT_MAX_BYTES = 1_572_864
PROFILE_PATHS = {
    ".apk": "assets/dexopt/baseline.prof",
    ".aab": "BUNDLE-METADATA/com.android.tools.build.profiles/baseline.prof",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Verify that an Android APK/AAB contains a usable compiled Baseline Profile",
    )
    parser.add_argument("artifact", type=Path, help="Release APK or Android App Bundle")
    parser.add_argument(
        "--max-bytes",
        type=int,
        default=DEFAULT_MAX_BYTES,
        help=f"Maximum uncompressed baseline.prof size (default: {DEFAULT_MAX_BYTES})",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    artifact = args.artifact
    suffix = artifact.suffix.lower()
    profile_path = PROFILE_PATHS.get(suffix)
    if profile_path is None:
        print(f"ERROR: unsupported artifact type: {artifact}", file=sys.stderr)
        return 2
    if not artifact.is_file():
        print(f"ERROR: release artifact not found: {artifact}", file=sys.stderr)
        return 2
    if args.max_bytes <= 0:
        print("ERROR: --max-bytes must be positive", file=sys.stderr)
        return 2

    try:
        with ZipFile(artifact) as archive:
            try:
                info = archive.getinfo(profile_path)
            except KeyError:
                print(
                    f"ERROR: {artifact.name} does not contain {profile_path}",
                    file=sys.stderr,
                )
                return 1
    except BadZipFile:
        print(f"ERROR: invalid ZIP container: {artifact}", file=sys.stderr)
        return 2

    if info.file_size <= 0:
        print(f"ERROR: {profile_path} is empty in {artifact.name}", file=sys.stderr)
        return 1
    if info.file_size > args.max_bytes:
        print(
            f"ERROR: compiled Baseline Profile is {info.file_size:,} bytes; "
            f"limit is {args.max_bytes:,} bytes",
            file=sys.stderr,
        )
        return 1

    print(
        f"Verified {artifact.name}: {profile_path} "
        f"({info.file_size:,} bytes uncompressed, limit {args.max_bytes:,})"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
