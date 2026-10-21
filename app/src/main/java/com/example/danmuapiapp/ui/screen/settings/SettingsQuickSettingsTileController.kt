package com.example.danmuapiapp.ui.screen.settings

import android.app.Activity
import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import com.example.danmuapiapp.R
import com.example.danmuapiapp.data.service.DanmuQuickSettingsTileService

/**
 * 设置页「控制中心快捷按钮」子控制器：请求系统添加磁贴并回报结果。
 */
internal class SettingsQuickSettingsTileController(
    private val postMessage: (String) -> Unit
) {
    fun requestAddQuickSettingsTile(activity: Activity?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            postMessage("当前系统版本不支持控制中心快捷按钮")
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            postMessage("请在控制中心编辑页手动添加“弹幕API服务”")
            return
        }
        if (activity == null) {
            postMessage("当前界面无法发起添加，请稍后重试")
            return
        }

        val manager = activity.getSystemService(StatusBarManager::class.java)
        if (manager == null) {
            postMessage("系统控制中心服务不可用，请手动添加")
            return
        }

        val component = ComponentName(activity, DanmuQuickSettingsTileService::class.java)
        val label = activity.getString(R.string.qs_tile_label)
        val icon = Icon.createWithResource(activity, R.drawable.ic_qs_danmu_service)
        manager.requestAddTileService(
            component,
            label,
            icon,
            activity.mainExecutor
        ) { result ->
            postMessage(
                when (result) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "已添加控制中心按钮"
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "控制中心按钮已存在"
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> "未添加控制中心按钮"
                    StatusBarManager.TILE_ADD_REQUEST_ERROR_REQUEST_IN_PROGRESS -> "系统正在处理上一次添加请求"
                    StatusBarManager.TILE_ADD_REQUEST_ERROR_APP_NOT_IN_FOREGROUND -> "请保持应用在前台后重试"
                    else -> "添加控制中心按钮失败：$result"
                }
            )
        }
    }
}
