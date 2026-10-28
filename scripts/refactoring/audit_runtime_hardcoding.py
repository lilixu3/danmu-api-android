#!/usr/bin/env python3
"""Report places that duplicate the runtime asset filename set."""

from __future__ import annotations

import argparse
import re
from dataclasses import dataclass
from pathlib import Path

from runtime_assets import RuntimeAssetError, validate_manifest


SCAN_ROOTS = (
    Path("app/src/main/java"),
    Path("app/build.gradle.kts"),
    # WS7 起 Node 资产与打包校验逻辑迁到 build-logic 预编译脚本插件，审计必须跟随。
    Path("build-logic/src/main/kotlin"),
    # WS8 起代码开始迁入 Gradle 模块，审计范围必须覆盖新模块源码。
    Path("core"),
    Path("ui"),
    Path("scripts"),
)
IGNORED_PREFIXES = (
    Path("scripts/refactoring"),
)
CLUSTER_THRESHOLD = 3
IGNORED_PATH_PARTS = ("build",)


@dataclass(frozen=True)
class Finding:
    path: Path
    line: int
    names: tuple[str, ...]

    def render(self) -> str:
        return f"{self.path}:{self.line}: {', '.join(self.names)}"


def _iter_files(repo_root: Path) -> list[Path]:
    files: list[Path] = []
    for root in SCAN_ROOTS:
        target = repo_root / root
        candidates = [target] if target.is_file() else list(target.rglob("*"))
        for path in candidates:
            if not path.is_file():
                continue
            relative = path.relative_to(repo_root)
            if any(relative.is_relative_to(prefix) for prefix in IGNORED_PREFIXES):
                continue
            # 跳过各模块的构建产物目录，只审计源码。
            if any(part in IGNORED_PATH_PARTS for part in relative.parts[:-1]):
                continue
            if path.suffix not in {".kt", ".kts", ".py", ".sh", ".cjs", ".js"}:
                continue
            files.append(path)
    return sorted(files)


def audit(repo_root: Path) -> list[Finding]:
    validation = validate_manifest(repo_root)
    names = validation.entries
    findings: list[Finding] = []
    for path in _iter_files(repo_root):
        try:
            lines = path.read_text(encoding="utf-8").splitlines()
        except UnicodeDecodeError:
            continue
        for line_number, line in enumerate(lines, 1):
            present = tuple(name for name in names if re.search(rf"(?<![A-Za-z0-9_.-]){re.escape(name)}(?![A-Za-z0-9_.-])", line))
            if len(present) >= CLUSTER_THRESHOLD:
                findings.append(
                    Finding(
                        path.relative_to(repo_root),
                        line_number,
                        present,
                    )
                )
    return findings


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--repo-root",
        type=Path,
        default=Path(__file__).resolve().parents[2],
    )
    parser.add_argument("--fail-on-findings", action="store_true")
    args = parser.parse_args()

    try:
        findings = audit(args.repo_root.resolve())
    except RuntimeAssetError as error:
        print(f"Runtime hardcoding audit failed to load manifest: {error}")
        return 1

    if not findings:
        print("Runtime hardcoding audit passed: no duplicated filename clusters.")
        return 0

    print(f"Runtime hardcoding audit found {len(findings)} duplicated filename cluster(s):")
    for finding in findings:
        print(f"  {finding.render()}")
    return 1 if args.fail_on_findings else 0


if __name__ == "__main__":
    raise SystemExit(main())
