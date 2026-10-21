#!/usr/bin/env python3
"""Check source dependency rules that must stay true during the refactor."""

from __future__ import annotations

import argparse
import re
from dataclasses import dataclass
from pathlib import Path


IMPORT_RE = re.compile(r"^\s*import\s+([A-Za-z0-9_.*]+)\s*$")
SOURCE_PACKAGE = Path("app/src/main/java/com/example/danmuapiapp")


@dataclass(frozen=True)
class Violation:
    path: Path
    line: int
    imported: str
    rule: str

    def render(self) -> str:
        return f"{self.path}:{self.line}: {self.imported} ({self.rule})"


def _imports(path: Path) -> list[tuple[int, str]]:
    found: list[tuple[int, str]] = []
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        match = IMPORT_RE.match(line)
        if match:
            found.append((line_number, match.group(1)))
    return found


def check_tree(repo_root: Path) -> list[Violation]:
    source_root = repo_root / SOURCE_PACKAGE
    violations: list[Violation] = []

    for path in source_root.rglob("*.kt"):
        relative = path.relative_to(source_root)
        layer = relative.parts[0] if relative.parts else ""
        for line_number, imported in _imports(path):
            if layer == "domain":
                forbidden = tuple(
                    prefix
                    for prefix in (
                        "com.example.danmuapiapp.data.",
                        "com.example.danmuapiapp.ui.",
                        "android.",
                        "androidx.",
                    )
                    if imported.startswith(prefix)
                )
                if forbidden:
                    violations.append(
                        Violation(
                            path.relative_to(repo_root),
                            line_number,
                            imported,
                            "domain must remain platform/UI/data independent",
                        )
                    )
            elif layer == "data" and imported.startswith("com.example.danmuapiapp.ui."):
                violations.append(
                    Violation(
                        path.relative_to(repo_root),
                        line_number,
                        imported,
                        "data must not depend on UI",
                    )
                )

    return violations


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--repo-root",
        type=Path,
        default=Path(__file__).resolve().parents[2],
    )
    args = parser.parse_args()

    violations = check_tree(args.repo_root.resolve())
    if violations:
        print("Architecture boundary violations:")
        for violation in violations:
            print(f"  {violation.render()}")
        return 1

    print("Architecture boundary check passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
