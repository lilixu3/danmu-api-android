package com.example.danmuapiapp

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.UserManager
import android.util.Log
import com.example.danmuapiapp.data.util.AppAppearancePrefs
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class DanmuApiApplication : Application() {

    companion object {
        private const val TAG = "DanmuApiApp"
    }

    override fun onCreate() {
        super.onCreate()

        // 标准做法：未解锁阶段不访问 CE 存储，避免 Direct Boot 期间崩溃。
        if (!isUserUnlockedSafe()) return

        runCatching {
            val prefs = getSharedPreferences(AppAppearancePrefs.PREFS_UI_LEGACY, MODE_PRIVATE)
            AppAppearancePrefs.applyNightMode(AppAppearancePrefs.readNightMode(prefs))
        }.onFailure {
            Log.w(TAG, "初始化夜间模式失败，已跳过：${it.message}")
        }
    }

    private fun isUserUnlockedSafe(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return true
        val um = getSystemService(Context.USER_SERVICE) as? UserManager ?: return true
        return runCatching { um.isUserUnlocked }.getOrDefault(true)
    }
}
