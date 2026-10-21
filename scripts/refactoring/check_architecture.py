#!/usr/bin/env python3
"""Check source dependency rules that must stay true during the refactor."""

from __future__ import annotations

import argparse
import re
from dataclasses import dataclass
from pathlib import Path


IMPORT_RE = re.compile(r"^\s*import\s+([A-Za-z0-9_.*]+)\s*$")
SOURCE_PACKAGE = Path("app/src/main/java/com/example/danmuapiapp")
APP_PACKAGE_PREFIX = "com.example.danmuapiapp."
PROJECT_DEPENDENCY_RE = re.compile(r'project\(\s*"([^"]+)"\s*\)')


@dataclass(frozen=True)
class ModuleRule:
    """WS8 Gradle 模块的依赖方向约束。"""

    name: str
    source_root: Path
    build_file: Path
    allowed_project_dependencies: tuple[str, ...]
    allowed_app_package_prefixes: tuple[str, ...]
    allow_android_imports: bool


MODULE_RULES = (
    ModuleRule(
        name=":core:model",
        source_root=Path("core/model/src/main/java"),
        build_file=Path("core/model/build.gradle.kts"),
        allowed_project_dependencies=(),
        allowed_app_package_prefixes=("com.example.danmuapiapp.core.model",),
        allow_android_imports=False,
    ),
    ModuleRule(
        name=":core:common",
        source_root=Path("core/common/src/main/java"),
        build_file=Path("core/common/build.gradle.kts"),
        allowed_project_dependencies=(":core:model",),
        allowed_app_package_prefixes=(
            "com.example.danmuapiapp.core.common",
            "com.example.danmuapiapp.core.model",
        ),
        allow_android_imports=False,
    ),
    ModuleRule(
        name=":ui:designsystem",
        source_root=Path("ui/designsystem/src/main/java"),
        build_file=Path("ui/designsystem/build.gradle.kts"),
        allowed_project_dependencies=(":core:model", ":core:common"),
        allowed_app_package_prefixes=(
            "com.example.danmuapiapp.ui.designsystem",
            "com.example.danmuapiapp.core.",
        ),
        allow_android_imports=True,
    ),
)


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

    for rule in MODULE_RULES:
        violations.extend(check_module(repo_root, rule))

    return violations


def check_module(repo_root: Path, rule: ModuleRule) -> list[Violation]:
    """校验单个 Gradle 模块的源码导入与模块间依赖方向。"""

    violations: list[Violation] = []

    source_root = repo_root / rule.source_root
    if source_root.is_dir():
        for path in sorted(source_root.rglob("*.kt")):
            for line_number, imported in _imports(path):
                if not rule.allow_android_imports and (
                    imported.startswith("android.") or imported.startswith("androidx.")
                ):
                    violations.append(
                        Violation(
                            path.relative_to(repo_root),
                            line_number,
                            imported,
                            f"{rule.name} must remain platform independent",
                        )
                    )
                elif imported.startswith(APP_PACKAGE_PREFIX) and not any(
                    imported.startswith(prefix) for prefix in rule.allowed_app_package_prefixes
                ):
                    violations.append(
                        Violation(
                            path.relative_to(repo_root),
                            line_number,
                            imported,
                            f"{rule.name} may only import its own package and :core modules",
                        )
                    )

    build_file = repo_root / rule.build_file
    if build_file.is_file():
        text = build_file.read_text(encoding="utf-8")
        for match in PROJECT_DEPENDENCY_RE.finditer(text):
            dependency = match.group(1)
            if dependency in rule.allowed_project_dependencies:
                continue
            violations.append(
                Violation(
                    rule.build_file,
                    text.count("\n", 0, match.start()) + 1,
                    dependency,
                    f"{rule.name} may only depend on {rule.allowed_project_dependencies or 'no project'}",
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
