package com.example.danmuapiapp.domain.model

import java.io.IOException

/**
 * 核心候选已经下载到 staging，但当前 App 无法安全补齐其运行时依赖。
 *
 * 这是可向 UI 明确展示的阻断结果：正式核心尚未被替换，原有版本（如有）保持不变。
 */
class CoreRuntimeDependencyUnavailableException(
    val missingDependencies: List<String>,
    val runtimePackUnavailableReason: String? = null,
    cause: Throwable? = null
) : IOException(
    buildMessage(
        missingDependencies = missingDependencies,
        runtimePackUnavailableReason = runtimePackUnavailableReason
    ),
    cause
) {
    companion object {
        private fun buildMessage(
            missingDependencies: List<String>,
            runtimePackUnavailableReason: String?
        ): String {
            val detail = runtimePackUnavailableReason
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.let { "签名依赖仓库未能提供匹配依赖包（$it）。" }
                .orEmpty()
            val missing = missingDependencies
                .asSequence()
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinct()
                .sorted()
                .joinToString(", ")
            return "${detail}检测到未安装的核心依赖：$missing。" +
                "本次核心变更已在替换前取消，正式核心未被替换，原有版本（如有）保持不变；" +
                "若依赖包含原生模块，则需要兼容该 Node ABI 的 App 版本。"
        }
    }
}
