package com.example.danmuapiapp.ui.screen.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danmuapiapp.domain.repository.RuntimeRepository
import com.example.danmuapiapp.data.tunnel.TunnelActionResult
import com.example.danmuapiapp.data.tunnel.TunnelRepository
import com.example.danmuapiapp.data.tunnel.TunnelSettings
import com.example.danmuapiapp.data.tunnel.TunnelUiState
import com.example.danmuapiapp.data.tunnel.FrpcRelease
import com.example.danmuapiapp.data.tunnel.compareVersions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

@HiltViewModel
class TunnelViewModel @Inject constructor(
    private val repository: TunnelRepository,
    private val runtimeRepository: RuntimeRepository
) : ViewModel() {

    val state: StateFlow<TunnelUiState> = repository.state

    // The draft lives in the ViewModel, so rotation never resets edits or puts tokens in a Bundle.
    private val _draft = MutableStateFlow<TunnelDraft?>(null)
    internal val draft = _draft.asStateFlow()
    private val _savedDraft = MutableStateFlow<TunnelDraft?>(null)
    internal val savedDraft = _savedDraft.asStateFlow()
    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()

    private val _log = MutableStateFlow("")
    val log: StateFlow<String> = _log.asStateFlow()

    data class KernelUpdateUi(
        val checking: Boolean = false,
        val applying: Boolean = false,
        val latest: String = "",
        val available: Boolean = false,
        val message: String = "",
        val release: FrpcRelease? = null
    )

    private val _kernelUpdate = MutableStateFlow(KernelUpdateUi())
    val kernelUpdate: StateFlow<KernelUpdateUi> = _kernelUpdate.asStateFlow()

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        repository.refresh()
        if (_draft.value == null) {
            val initial = TunnelDraft.from(state.value.settings)
            _draft.value = initial
            _savedDraft.value = initial
        }
    }

    internal fun editDraft(value: TunnelDraft) { _draft.value = value }

    internal fun saveDraft(onResult: (TunnelActionResult) -> Unit) = viewModelScope.launch {
        val value = _draft.value ?: return@launch
        if (_saving.value) return@launch
        val check = value.check(state.value.servicePort)
        if (!check.ok) {
            onResult(TunnelActionResult(false, check.errors.first()))
            return@launch
        }
        _saving.value = true
        try {
            val result = repository.save(value.settings(), value.effectiveConfig(state.value.servicePort))
            if (result.ok) _savedDraft.value = value
            onResult(result)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            onResult(TunnelActionResult(false, "保存失败，请稍后重试"))
        } finally {
            _saving.value = false
        }
    }

    fun refreshLog() = viewModelScope.launch { _log.value = repository.readLog() }

    fun clearLog() = viewModelScope.launch {
        repository.clearLog()
        _log.value = repository.readLog()
    }

    fun save(
        settings: TunnelSettings,
        configText: String,
        onResult: (TunnelActionResult) -> Unit
    ) = viewModelScope.launch {
        onResult(repository.save(settings, configText))
    }

    fun start(onResult: (TunnelActionResult) -> Unit = {}) = viewModelScope.launch {
        onResult(repository.start())
    }

    fun stop(onResult: (TunnelActionResult) -> Unit = {}) = viewModelScope.launch {
        onResult(repository.stop())
    }

    /**
     * 启动并等状态收敛：普通模式要经过 :node 进程异步拉起，只刷新一次会显示旧状态。
     * 返回 (actionResult, converged)：converged=false 表示超时仍未到目标状态。
     */
    fun startAndWait(onResult: (TunnelActionResult, Boolean) -> Unit) = viewModelScope.launch {
        val result = repository.start()
        if (!result.ok) {
            onResult(result, false)
            return@launch
        }
        onResult(result, awaitRunning(expected = true))
    }

    fun stopAndWait(onResult: (TunnelActionResult, Boolean) -> Unit) = viewModelScope.launch {
        val result = repository.stop()
        onResult(result, awaitRunning(expected = false))
    }

    fun restartAndWait(onResult: (TunnelActionResult, Boolean) -> Unit) = viewModelScope.launch {
        val result = repository.restart()
        onResult(result, awaitRunning(expected = true))
    }

    fun checkKernelUpdate() = viewModelScope.launch {
        _kernelUpdate.value = KernelUpdateUi(checking = true)
        val current = state.value.kernelVersion
        repository.checkFrpcUpdate().fold(
            onSuccess = { release ->
                val newer = compareVersions(release.version, current) > 0
                _kernelUpdate.value = KernelUpdateUi(
                    latest = release.version,
                    available = newer,
                    message = if (newer) {
                        "发现新版本 ${release.version}（当前 $current）"
                    } else {
                        "已是最新版本（$current）"
                    },
                    release = release
                )
            },
            onFailure = { error ->
                _kernelUpdate.value = KernelUpdateUi(message = "检查失败：${error.message}")
            }
        )
    }

    fun applyKernelUpdate() = viewModelScope.launch {
        val release = _kernelUpdate.value.release ?: return@launch
        _kernelUpdate.value = _kernelUpdate.value.copy(applying = true)
        val result = repository.applyFrpcUpdate(release)
        _kernelUpdate.value = _kernelUpdate.value.copy(
            applying = false,
            message = result.message
        )
    }

    private suspend fun awaitRunning(
        expected: Boolean,
        timeoutMs: Long = 12_000L
    ): Boolean {
        // 广播驱动：等状态流到目标值即可，不做轮询
        return withTimeoutOrNull(timeoutMs) {
            repository.state.first { it.running == expected }
            true
        } ?: false
    }
}
