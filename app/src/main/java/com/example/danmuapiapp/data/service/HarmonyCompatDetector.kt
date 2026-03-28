package com.example.danmuapiapp.data.service

import android.os.Build

/**
 * 轻量鸿蒙兼容环境探测器。
 *
 * 返回软结论：true = 疑似鸿蒙兼容环境，false = 普通 Android。
 * 不做跳转拦截，仅用于 UI 展示/入口显示。
 */
object HarmonyCompatDetector {

    @Volatile
    private var cachedResult: Boolean? = null

    fun isLikelyHarmonyCompat(): Boolean {
        cachedResult?.let { return it }
        val result = detectInternal()
        cachedResult = result
        return result
    }

    private fun detectInternal(): Boolean {
        // HarmonyOS NEXT 的 Build.FINGERPRINT 通常包含 "HarmonyOS"
        // 但卓易通兼容层可能覆盖部分字段，因此做多信号组合。
        val fingerprint = Build.FINGERPRINT.orEmpty().lowercase()
        if ("harmony" in fingerprint) return true
        val display = Build.DISPLAY.orEmpty().lowercase()
        if ("harmony" in display) return true

        // 某些卓易通版本会设置系统属性。
        val harmonyVersion = readSystemProperty("ro.build.version.harmonyos")
        if (!harmonyVersion.isNullOrBlank()) return true

        val brand = Build.BRAND.orEmpty().lowercase()
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()

        // 部分华为设备在卓易通下 brand 仍是 "huawei"
        // 但会有一些差异化属性
        if (brand in setOf("huawei", "honor") || manufacturer in setOf("huawei", "honor")) {
            val buildOs = readSystemProperty("ro.build.version.os")
            if (!buildOs.isNullOrBlank() && "harmony" in buildOs.lowercase()) return true
        }

        return false
    }

    private fun readSystemProperty(key: String): String? {
        return runCatching {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java)
            (method.invoke(null, key) as? String)?.trim()
        }.getOrNull()
    }

    /** 用于单元测试或调试时清除缓存 */
    fun resetCache() {
        cachedResult = null
    }
}
