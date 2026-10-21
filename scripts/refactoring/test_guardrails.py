#!/usr/bin/env python3

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from check_architecture import check_tree
from audit_runtime_hardcoding import audit
from runtime_assets import (
    MANIFEST_NAME,
    RuntimeAssetError,
    copy_assets,
    normalize_relative_path,
    validate_manifest,
)


class RuntimeAssetTests(unittest.TestCase):
    def _fixture(self) -> tuple[tempfile.TemporaryDirectory[str], Path]:
        temporary = tempfile.TemporaryDirectory()
        repo = Path(temporary.name)
        runtime = repo / "app/src/main/assets/nodejs-project"
        (runtime / "server").mkdir(parents=True)
        (runtime / "main.js").write_text("require('./server/app.js')\n", encoding="utf-8")
        (runtime / "server/app.js").write_text("module.exports = {}\n", encoding="utf-8")
        (runtime / MANIFEST_NAME).write_text(
            '["server/app.js", "main.js"]',
            encoding="utf-8",
        )
        return temporary, repo

    def test_normalizes_posix_relative_paths(self) -> None:
        self.assertEqual("server/app.js", normalize_relative_path("server\\app.js"))

    def test_rejects_path_traversal(self) -> None:
        for value in ("../secret", "/absolute", "server/../main.js", ""):
            with self.subTest(value=value):
                with self.assertRaises(RuntimeAssetError):
                    normalize_relative_path(value)

    def test_manifest_supports_nested_paths(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            runtime = repo / "app/src/main/assets/nodejs-project"
            (runtime / "server").mkdir(parents=True)
            (runtime / "server/app.js").write_text("ok\n", encoding="utf-8")
            (runtime / MANIFEST_NAME).write_text('["server/app.js"]', encoding="utf-8")
            validation = validate_manifest(repo)
            self.assertEqual(("server/app.js",), validation.entries)

    def test_copy_preserves_nested_directories(self) -> None:
        temporary, repo = self._fixture()
        try:
            validation = validate_manifest(repo)
            destination = repo / "copied"
            copied = copy_assets(validation.runtime_root, destination, validation.entries)
            self.assertEqual(
                (MANIFEST_NAME, "server/app.js", "main.js"),
                copied,
            )
            self.assertTrue((destination / "server/app.js").is_file())
        finally:
            temporary.cleanup()

    def test_hardcoding_audit_finds_clustered_runtime_names(self) -> None:
        temporary, repo = self._fixture()
        try:
            runtime = repo / "app/src/main/assets/nodejs-project"
            (runtime / "worker.js").write_text("module.exports = {}\n", encoding="utf-8")
            (runtime / MANIFEST_NAME).write_text(
                '["server/app.js", "main.js", "worker.js"]',
                encoding="utf-8",
            )
            source = repo / "app/src/main/java"
            source.mkdir(parents=True)
            (source / "Bad.kt").write_text(
                'val files = listOf("main.js", "server/app.js", "worker.js")\n',
                encoding="utf-8",
            )
            findings = audit(repo)
            self.assertEqual(1, len(findings))
        finally:
            temporary.cleanup()


class ArchitectureTests(unittest.TestCase):
    def test_rejects_data_to_ui_and_domain_to_data(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            source = repo / "app/src/main/java/com/example/danmuapiapp"
            (source / "data").mkdir(parents=True)
            (source / "domain").mkdir(parents=True)
            (source / "data/Bad.kt").write_text(
                "import com.example.danmuapiapp.ui.component.AppToast\n",
                encoding="utf-8",
            )
            (source / "domain/Bad.kt").write_text(
                "import com.example.danmuapiapp.data.repository.CoreRepositoryImpl\n",
                encoding="utf-8",
            )
            violations = check_tree(repo)
            self.assertEqual(2, len(violations))


if __name__ == "__main__":
    unittest.main()
