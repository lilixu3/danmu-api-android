package com.example.danmuapiapp.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.CoreUpdatePlan
import com.example.danmuapiapp.domain.model.CoreUpdateRequest
import com.example.danmuapiapp.domain.repository.CoreRepository
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** 在停止服务和替换核心之前等待用户选择，三个更新入口共用。 */
class CoreUpdateChoiceController {
    var pendingPlan by mutableStateOf<CoreUpdatePlan?>(null)
        private set
    private var continuation: CancellableContinuation<CoreUpdateRequest?>? = null

    suspend fun prepare(repository: CoreRepository, variant: ApiVariant): Result<CoreUpdateRequest?> {
        val result = repository.prepareCoreUpdate(variant)
        val plan = result.getOrElse { return Result.failure(it) }
        return Result.success(awaitChoice(plan))
    }

    internal suspend fun awaitChoice(plan: CoreUpdatePlan): CoreUpdateRequest? {
        if (!plan.requiresPullRequestChoice) return CoreUpdateRequest(plan, false)
        check(continuation == null) { "已有更新确认正在等待处理" }
        return suspendCancellableCoroutine { waiting ->
            pendingPlan = plan
            continuation = waiting
            waiting.invokeOnCancellation {
                if (continuation === waiting) {
                    continuation = null
                    pendingPlan = null
                }
            }
        }
    }

    fun choose(keepPullRequests: Boolean) {
        val plan = pendingPlan ?: return
        finish(CoreUpdateRequest(plan, keepPullRequests))
    }

    fun dismiss() = finish(null)

    private fun finish(request: CoreUpdateRequest?) {
        val waiting = continuation
        continuation = null
        pendingPlan = null
        if (waiting?.isActive == true) waiting.resume(request)
    }
}
