#!/usr/bin/env python3
"""Validate and copy the manually maintained Node runtime asset set."""

from __future__ import annotations

import argparse
import json
import shutil
from dataclasses import dataclass
from pathlib import Path, PurePosixPath
from typing import Iterable


RUNTIME_RELATIVE_ROOT = Path("app/src/main/assets/nodejs-project")
MANIFEST_NAME = "app-runtime-assets.json"
EXCLUDED_SOURCE_DIRS = {"node_modules", "config"}


class RuntimeAssetError(ValueError):
    pass


@dataclass(frozen=True)
class ManifestValidation:
    runtime_root: Path
    entries: tuple[str, ...]
    unlisted_files: tuple[str, ...]

    @property
    def all_copied_files(self) -> tuple[str, ...]:
        return (MANIFEST_NAME, *self.entries)


def normalize_relative_path(raw: object) -> str:
    if not isinstance(raw, str):
        raise RuntimeAssetError(f"manifest entry is not a string: {raw!r}")
    value = raw.strip().replace("\\", "/")
    if not value:
        raise RuntimeAssetError("manifest contains an empty path")
    path = PurePosixPath(value)
    if path.is_absolute():
        raise RuntimeAssetError(f"manifest path must be relative: {value}")
    if any(part in {"", ".", ".."} for part in value.split("/")):
        raise RuntimeAssetError(f"manifest path escapes or normalizes the runtime root: {value}")
    return path.as_posix()


def _discover_runtime_files(runtime_root: Path) -> set[str]:
    discovered: set[str] = set()
    for path in runtime_root.rglob("*"):
        if not path.is_file():
            continue
        relative = path.relative_to(runtime_root)
        if relative.as_posix() == MANIFEST_NAME:
            continue
        if relative.parts and relative.parts[0] in EXCLUDED_SOURCE_DIRS:
            continue
        discovered.add(relative.as_posix())
    return discovered


def validate_manifest(repo_root: Path, require_complete: bool = True) -> ManifestValidation:
    runtime_root = repo_root / RUNTIME_RELATIVE_ROOT
    manifest_path = runtime_root / MANIFEST_NAME
    if not manifest_path.is_file():
        raise RuntimeAssetError(f"runtime manifest is missing: {manifest_path}")

    try:
        payload = json.loads(manifest_path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise RuntimeAssetError(f"runtime manifest is not valid JSON: {error}") from error
    if not isinstance(payload, list):
        raise RuntimeAssetError("runtime manifest must be a JSON array")

    entries = tuple(normalize_relative_path(item) for item in payload)
    if not entries:
        raise RuntimeAssetError("runtime manifest must not be empty")
    duplicates = sorted({entry for entry in entries if entries.count(entry) > 1})
    if duplicates:
        raise RuntimeAssetError(f"runtime manifest contains duplicate paths: {duplicates}")
    if MANIFEST_NAME in entries:
        raise RuntimeAssetError("runtime manifest must not list itself")

    missing = [entry for entry in entries if not (runtime_root / entry).is_file()]
    if missing:
        raise RuntimeAssetError(f"runtime manifest entries are missing: {missing}")

    discovered = _discover_runtime_files(runtime_root)
    unlisted = tuple(sorted(discovered.difference(entries)))
    stale = tuple(sorted(set(entries).difference(discovered)))
    if stale:
        raise RuntimeAssetError(f"runtime manifest contains paths outside the source audit: {stale}")
    if require_complete and unlisted:
        raise RuntimeAssetError(
            "runtime files exist but are absent from the manifest: "
            + ", ".join(unlisted)
        )

    return ManifestValidation(runtime_root, entries, unlisted)


def copy_assets(
    source_root: Path,
    destination_root: Path,
    entries: Iterable[str],
) -> tuple[str, ...]:
    copied: list[str] = []
    for entry in (MANIFEST_NAME, *entries):
        relative = normalize_relative_path(entry)
        source = source_root / relative
        if not source.is_file():
            raise RuntimeAssetError(f"cannot copy missing runtime asset: {source}")
        destination = destination_root / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, destination)
        copied.append(relative)
    return tuple(copied)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--repo-root",
        type=Path,
        default=Path(__file__).resolve().parents[2],
    )
    parser.add_argument(
        "--allow-incomplete",
        action="store_true",
        help="allow manifest-valid files that are not listed (audit output only)",
    )
    args = parser.parse_args()

    try:
        validation = validate_manifest(
            args.repo_root.resolve(),
            require_complete=not args.allow_incomplete,
        )
    except RuntimeAssetError as error:
        print(f"Runtime asset validation failed: {error}")
        return 1

    print(
        "Runtime asset manifest validated: "
        f"{len(validation.entries)} entries, "
        f"{len(validation.all_copied_files)} files including the manifest."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
