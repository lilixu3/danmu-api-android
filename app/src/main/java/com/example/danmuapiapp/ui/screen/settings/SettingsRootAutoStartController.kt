package com.example.danmuapiapp.ui.screen.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.danmuapiapp.data.service.RootAutoStartModule
import com.example.danmuapiapp.data.service.RootAutoStartPrefs
import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.domain.repository.RuntimeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 设置页「Root 开机自启」子控制器：安装/卸载自启模块与开关状态。
 *
 * 行为与拆分前一致，仅把状态与协程从 ViewModel 中搬出。
 */
internal class SettingsRootAutoStartController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val runtimeRepo: RuntimeRepository,
    private val postMessage: (String) -> Unit
) {
    var rootBootAutoStartEnabled by mutableStateOf(
        RootAutoStartPrefs.isBootAutoStartEnabled(context)
    )
        private set
    var isRootAutoStartOperating by mutableStateOf(false)
        private set

    fun enableRootBootAutoStart() {
        if (isRootAutoStartOperating) return
        if (runtimeRepo.runtimeState.value.runMode != RunMode.Root) {
            postMessage("请先切换到 Root 模式")
            return
        }
        scope.launch {
            isRootAutoStartOperating = true
            val result = withContext(Dispatchers.IO) {
                RootAutoStartModule.installAndEnable(context)
            }
            if (result.ok) {
                RootAutoStartPrefs.setBootAutoStartEnabled(context, true)
                rootBootAutoStartEnabled = true
                postMessage("已安装模块并开启开机自启，建议重启设备验证")
            } else {
                postMessage("开启失败：${result.message}")
            }
            isRootAutoStartOperating = false
        }
    }

    fun disableRootBootAutoStart(uninstallModule: Boolean) {
        if (isRootAutoStartOperating) return
        scope.launch {
            isRootAutoStartOperating = true
            val result = withContext(Dispatchers.IO) {
                if (uninstallModule) RootAutoStartModule.uninstall()
                else RootAutoStartModule.disableOnly()
            }
            if (result.ok) {
                RootAutoStartPrefs.setBootAutoStartEnabled(context, false)
                rootBootAutoStartEnabled = false
                postMessage(
                    if (uninstallModule) {
                        "已卸载模块并关闭开机自启"
                    } else {
                        "已关闭开机自启（模块保留）"
                    }
                )
            } else {
                postMessage(
                    if (uninstallModule) {
                        "卸载失败：${result.message}"
                    } else {
                        "关闭失败：${result.message}"
                    }
                )
            }
            isRootAutoStartOperating = false
        }
    }

    fun refreshBootState() {
        rootBootAutoStartEnabled = RootAutoStartPrefs.isBootAutoStartEnabled(context)
    }
}
