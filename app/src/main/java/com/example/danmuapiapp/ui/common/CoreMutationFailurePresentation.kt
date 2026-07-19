package com.example.danmuapiapp.ui.common

import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.CoreRuntimeDependencyUnavailableException

data class CoreDependencyBlockedPrompt(
    val variant: ApiVariant,
    val variantLabel: String,
    val actionLabel: String,
    val missingDependencies: List<String>,
    val unavailableReason: String?
) {
    val title: String
        get() = "$variantLabel${actionLabel}已取消"

    val guidance: String
        get() = if (variant == ApiVariant.Custom) {
            "自定义核心不会查询稳定版或开发版签名依赖仓库。请调整自定义核心依赖，或安装已经内置这些依赖的 App 版本。"
        } else {
            "请稍后重试；若持续出现，请等待对应通道发布匹配的签名依赖包，或更新到已内置这些依赖的 App 版本。"
        }
}

internal data class CoreMutationFailurePresentation(
    val fallbackMessage: String?,
    val dependencyBlockedPrompt: CoreDependencyBlockedPrompt?
)

internal fun resolveCoreMutationFailure(
    error: Throwable,
    variant: ApiVariant,
    variantLabel: String,
    actionLabel: String,
    failurePrefix: String
): CoreMutationFailurePresentation {
    val dependencyError = error.findCoreRuntimeDependencyError()
    if (dependencyError != null) {
        val missing = dependencyError.missingDependencies
            .asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .sorted()
            .toList()
        return CoreMutationFailurePresentation(
            fallbackMessage = null,
            dependencyBlockedPrompt = CoreDependencyBlockedPrompt(
                variant = variant,
                variantLabel = variantLabel,
                actionLabel = actionLabel,
                missingDependencies = missing,
                unavailableReason = dependencyError.runtimePackUnavailableReason
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
            )
        )
    }

    return CoreMutationFailurePresentation(
        fallbackMessage = "$failurePrefix：${error.message ?: "未知错误"}",
        dependencyBlockedPrompt = null
    )
}

private fun Throwable.findCoreRuntimeDependencyError(): CoreRuntimeDependencyUnavailableException? {
    val visited = HashSet<Throwable>()
    var current: Throwable? = this
    while (current != null && visited.add(current)) {
        if (current is CoreRuntimeDependencyUnavailableException) return current
        current = current.cause
    }
    return null
}
