package com.example.danmuapiapp.ui.component

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDialogPolicyTest {
    @Test
    fun `共享弹窗应只有一个滚动正文和可换行操作区`() {
        val source = readSource("app/src/main/java/com/example/danmuapiapp/ui/component/AppDialog.kt")

        assertTrue(source.contains("fun AppDialog("))
        assertTrue(source.contains("usePlatformDefaultWidth = false"))
        assertTrue(source.contains("decorFitsSystemWindows = false"))
        assertTrue(source.contains("safeDrawingPadding()"))
        assertTrue(source.contains("imePadding()"))
        assertTrue(source.contains("FlowRow("))
        assertTrue(source.contains("verticalScroll(rememberScrollState())"))
        assertFalse(source.contains("ModalBottomSheet"))
        assertFalse(source.contains("horizontalScroll"))
    }

    @Test
    fun `主代码不应残留旧弹窗实现与展示偏好`() {
        val mainDir = resolveRepoRoot().resolve("app/src/main/java")
        val source = Files.walk(mainDir).use { paths ->
            paths
                .filter { Files.isRegularFile(it) && (it.toString().endsWith(".kt") || it.toString().endsWith(".java")) }
                .map { it.toFile().readText() }
                .toList()
                .joinToString("\n")
        }

        assertFalse(source.contains("AppBottomSheetDialog"))
        assertFalse(source.contains("AppPanelDialog"))
        assertFalse(source.contains("AppDialogPlacement"))
        assertFalse(source.contains("DialogPresentationPreference"))
        assertFalse(source.contains("bottomSheetGesturesEnabled"))
        assertFalse(source.contains("DIALOG_STYLE_BOTTOM_SHEET"))
        assertFalse(source.contains("androidx.compose.material3.AlertDialog"))
        assertFalse(source.contains("ModalBottomSheet("))
    }

    private fun readSource(relativePath: String): String {
        return resolveRepoRoot().resolve(relativePath).toFile().readText()
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
