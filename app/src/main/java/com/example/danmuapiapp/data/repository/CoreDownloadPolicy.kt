package com.example.danmuapiapp.data.repository

/** Pure download error and progress rules shared by streamed archive downloads. */
internal object CoreDownloadPolicy {
    const val PROGRESS_EMIT_INTERVAL_MS = 300L

    fun failureMessage(httpCode: Int): String {
        return when (httpCode) {
            401, 403 -> "下载失败：GitHub 拒绝访问（HTTP $httpCode），请检查 Token、仓库权限或代理线路"
            404 -> "下载失败：仓库、分支或版本不存在（HTTP 404）"
            else -> "下载失败：GitHub 返回 HTTP $httpCode"
        }
    }

    fun shouldEmitProgress(
        now: Long,
        lastEmitAt: Long,
        downloadedBytes: Long,
        totalBytes: Long
    ): Boolean {
        return now - lastEmitAt >= PROGRESS_EMIT_INTERVAL_MS ||
            (totalBytes > 0L && downloadedBytes >= totalBytes)
    }

    fun progress(downloadedBytes: Long, totalBytes: Long): Float? {
        return if (totalBytes > 0L) {
            (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else {
            null
        }
    }

    fun completedBytes(totalBytes: Long, lastDownloadedBytes: Long): Long {
        return if (totalBytes > 0L) totalBytes else lastDownloadedBytes
    }
}
