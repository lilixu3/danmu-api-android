package com.example.danmuapiapp.data.util

/**
 * 本地核心 API 的 URL 拼装。
 *
 * 之前缓存管理、设备控制、运行时仓库和本地弹幕各写了一遍形如
 * `http://127.0.0.1:<port><tokenPath>/<path>` 的字符串，这里统一到一处，
 * 并统一 token / path 的前导斜杠写法。
 */
internal object RuntimeApiUrls {

    const val HOST = "127.0.0.1"

    /**
     * @param tokenPath 允许 `""`、`"/token"`、`"token"` 三种写法，空表示不带 token 段
     * @param path 允许 `""`、`"/api/x"`、`"api/x"` 三种写法
     */
    fun local(port: Int, tokenPath: String = "", path: String = ""): String {
        val token = tokenPath.trim().trim('/')
        val tail = path.trim().trim('/')
        return buildString {
            append("http://").append(HOST).append(':').append(port)
            if (token.isNotEmpty()) append('/').append(token)
            if (tail.isNotEmpty()) append('/').append(tail)
        }
    }
}
