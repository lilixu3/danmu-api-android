package com.example.danmuapiapp.data.service

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 管理启动阶段的预热流程与 UI 状态。
 */
@Singleton
class RuntimeWarmupCoordinator @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "RuntimeWarmup"
        private const val WARMUP_TIMEOUT_MS = 45_000L
    }

    sealed interface UiState {
        data object NotStarted : UiState
        data class Running(val title: String, val detail: String) : UiState
        data object Ready : UiState
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)

    private val _uiState = MutableStateFlow<UiState>(UiState.NotStarted)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun startIfNeeded() {
        if (!started.compareAndSet(false, true)) return

        // 先切到可见状态，避免系统 Splash 长时间停留造成“白屏感知”。
        _uiState.value = UiState.Running(
            title = "正在准备启动",
            detail = "正在检查运行环境"
        )

        scope.launch {
            RuntimeWarmupManager.ensurePendingFlagForCurrentVersion(context)
            if (!RuntimeWarmupManager.needsForegroundWarmup(context)) {
                _uiState.value = UiState.Ready
                return@launch
            }

            _uiState.value = UiState.Running(
                title = "正在检查运行环境",
                detail = "更新后首次启动会进行一次依赖校验"
            )

            val uiUpdatesEnabled = AtomicBoolean(true)
            val warmupDeferred = scope.async {
                RuntimeWarmupManager.runWarmup(context) { step ->
                    if (uiUpdatesEnabled.get()) {
                        _uiState.value = when (step) {
                            RuntimeWarmupManager.Step.Checking -> UiState.Running(
                                title = "正在检查运行环境",
                                detail = "正在确认目录与版本信息"
                            )

                            RuntimeWarmupManager.Step.Extracting -> UiState.Running(
                                title = "正在加载依赖",
                                detail = "首次加载会稍慢，后续启动将恢复正常"
                            )

                            RuntimeWarmupManager.Step.Syncing -> UiState.Running(
                                title = "正在同步配置",
                                detail = "即将进入首页"
                            )
                        }
                    }
                }
            }
            val completed = withTimeoutOrNull<Boolean>(WARMUP_TIMEOUT_MS) {
                warmupDeferred.await().isSuccess
            }
            uiUpdatesEnabled.set(false)

            if (completed == true) {
                _uiState.value = UiState.Ready
                return@launch
            }

            val reason = if (completed == null) {
                "预热超时，已跳过"
            } else {
                "预热失败，已跳过"
            }
            Log.w(TAG, reason)
            RuntimeWarmupManager.skipPendingForCurrentVersion(context, reason)
            _uiState.value = UiState.Ready
        }
    }
}
