package com.example.danmuapiapp.ui.screen.home

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeDialogPresentationPolicyTest {
    @Test
    fun screenDialogsUseSharedAdaptiveDialogPresentation() {
        val screenSourceDir = resolveRepoRoot()
            .resolve("app/src/main/java/com/example/danmuapiapp/ui/screen")
        val offenders = Files.walk(screenSourceDir).use { paths ->
            paths
                .filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
                .filter { path -> path.toFile().readText().contains("ModalBottomSheet(") }
                .map { screenSourceDir.relativize(it).toString() }
                .sorted()
                .toList()
        }

        assertTrue(
            "页面弹窗应通过共享自适应弹窗入口，以遵循全局居中/底部弹窗设置；仍直接使用 ModalBottomSheet 的文件：$offenders",
            offenders.isEmpty()
        )
    }

    private fun resolveRepoRoot(): Path {
        var current = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
        while (true) {
            if (current.resolve("settings.gradle.kts").exists() || current.resolve("settings.gradle").exists()) {
                return current
            }
            current.parent?.let { current = it } ?: break
        }
        error("Cannot resolve repository root from user.dir=${System.getProperty("user.dir")}")
    }
}
