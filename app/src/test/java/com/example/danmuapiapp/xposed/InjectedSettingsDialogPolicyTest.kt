package com.example.danmuapiapp.xposed

import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InjectedSettingsDialogPolicyTest {

    @Test
    fun `注入输入弹窗应原位校验且不保留样式分支`() {
        val source = readSettingsDialogSource()
        val dialogSource = readSource("app/src/main/java/com/example/danmuapiapp/xposed/DanmuDialog.java")

        assertTrue(source.contains("DanmuDialog.showTextInput"))
        assertTrue(dialogSource.contains("if (message != null && !message.trim().isEmpty())"))
        assertTrue(dialogSource.contains("input.setError(message)"))
        assertTrue(dialogSource.contains("return;"))
        assertFalse(source.contains("setPositiveButton"))
        assertFalse(source.contains("dialogStyle"))
        assertFalse(source.contains("底部抽屉"))
    }

    @Test
    fun `注入设置应走自有弹窗不再融入宿主页面`() {
        val source = readSettingsDialogSource()
        val moduleSource = readXposedSource()

        assertTrue(source.contains("AlertDialog dialog = DanmuDialog.create(activity, root)"))
        assertTrue(source.contains("DanmuDialog.showCentered(dialog, activity"))
        assertTrue(moduleSource.contains("settingsDialog.show(activity, theme, shellPort, onChanged)"))

        assertFalse("设置不能再贴到宿主 content 上，宿主换版即失效", source.contains("android.R.id.content"))
        assertFalse("AlertDialog 自带返回键关闭，不需要 hook 宿主返回入口", source.contains("installBackInterceptor"))
        assertFalse(source.contains("attachHostNavigationCloseGuard"))
        assertFalse(source.contains("OnPreDrawListener"))
        assertFalse(source.contains("resolveHostPageBackground"))
    }

    @Test
    fun `宿主页面融合相关实现应已从注入源码中移除`() {
        val sources = readXposedSources()

        assertFalse(
            "不应再有设置 overlay 与宿主设置行注入",
            sources.any {
                it.contains("showInjectionSettingsOverlay") || it.contains("injectSettingsRow")
            }
        )
        assertFalse(
            "不应再 hook 宿主返回方法 p0()/q()",
            sources.any { it.contains("findHostBackHandlerMethod") }
        )
        assertFalse(
            "不应再截屏裁剪宿主壁纸做背景",
            sources.any {
                it.contains("resolveHostPageBackground") ||
                    it.contains("captureScreenAlignedDrawable") ||
                    it.contains("loadHostWallpaperPreviewDrawable")
            }
        )
        assertFalse(
            "不应再用轮询守卫自动关闭 overlay",
            sources.any { it.contains("closeActiveSettingsOverlay") }
        )
    }

    @Test
    fun `设置项应复用注入设置存储且键与默认值不变`() {
        val source = readSettingsDialogSource()
        val storeSource = readSource("app/src/main/java/com/example/danmuapiapp/xposed/DanmuXposedSettingsStore.java")

        assertTrue(source.contains("host.readInjectionSettings(activity, fallbackPort)"))
        assertTrue(source.contains("host.saveInjectionSettings(activity, updated)"))
        assertTrue(source.contains("host.readEpisodeShowTitles(activity)"))
        assertTrue(source.contains("host.saveEpisodeShowTitles(activity, next)"))

        assertTrue(storeSource.contains("prefs.getBoolean(KEY_UI_DARK_THEME, false)"))
        assertTrue(storeSource.contains("\"app_danmu_injection\""))
        assertTrue(storeSource.contains("\"ui_dark_theme\""))
        assertTrue(storeSource.contains("\"episode_show_titles\""))

        assertTrue(source.contains("port > 0 && port <= 65535"))
        assertTrue(source.contains("size >= 8 && size <= 80"))
        assertTrue(source.contains("\"影视壳端口\""))
        assertTrue(source.contains("\"弹幕大小\""))
        assertTrue(source.contains("\"时间轴偏移\""))
        assertTrue(source.contains("\"界面主题\""))
        assertTrue(source.contains("\"集详情显示\""))
    }

    private fun readXposedSource(): String {
        return readSource("app/src/main/java/com/example/danmuapiapp/xposed/DanmuXposedModule.java")
    }

    private fun readSettingsDialogSource(): String {
        return readSource("app/src/main/java/com/example/danmuapiapp/xposed/DanmuXposedSettingsDialog.java")
    }

    private fun readXposedSources(): List<String> {
        return resolveRepoRoot()
            .resolve("app/src/main/java/com/example/danmuapiapp/xposed")
            .toFile()
            .listFiles { file -> file.extension == "java" }
            .orEmpty()
            .map { it.readText() }
    }

    private fun readSource(relativePath: String): String {
        return resolveRepoRoot()
            .resolve(relativePath)
            .toFile()
            .readText()
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
