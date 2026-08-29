package com.example.danmuapiapp.desktop.runtime

import java.io.File

/**
 * 后台运行实例归属判断。只有以下条件全部满足，UI 才能认领该实例：
 * 1. 健康响应 identity 等于安装级持久身份；
 * 2. 健康响应 ports.main 等于本地配置的实际端口；
 * 3. envHome / resolvedHome / cwd 三个目录都等于当前运行目录。
 *
 * 这条边界拒绝：终端直接启动核心（通常无 identity）、其他运行目录实例、
 * 同端口的其他本地服务，以及任何远端设备（探测只走 loopback）。
 */
object RuntimeOwnership {

    data class Health(
        val runtimeIdentity: String?,
        val port: Int?,
        val envHome: String?,
        val resolvedHome: String?,
        val cwd: String?,
    )

    fun isOwned(
        expectedIdentity: String,
        expectedPort: Int,
        expectedHome: File,
        health: Health,
    ): Boolean {
        if (expectedIdentity.isBlank() || health.runtimeIdentity != expectedIdentity) return false
        if (health.port != expectedPort) return false
        val expected = canonical(expectedHome) ?: return false
        return listOf(health.envHome, health.resolvedHome, health.cwd).all { raw ->
            raw?.let(::canonical) == expected
        }
    }

    private fun canonical(raw: String): String? = runCatching { File(raw).canonicalPath }.getOrNull()
    private fun canonical(raw: File): String? = runCatching { raw.canonicalPath }.getOrNull()
}
