package com.example.danmuapiapp.ui.screen.home.support

/**
 * 缓存目录文件事件过滤策略：只有请求记录相关文件的变化才触发缓存刷新。
 *
 * 从 `HomeViewModel.shouldHandleCacheFileChange` 原样抽出，行为保持不变。
 */
internal fun shouldHandleHomeCacheFileChange(path: String?): Boolean {
    val name = path
        ?.substringAfterLast('/')
        ?.trim()
        ?.lowercase()
        .orEmpty()
    if (name.isBlank()) return false
    return name == "reqrecords" || name == "todayreqnum"
}
