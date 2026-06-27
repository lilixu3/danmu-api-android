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
        val moduleSource = readXposedSource()
        val source = readSettingsOverlaySource()
        val overlayMethod = source.substringAfter("void showInjectionSettingsOverlay")
            .substringBefore("private ViewGroup findHostContentRoot")

        assertTrue(source.contains("activity.findViewById(android.R.id.content)"))
        assertTrue(moduleSource.contains("settingsOverlay.showInjectionSettingsOverlay(activity, backgroundAnchor, shellPortRef)"))
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
    fun `注入设置子页返回键应拦截影视壳官方返回入口先关闭 overlay`() {
        val moduleSource = readXposedSource()
        val source = readSettingsOverlaySource()
        val backHookMethod = source.substringAfter("private void installSettingsOverlayBackInterceptor")
            .substringBefore("private Method findHostBackHandlerMethod")
        val findBackMethod = source.substringAfter("private Method findHostBackHandlerMethod")
            .substringBefore("private boolean closeActiveSettingsOverlay")
        val closeActiveMethod = source.substringAfter("private boolean closeActiveSettingsOverlay")
            .substringBefore("private boolean isSettingsOverlayView")

        assertTrue(source.contains("installSettingsOverlayBackInterceptor(activity)"))
        assertTrue("FongMi/OK Android 13+ 返回链路是 OnBackInvokedCallback -> 官方 HomeActivity 返回方法，不能只 hook onBackPressed",
            backHookMethod.contains("findHostBackHandlerMethod(activity.getClass())"))
        assertTrue(backHookMethod.contains("host.installBackInterceptor(method, this::closeActiveSettingsOverlay)"))
        assertTrue(moduleSource.contains("hook(method).intercept"))
        assertTrue(moduleSource.contains("handler.closeActiveSettingsOverlay((Activity) thisObject)"))
        assertTrue("有注入 overlay 时必须直接消费官方返回入口，不能继续 chain.proceed() 触发官方返回到主界面",
            moduleSource.contains("return null"))
        assertTrue(source.contains("private Method findHostBackHandlerMethod(Class<?> cls)"))
        assertTrue("FongMi 5.5.2 HomeActivity 官方返回入口是 p0()", findBackMethod.contains("\"p0\""))
        assertTrue("OK影视 5.1.x HomeActivity 官方返回入口是 q()", findBackMethod.contains("\"q\""))
        assertTrue(findBackMethod.contains("getDeclaredMethod(methodName)"))
        assertTrue(closeActiveMethod.contains("findHostContentRoot(activity)"))
        assertTrue(closeActiveMethod.contains("isSettingsOverlayView(child)"))
        assertTrue(closeActiveMethod.contains("closeSettingsOverlay(child)"))

        assertFalse("不能只靠 onBackPressed；Android 13+ FongMi 返回会绕过它走 OnBackInvokedCallback -> HomeActivity.p0()",
            source.contains("findActivityOnBackPressedMethod"))
        assertFalse("不能只靠 OnKeyListener 处理返回；FongMi 官方返回链路会绕过注入 View",
            source.contains("root.setOnKeyListener"))
    }

    @Test
    fun `注入设置子页背景应裁剪复用宿主壁纸避免拼接色差`() {
        val moduleSource = readXposedSource()
        val source = readHostBackgroundsSource()
        val rowInjectorSource = readSettingsRowInjectorSource()
        val resolveMethod = source.substringAfter("static Drawable resolveHostPageBackground")
            .substringBefore("private static Drawable findMeaningfulAncestorBackgroundDrawable")

        assertTrue(readSettingsOverlaySource().contains("resolveHostPageBackground(activity, backgroundAnchor, hostPageContainer"))
        assertTrue(resolveMethod.contains("loadHostWallpaperPreviewDrawable(activity)"))
        assertTrue(resolveMethod.contains("captureHostWallpaperResourceBackground(activity, cropAnchor"))
        assertTrue(resolveMethod.contains("captureHostWallpaperBackground(activity, cropAnchor"))
        assertTrue(resolveMethod.contains("captureWindowBackground(activity, resolveBackgroundCaptureTarget(activity, cropAnchor), logger)"))
        assertTrue(source.contains("loadHostWallpaperResource(activity, wallIndex)"))
        assertTrue(source.contains("readHostPreferenceInt(activity, \"wall\", 1)"))
        assertTrue(source.contains("findDirectHostWallpaperLayer(content)"))
        assertTrue(source.contains("isKnownHostWallpaperLayerClass(className)"))
        assertTrue(rowInjectorSource.contains("cloneDrawable(anchorRow.getBackground())"))
        assertTrue(source.contains("\"s30\""))
        assertTrue(source.contains("\"Q3.k\""))
        assertFalse("FongMi 5.5.x dex 描述符是 Ls30;，运行时类名是 s30；不能保留 JADX 反编译目录里的假包名 defpackage.s30",
            source.contains("\"defpackage.s30\""))
        assertFalse("普通页面里的 @id/image 不能再作为壁纸发现依据", source.contains("hasWallpaperImageSignature"))
        assertFalse("普通页面里的 className.contains(\"wall\") 不能再作为壁纸发现依据", source.contains("className.toLowerCase(Locale.ROOT).contains(\"wall\")"))
    }

    @Test
    fun `OK影视静态窗口壁纸应按屏幕坐标裁剪避免顶底拼接色差`() {
        val source = readHostBackgroundsSource()
        val resolveMethod = source.substringAfter("static Drawable resolveHostPageBackground")
            .substringBefore("private static Drawable findMeaningfulAncestorBackgroundDrawable")
        val captureMethod = source.substringAfter("private static Drawable captureHostWallpaperBackground")
            .substringBefore("private static View resolveBackgroundCaptureTarget")

        assertTrue("OK影视 5.1.x 静态壁纸设置在 Window background，不能直接加载 wallpaper_1 当 overlay 本地背景",
            resolveMethod.contains("loadHostWallpaperPreviewDrawable(activity)"))
        assertTrue(resolveMethod.contains("captureHostWallpaperResourceBackground(activity, cropAnchor"))
        assertTrue(resolveMethod.contains("captureHostWallpaperBackground(activity, cropAnchor"))
        assertTrue(resolveMethod.contains("captureWindowBackground(activity, resolveBackgroundCaptureTarget(activity, cropAnchor), logger)"))
        assertTrue(resolveMethod.contains("findMeaningfulAncestorBackgroundDrawable(backgroundAnchor)"))
        assertFalse("资源兜底也必须按 decor/content 屏幕坐标裁剪，避免从 overlay 顶部重新铺图造成状态栏/底部导航拼接色差",
            resolveMethod.contains("Drawable resourceWall = loadHostWallpaperResource(activity)"))

        assertTrue(source.contains("private static Drawable captureWindowBackground(Activity activity, View target"))
        assertTrue(source.contains("window.getDecorView()"))
        assertTrue(source.contains("captureScreenAlignedDrawable(activity, background, decor, target)"))
        assertTrue(source.contains("private static Drawable captureScreenAlignedDrawable(Activity activity, Drawable source, View boundsAnchor, View target)"))
        assertTrue(source.contains("boundsAnchor.getLocationOnScreen(boundsLoc)"))
        assertTrue(source.contains("target.getLocationOnScreen(targetLoc)"))
        assertTrue(source.contains("canvas.translate(boundsLoc[0] - targetLoc[0], boundsLoc[1] - targetLoc[1])"))
        assertTrue(captureMethod.contains("if (wall == null || wall.getWidth() <= 0 || wall.getHeight() <= 0) return null;"))
        assertFalse(captureMethod.contains("captureWindowBackground(activity, target"))
    }

    private fun readXposedSource(): String {
        return readSource("app/src/main/java/com/example/danmuapiapp/xposed/DanmuXposedModule.java")
    }

    private fun readHostBackgroundsSource(): String {
        return readSource("app/src/main/java/com/example/danmuapiapp/xposed/DanmuXposedHostBackgrounds.java")
    }

    private fun readSettingsRowInjectorSource(): String {
        return readSource("app/src/main/java/com/example/danmuapiapp/xposed/DanmuXposedSettingsRowInjector.java")
    }

    private fun readSettingsOverlaySource(): String {
        return readSource("app/src/main/java/com/example/danmuapiapp/xposed/DanmuXposedSettingsOverlay.java")
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
