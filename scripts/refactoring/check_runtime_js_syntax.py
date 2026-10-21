#!/usr/bin/env python3
"""Run node --check for every JavaScript file declared by the runtime manifest."""

from __future__ import annotations

import argparse
import subprocess
from pathlib import Path

from runtime_assets import validate_manifest


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--repo-root",
        type=Path,
        default=Path(__file__).resolve().parents[2],
    )
    parser.add_argument("--node", default="node")
    args = parser.parse_args()

    validation = validate_manifest(args.repo_root.resolve())
    javascript = [entry for entry in validation.entries if entry.endswith((".js", ".cjs", ".mjs"))]
    for entry in javascript:
        result = subprocess.run(
            [args.node, "--check", str(validation.runtime_root / entry)],
            text=True,
            capture_output=True,
        )
        if result.returncode != 0:
            print(f"JavaScript syntax check failed: {entry}")
            if result.stdout:
                print(result.stdout)
            if result.stderr:
                print(result.stderr)
            return result.returncode
    print(f"JavaScript syntax check passed for {len(javascript)} manifest entries.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
