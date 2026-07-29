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
    fun `播放弹窗应使用共享居中窗口并移除底部抽屉分支`() {
        val source = readManualSearchDialogSource()
        val dialogSource = readDialogSource()
        val showMethod = source.substringAfter("void show(Activity activity)")
            .substringBefore("private ScrollView buildContentScroll")

        assertTrue(showMethod.contains("DanmuDialog.create(activity, root)"))
        assertTrue(showMethod.contains("DanmuDialog.showCentered(dialog, activity,"))
        assertTrue(dialogSource.contains("window.setGravity(Gravity.CENTER)"))
        assertTrue(dialogSource.contains("window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)"))
        assertFalse(source.contains("topRoundedSheet"))
        assertFalse(source.contains("Gravity.BOTTOM"))
        assertFalse(source.contains("DIALOG_STYLE"))
        assertFalse(source.contains("dialogStyle"))
    }

    @Test
    fun `播放弹窗横屏应双列并列而竖屏按阶段切换`() {
        val source = readManualSearchDialogSource()
        val showMethod = source.substringAfter("void show(Activity activity)")
            .substringBefore("private ScrollView buildContentScroll")

        assertTrue(source.contains("DanmuDialog.isLandscape(activity)"))
        assertTrue("横屏两列必须同时可见，不能沿用竖屏的阶段互斥可见性",
            showMethod.contains("if (!landscape) {"))
        assertTrue(showMethod.contains("MAX_WIDTH_LANDSCAPE_DP : MAX_WIDTH_PORTRAIT_DP"))
        assertTrue("横屏分集网格列数上限要比竖屏宽",
            source.contains("clamp(columns, 4, landscape ? 12 : 8)"))
    }

    @Test
    fun `播放弹窗常驻搜索框且遥控器可遍历分集网格`() {
        val source = readManualSearchDialogSource()
        val uiSource = readUiSource()

        assertTrue("搜索框在任何阶段都要留在原位，返回不应重建输入区",
            source.contains("root.addView(searchRow"))
        assertTrue(source.contains("DanmuUi.wireGridFocus(episodeGrid, columns)"))
        assertTrue(uiSource.contains("cell.setNextFocusLeftId"))
        assertTrue(uiSource.contains("cell.setNextFocusUpId"))
        assertTrue(source.contains("DanmuDialog.focusFirst(dialog, searchButton)"))
    }

    @Test
    fun `推送进度应通过回调回传而不是持有弹窗控件`() {
        val source = readManualSearchDialogSource()
        val coordinatorSource = readPushCoordinatorSource()

        assertTrue(source.contains("PushFeedback feedback"))
        assertTrue(coordinatorSource.contains("void pushCandidate(Activity activity, CandidateHandle candidate, int shellPort, PushFeedback feedback)"))
        assertTrue(coordinatorSource.contains("void autoPushCurrent(Activity activity, int fallbackPort, PushFeedback feedback)"))
        assertTrue("推送记录弹窗应搬出推送协调器", coordinatorSource.contains("List<String> pushHistorySnapshot()"))
        assertFalse("推送协调器不应再直接构建弹窗视图", coordinatorSource.contains("DanmuDialog.root(activity"))
        assertFalse(coordinatorSource.contains("TextView statusText"))
        assertFalse(coordinatorSource.contains("TextView notifyDot"))
    }

    @Test
    fun `播放弹窗异步结果应绑定当前请求与弹窗生命周期`() {
        val source = readManualSearchDialogSource()

        assertTrue(source.contains("private static final class SearchDialogState"))
        assertTrue(source.contains("dialog.setOnDismissListener(d -> state.active = false)"))
        assertTrue(source.contains("requestId != state.searchRequestId"))
        assertTrue(source.contains("requestId != state.detailRequestId"))
        assertFalse(source.contains("final int[] selectedEpisodeIndex"))
        assertFalse(source.contains("final boolean[] searching"))
    }

    @Test
    fun `播放控制按钮应复制官方控制项样式而不是自造透明按钮`() {
        val source = readPlaybackControlsSource()
        val styleMethod = source.substringAfter("private static void copyHostControlStyle")
            .substringBefore("private static Drawable cloneDrawable")

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
        val source = readPlaybackControlsSource()
        val createMethod = source.substringAfter("static View createButton")
            .substringBefore("private static void copyHostControlStyle")
        val cloneLpMethod = source.substringAfter("static ViewGroup.LayoutParams cloneLayoutParamsForInsert")
            .substringBefore("static View findTaggedButton")

        assertTrue(cloneLpMethod.contains("int width = textButton && source != null ? source.width"))
        assertTrue(cloneLpMethod.contains("int height = textButton && source != null ? source.height"))
        assertFalse("官方控制项是 wrap_content，不应给注入 TextView 强制 dp(28) 最小高度", createMethod.contains("int height = dp(activity, 28)"))
        assertFalse("官方控制项是 wrap_content，不应把 TextView 的 wrap_content 转成固定高度", cloneLpMethod.contains("if (height <= 0 || height == ViewGroup.LayoutParams.MATCH_PARENT) height = dp(activity, 28)"))
    }

    @Test
    fun `官方弹幕控制项可见时应优先贴近弹幕按钮注入`() {
        val source = readPlaybackControlsSource()
        val idBlock = source.substringAfter("private static final String[] SHELL_CONTROL_ANCHOR_IDS")
            .substringBefore("private static final String[] CONTAINER_ANCHOR_IDS")
        val priorityMethod = source.substringAfter("private static int anchorPriority")
            .substringBefore("private static String readViewText")

        assertTrue(idBlock.contains("\"danmaku\", \"ending\", \"episodes\""))
        assertTrue(priorityMethod.indexOf("弹幕") < priorityMethod.indexOf("片尾"))
    }

    @Test
    fun `播放页识别和锚点应收窄到官方 VideoActivity 控制栏`() {
        val source = readPlaybackControlsSource()
        val hintsBlock = source.substringAfter("private static final String[] ACTIVITY_HINTS")
            .substringBefore("private static final String[] ANCHOR_TEXTS")

        assertTrue(source.contains("private static boolean isKnownPlaybackActivityName"))
        assertTrue(source.contains("className.endsWith(\".VideoActivity\")"))
        assertTrue(source.contains("anchor == null || anchor.parent == null"))
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
        return readSource("app/src/main/java/com/example/danmuapiapp/xposed/DanmuXposedModule.java")
    }

    private fun readPlaybackControlsSource(): String {
        return readSource("app/src/main/java/com/example/danmuapiapp/xposed/DanmuXposedPlaybackControls.java")
    }

    private fun readManualSearchDialogSource(): String {
        return readSource("app/src/main/java/com/example/danmuapiapp/xposed/DanmuXposedManualSearchDialog.java")
    }

    private fun readDialogSource(): String {
        return readSource("app/src/main/java/com/example/danmuapiapp/xposed/DanmuDialog.java")
    }

    private fun readUiSource(): String {
        return readSource("app/src/main/java/com/example/danmuapiapp/xposed/DanmuUi.java")
    }

    private fun readPushCoordinatorSource(): String {
        return readSource("app/src/main/java/com/example/danmuapiapp/xposed/DanmuXposedPushCoordinator.java")
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
