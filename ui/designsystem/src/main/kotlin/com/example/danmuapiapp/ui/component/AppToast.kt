package com.example.danmuapiapp.ui.component

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.max

private const val SUCCESS_TOAST_MILLIS = 1_600L
private const val WARNING_TOAST_MILLIS = 2_200L
private const val ERROR_TOAST_MILLIS = 2_800L
private const val ACTION_TOAST_MIN_MILLIS = 5_000L

enum class AppToastKind {
    Info,
    Success,
    Warning,
    Error
}

data class AppToastMessage(
    val id: Long,
    val text: String,
    val kind: AppToastKind,
    val durationMillis: Long,
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null
)

/**
 * Keeps one transient message at a time. A newer message replaces the current one instead of
 * entering the four-second Snackbar queue used by Material3.
 */
@Stable
class AppToastController {
    var currentMessage by mutableStateOf<AppToastMessage?>(null)
        private set

    private var nextId = 0L
    fun show(
        text: String,
        kind: AppToastKind? = null,
        durationMillis: Long? = null,
        actionLabel: String? = null,
        action: (() -> Unit)? = null
    ) {
        val normalizedText = text.trim()
        if (normalizedText.isEmpty()) return

        val resolvedKind = kind ?: inferAppToastKind(normalizedText)
        val hasAction = actionLabel != null && action != null
        val resolvedActionLabel = actionLabel?.trim()?.takeIf { it.isNotEmpty() && hasAction }
        val requestedDuration = durationMillis ?: defaultAppToastDurationMillis(
            kind = resolvedKind,
            text = normalizedText
        )
        val resolvedDuration = if (resolvedActionLabel != null) {
            max(requestedDuration, ACTION_TOAST_MIN_MILLIS)
        } else {
            requestedDuration.coerceAtLeast(SUCCESS_TOAST_MILLIS)
        }
        nextId += 1L
        currentMessage = AppToastMessage(
            id = nextId,
            text = normalizedText,
            kind = resolvedKind,
            durationMillis = resolvedDuration,
            actionLabel = resolvedActionLabel,
            action = action.takeIf { resolvedActionLabel != null }
        )
    }

    fun dismiss(id: Long? = null) {
        val current = currentMessage ?: return
        if (id == null || current.id == id) {
            currentMessage = null
        }
    }
}

/**
 * Process-wide transient feedback entry point.
 *
 * The controller intentionally owns no Context and falls back through the main thread when
 * called from a background callback. Services that may run without an Activity should continue
 * using their system Toast or a notification instead.
 */
object AppToast {
    internal val controller = AppToastController()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun show(
        text: String,
        kind: AppToastKind? = null,
        durationMillis: Long? = null,
        actionLabel: String? = null,
        action: (() -> Unit)? = null
    ) {
        runOnMain {
            controller.show(
                text = text,
                kind = kind,
                durationMillis = durationMillis,
                actionLabel = actionLabel,
                action = action
            )
        }
    }

    fun info(text: String) {
        show(text, AppToastKind.Info)
    }

    fun success(text: String) {
        show(text, AppToastKind.Success)
    }

    fun warning(text: String) {
        show(text, AppToastKind.Warning)
    }

    fun error(text: String) {
        show(text, AppToastKind.Error)
    }

    fun dismiss() {
        runOnMain { controller.dismiss() }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
}

internal fun inferAppToastKind(text: String): AppToastKind {
    val normalized = text.trim()
    if (normalized.isEmpty()) return AppToastKind.Info
    if (ERROR_TOAST_KEYWORDS.any(normalized::contains)) return AppToastKind.Error
    if (WARNING_TOAST_KEYWORDS.any(normalized::contains)) return AppToastKind.Warning
    if (SUCCESS_TOAST_KEYWORDS.any(normalized::contains)) return AppToastKind.Success
    return AppToastKind.Info
}

internal fun defaultAppToastDurationMillis(kind: AppToastKind, text: String): Long {
    val base = when (kind) {
        AppToastKind.Info,
        AppToastKind.Success -> SUCCESS_TOAST_MILLIS

        AppToastKind.Warning -> WARNING_TOAST_MILLIS
        AppToastKind.Error -> ERROR_TOAST_MILLIS
    }
    val visibleCharacters = text.count { !it.isWhitespace() }
    val longMessageExtra = when {
        kind == AppToastKind.Warning || kind == AppToastKind.Error -> when {
            visibleCharacters > 42 -> 800L
            visibleCharacters > 28 -> 400L
            else -> 0L
        }

        else -> 0L
    }
    return base + longMessageExtra
}

private val ERROR_TOAST_KEYWORDS = listOf(
    "失败",
    "错误",
    "异常",
    "无法",
    "拒绝",
    "不可用",
    "崩溃"
)

private val WARNING_TOAST_KEYWORDS = listOf(
    "请先",
    "请输入",
    "请选择",
    "未发现",
    "未检测到",
    "没有",
    "缺少",
    "缺失",
    "注意",
    "风险",
    "冲突",
    "暂停"
)

private val SUCCESS_TOAST_KEYWORDS = listOf(
    "成功",
    "已完成",
    "已保存",
    "已复制",
    "已导出",
    "已开启",
    "已关闭",
    "已更新",
    "已修改",
    "已删除",
    "已清空",
    "已重试",
    "已允许",
    "已添加"
)
