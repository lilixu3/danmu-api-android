package com.example.danmuapiapp.xposed

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InjectedSettingsOverlayPolicyTest {

    @Test
    fun `注入设置子页应挂到宿主 content 并在宿主切页时关闭 overlay`() {
        val source = readXposedSource()
        val overlayMethod = source.substringAfter("private void showInjectionSettingsOverlay")
            .substringBefore("private ViewGroup findHostContentRoot")

        assertTrue(source.contains("activity.findViewById(android.R.id.content)"))
        assertTrue(source.contains("showInjectionSettingsOverlay(activity, backgroundAnchor, shellPortRef)"))
        assertTrue(overlayMethod.contains("hostContent.addView(root, buildSettingsOverlayLayoutParams"))
        assertTrue(overlayMethod.contains("root.setTag(overlayState)"))
        assertTrue(source.contains("attachHostNavigationCloseGuard(root, overlayState)"))
        assertTrue(source.contains("closeSettingsOverlay(overlayRoot)"))
        assertTrue(source.contains("ViewTreeObserver.OnPreDrawListener preDrawGuard"))
        assertTrue(source.contains("observer.addOnPreDrawListener(preDrawGuard)"))
        assertTrue(source.contains("detachHostNavigationCloseGuard(view, state)"))

        assertFalse("设置子页不能再使用独立 Dialog Window，否则会和底部导航/沉浸背景分层冲突", overlayMethod.contains("new Dialog("))
        assertFalse("不应隐藏宿主设置页 root，底部导航切换时会造成叠页或恢复旧页", source.contains("hideHostPageForSettingsOverlay"))
        assertFalse("不应保留隐藏恢复逻辑，overlay 应靠自身背景遮住底层页面", source.contains("restoreHiddenViews"))
        assertFalse(source.contains("HiddenViewState"))
        assertFalse(source.contains("hiddenViews"))
    }

    @Test
    fun `注入设置子页背景应裁剪复用宿主壁纸避免拼接色差`() {
        val source = readXposedSource()

        assertTrue(source.contains("resolveHostPageBackground(activity, backgroundAnchor, hostPageContainer)"))
        assertTrue(source.contains("captureHostWallpaperBackground(activity, cropAnchor)"))
        assertTrue(source.contains("canvas.translate(wallLoc[0] - targetLoc[0], wallLoc[1] - targetLoc[1])"))
        assertTrue(source.contains("findDirectHostWallpaperLayer(content)"))
        assertTrue(source.contains("hasWallpaperImageSignature((ViewGroup) view)"))
        assertTrue(source.contains("\"image\".equals(safeResourceEntryName(child))"))
    }

    @Test
    fun `OK影视静态窗口壁纸应按屏幕坐标裁剪避免顶底拼接色差`() {
        val source = readXposedSource()
        val resolveMethod = source.substringAfter("private Drawable resolveHostPageBackground")
            .substringBefore("private Drawable resolveRowTemplateBackground")
        val captureMethod = source.substringAfter("private Drawable captureHostWallpaperBackground")
            .substringBefore("private View findHostWallpaperView")

        assertTrue("OK影视 5.1.x 静态壁纸设置在 Window background，不能直接加载 wallpaper_1 当 overlay 本地背景",
            captureMethod.contains("captureWindowBackground(activity, target)"))
        assertTrue(resolveMethod.contains("captureHostWallpaperResourceBackground(activity, cropAnchor)"))
        assertFalse("资源兜底也必须按 decor/content 屏幕坐标裁剪，避免从 overlay 顶部重新铺图造成状态栏/底部导航拼接色差",
            resolveMethod.contains("Drawable resourceWall = loadHostWallpaperResource(activity)"))

        assertTrue(source.contains("private Drawable captureWindowBackground(Activity activity, View target)"))
        assertTrue(source.contains("window.getDecorView()"))
        assertTrue(source.contains("captureScreenAlignedDrawable(activity, background, decor, target)"))
        assertTrue(source.contains("private Drawable captureScreenAlignedDrawable(Activity activity, Drawable source, View boundsAnchor, View target)"))
        assertTrue(source.contains("boundsAnchor.getLocationOnScreen(boundsLoc)"))
        assertTrue(source.contains("target.getLocationOnScreen(targetLoc)"))
        assertTrue(source.contains("canvas.translate(boundsLoc[0] - targetLoc[0], boundsLoc[1] - targetLoc[1])"))
    }

    private fun readXposedSource(): String {
        return resolveRepoRoot()
            .resolve("app/src/main/java/com/example/danmuapiapp/xposed/DanmuXposedModule.java")
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
