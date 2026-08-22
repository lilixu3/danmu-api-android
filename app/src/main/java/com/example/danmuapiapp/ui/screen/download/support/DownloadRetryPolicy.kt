package com.example.danmuapiapp.ui.screen.download

/**
 * Keeps retry decisions independent from the queue coroutine so permanent
 * request and local-file failures do not get sent repeatedly.
 */
internal object DownloadRetryPolicy {
    fun shouldTriggerBackoff(httpCode: Int?, detail: String): Boolean {
        if (httpCode == 429 || httpCode == 403 || httpCode == 408 || httpCode == 425) {
            return true
        }
        if (httpCode != null && httpCode in 500..599) return true

        val text = detail.lowercase()
        return containsAny(
            text,
            "timeout",
            "timed out",
            "连接超时",
            "请求超时",
            "unable to resolve host",
            "unknownhost",
            "connection reset",
            "connection refused",
            "failed to connect",
            "network is unreachable",
            "broken pipe",
            "socket",
            "eof",
            "网络错误",
            "网络不可达",
            "连接失败",
            "连接重置",
            "连接被拒绝",
            "流被重置"
        )
    }

    /**
     * A stale ID should be resolved once before deciding whether the failure
     * is retryable. The caller must gate this with the per-run rebuild flag.
     */
    fun shouldRebuildChainForStaleFailure(httpCode: Int?, detail: String): Boolean {
        if (httpCode == 400 || httpCode == 404 || httpCode == 410 || httpCode == 422) {
            return true
        }
        val text = detail.lowercase()
        if (httpCode == 500 && containsAny(
                text,
                "invalid",
                "无效",
                "不存在",
                "not found",
                "episode"
            )
        ) {
            return true
        }
        return containsAny(
            text,
            "missing or invalid",
            "参数错误",
            "episodeid",
            "弹幕数据为空",
            "资源不存在"
        )
    }

    /**
     * 4xx and known validation/storage errors are deterministic. Unknown
     * exceptions remain retryable for compatibility, while transport errors
     * are explicitly classified for backoff.
     */
    fun shouldRetryFailure(httpCode: Int?, detail: String): Boolean {
        if (httpCode != null) {
            return when {
                httpCode == 403 || httpCode == 408 || httpCode == 425 || httpCode == 429 -> true
                httpCode in 500..599 -> true
                httpCode in 400..499 -> false
                else -> false
            }
        }

        val text = detail.lowercase()
        if (containsAny(text, *PERMANENT_MARKERS)) return false
        return true
    }

    private fun containsAny(text: String, vararg markers: String): Boolean {
        return markers.any(text::contains)
    }

    private val PERMANENT_MARKERS = arrayOf(
        "保存目录",
        "目录不可写",
        "目录无效",
        "重新授权目录权限",
        "无法写入",
        "创建文件失败",
        "返回内容不是",
        "返回内容为空",
        "格式",
        "解析失败",
        "缺少",
        "不是有效 json",
        "不是 xml",
        "核心实际返回格式",
        "参数错误",
        "资源不存在",
        "弹幕数据为空",
        "未找到匹配剧集"
    )
}
