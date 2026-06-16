package com.example.danmuapiapp.xposed

import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InjectedPlaybackDialogPolicyTest {

    @Test
    fun `播放弹窗不应反射宿主 BottomSheetDialog 伪类名`() {
        val source = readXposedSource()

        assertFalse("JADX 会把默认包 Lnl; 显示成 defpackage.nl，但运行时 Class.forName(\"defpackage.nl\") 不存在",
            source.contains("\"defpackage.nl\""))
        assertFalse("JADX 会把 k4.d 显示成 DialogC1061d 别名，但运行时 Class.forName(\"k4.DialogC1061d\") 不存在",
            source.contains("\"k4.DialogC1061d\""))
        assertFalse("宿主 BottomSheetDialog 反射失败后会落到 AlertDialog fallback，不能保留这条无效尝试",
            source.contains("createHostStyledBottomSheetDialog"))
        assertFalse(source.contains("configureHostBottomSheet"))
        assertFalse(source.contains("findDesignBottomSheet"))
        assertFalse(source.contains("findBottomSheetBehavior"))
    }

    @Test
    fun `播放底部弹窗应保持 AlertDialog 窗口宽度约束不能被全屏 fallback 放大`() {
        val source = readXposedSource()
        val showMethod = source.substringAfter("private void showManualSearchDialog")
            .substringBefore("/** A scroll container styled as a sheet panel surface. */")

        assertTrue(showMethod.contains("root.setBackground(t.topRoundedSheet(activity))"))
        assertTrue(showMethod.contains("new AlertDialog.Builder(activity)"))
        assertTrue(showMethod.contains(".setView(root)"))
        assertTrue(showMethod.contains(".create()"))
        assertTrue(showMethod.contains("window.setLayout((int) (width * 0.82f), ViewGroup.LayoutParams.WRAP_CONTENT)"))

        assertFalse("底部 AlertDialog fallback 不能设置成全屏窗口，否则截图中面板会被放大到约 95% 并贴住右侧边缘",
            showMethod.contains("WindowManager.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT"))
        assertFalse(source.contains("buildPlaybackSheetSurface"))
        assertFalse(source.contains("resolvePlaybackSheetPanelWidth"))
        assertFalse(source.contains("material_bottom_sheet_max_width"))
    }

    @Test
    fun `播放控制按钮应复制官方控制项样式而不是自造透明按钮`() {
        val source = readXposedSource()
        val styleMethod = source.substringAfter("private void copyHostControlStyle")
            .substringBefore("private Drawable cloneDrawable")

        assertTrue(source.contains("copyHostControlStyle(activity, button, anchorView)"))
        assertTrue(styleMethod.contains("cloneDrawable(anchorView.getBackground())"))
        assertTrue(styleMethod.contains("button.setPadding(anchorView.getPaddingLeft()"))
        assertTrue(styleMethod.contains("button.setShadowLayer"))
        assertTrue(styleMethod.contains("button.setMaxLines"))
        assertTrue(styleMethod.contains("button.setMaxEms"))
        assertTrue(styleMethod.contains("button.setIncludeFontPadding(anchorText.getIncludeFontPadding())"))
        assertFalse("不应再硬编码透明背景，官方控制项使用 selectableItemBackgroundBorderless", styleMethod.contains("setBackgroundColor(Color.TRANSPARENT)"))
    }

    @Test
    fun `播放控制按钮布局应保留官方 wrap_content 尺寸而不是硬塞固定高度`() {
        val source = readXposedSource()
        val createMethod = source.substringAfter("private View createButton")
            .substringBefore("private void copyHostControlStyle")
        val cloneLpMethod = source.substringAfter("private ViewGroup.LayoutParams cloneLayoutParamsForInsert")
            .substringBefore("private View findTaggedButton")

        assertTrue(cloneLpMethod.contains("int width = textButton && source != null ? source.width"))
        assertTrue(cloneLpMethod.contains("int height = textButton && source != null ? source.height"))
        assertFalse("官方控制项是 wrap_content，不应给注入 TextView 强制 dp(28) 最小高度", createMethod.contains("int height = dp(activity, 28)"))
        assertFalse("官方控制项是 wrap_content，不应把 TextView 的 wrap_content 转成固定高度", cloneLpMethod.contains("if (height <= 0 || height == ViewGroup.LayoutParams.MATCH_PARENT) height = dp(activity, 28)"))
    }

    @Test
    fun `官方弹幕控制项可见时应优先贴近弹幕按钮注入`() {
        val source = readXposedSource()
        val idBlock = source.substringAfter("private static final String[] SHELL_CONTROL_ANCHOR_IDS")
            .substringBefore("private static final String[] CONTAINER_ANCHOR_IDS")
        val priorityMethod = source.substringAfter("private int anchorPriority")
            .substringBefore("private String readViewText")

        assertTrue(idBlock.contains("\"danmaku\", \"ending\", \"episodes\""))
        assertTrue(priorityMethod.indexOf("弹幕") < priorityMethod.indexOf("片尾"))
    }

    @Test
    fun `播放页识别和锚点应收窄到官方 VideoActivity 控制栏`() {
        val source = readXposedSource()
        val hintsBlock = source.substringAfter("private static final String[] ACTIVITY_HINTS")
            .substringBefore("private static final String[] ANCHOR_TEXTS")

        assertTrue(source.contains("private boolean isKnownPlaybackActivityName"))
        assertTrue(source.contains("className.endsWith(\".VideoActivity\")"))
        assertTrue(source.contains("anchor != null && anchor.parent != null"))
        assertTrue(source.contains("\"episodes\""))
        assertFalse("不能只靠 vod 这类宽泛命中判断播放页", hintsBlock.contains("\"vod\""))
    }

    @Test
    fun `清理无效兜底并在控制栏重建后重新调度注入`() {
        val source = readXposedSource()

        assertTrue(source.contains("button.addOnAttachStateChangeListener"))
        assertTrue(source.contains("scheduleInject(activity)"))
        assertFalse(source.contains("skip floating injection"))
        assertFalse(source.contains("final boolean fromResource"))
        assertFalse(source.contains("private ShellMedia readShellMedia()"))
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
