package com.example.danmuapiapp

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.UserManager
import com.example.danmuapiapp.data.service.AppDiagnosticLogger
import com.example.danmuapiapp.data.service.SystemHeartbeatScheduler
import com.example.danmuapiapp.data.util.AppAppearancePrefs
import com.example.danmuapiapp.data.util.DeviceCompatMode
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class DanmuApiApplication : Application() {

    companion object {
        private const val TAG = "DanmuApiApp"
    }

    override fun onCreate() {
        super.onCreate()
        AppDiagnosticLogger.installGlobalExceptionHandler(this)

        // 标准做法：未解锁阶段不访问 CE 存储，避免 Direct Boot 期间崩溃。
        if (!isUserUnlockedSafe()) return

        runCatching {
            val prefs = getSharedPreferences(AppAppearancePrefs.PREFS_UI_LEGACY, MODE_PRIVATE)
            AppAppearancePrefs.applyNightMode(AppAppearancePrefs.readNightMode(prefs))
        }.onFailure {
            AppDiagnosticLogger.w(this, TAG, "初始化夜间模式失败，已跳过：${it.message}", it)
        }

        if (DeviceCompatMode.shouldUseCompatMode(this)) {
            AppDiagnosticLogger.i(this, TAG, "检测到兼容设备，继续初始化系统心跳以支持 TV 实验性保活")
        }

        runCatching {
            SystemHeartbeatScheduler.refresh(this)
        }.onFailure {
            AppDiagnosticLogger.w(this, TAG, "初始化系统心跳调度失败，已跳过：${it.message}", it)
        }
    }

    private fun isUserUnlockedSafe(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return true
        val um = getSystemService(Context.USER_SERVICE) as? UserManager ?: return true
        return runCatching { um.isUserUnlocked }.getOrDefault(true)
    }
}
