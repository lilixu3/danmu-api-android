package com.example.danmuapiapp.data.service

import android.content.Context
import android.content.SharedPreferences
import com.example.danmuapiapp.domain.model.RunMode

/**
 * 运行模式读写工具，兼容旧版 root_mode 布尔值。
 */
object RuntimeModePrefs {

    const val PREFS_NAME = "danmu_node_run_mode"
    const val KEY_RUN_MODE = "run_mode"
    const val KEY_ROOT_MODE_LEGACY = "root_mode"
    private const val RUNTIME_MIRROR_PREFS = "runtime"

    fun get(context: Context): RunMode {
        val legacyPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hasLegacy = legacyPrefs.contains(KEY_RUN_MODE) || legacyPrefs.contains(KEY_ROOT_MODE_LEGACY)
        if (hasLegacy) return get(legacyPrefs)
        val runtimePrefs = context.getSharedPreferences(RUNTIME_MIRROR_PREFS, Context.MODE_PRIVATE)
        return get(runtimePrefs)
    }

    fun get(prefs: SharedPreferences): RunMode {
        val byString = RunMode.fromKey(prefs.getString(KEY_RUN_MODE, null))
        if (byString != null) return byString
        return if (prefs.getBoolean(KEY_ROOT_MODE_LEGACY, false)) {
            RunMode.Root
        } else {
            RunMode.Normal
        }
    }

    fun put(context: Context, mode: RunMode) {
        val legacyPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        put(legacyPrefs, mode)
        // 维持 runtime 镜像，避免监听 runtime 偏好的逻辑失效。
        val runtimePrefs = context.getSharedPreferences(RUNTIME_MIRROR_PREFS, Context.MODE_PRIVATE)
        put(runtimePrefs, mode)
    }

    fun put(prefs: SharedPreferences, mode: RunMode) {
        prefs.edit()
            .putString(KEY_RUN_MODE, mode.key)
            // 兼容旧逻辑：仅 Root 模式视为“高权限模式”
            .putBoolean(KEY_ROOT_MODE_LEGACY, mode == RunMode.Root)
            .apply()
    }
}
