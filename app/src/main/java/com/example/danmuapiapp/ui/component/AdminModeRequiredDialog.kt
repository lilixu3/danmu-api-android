package com.example.danmuapiapp.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

internal sealed interface AdminModeRequiredTarget {
    data class ConfigItem(val key: String) : AdminModeRequiredTarget
    data object RawConfig : AdminModeRequiredTarget
    data object ClearCache : AdminModeRequiredTarget
}

internal data class AdminModeRequiredPrompt(
    val title: String,
    val message: String,
    val confirmText: String
)

internal fun adminModeRequiredPrompt(
    target: AdminModeRequiredTarget,
    hasAdminTokenConfigured: Boolean
): AdminModeRequiredPrompt {
    val actionText = if (hasAdminTokenConfigured) "输入管理员密码" else "配置管理员密码"
    val message = when (target) {
        is AdminModeRequiredTarget.ConfigItem -> {
            "配置项 ${target.key} 属于管理员写操作，请先开启管理员模式后再编辑。"
        }
        AdminModeRequiredTarget.RawConfig -> {
            "源码配置属于管理员写操作，请先开启管理员模式后再保存。"
        }
        AdminModeRequiredTarget.ClearCache -> {
            if (hasAdminTokenConfigured) {
                "清理缓存属于管理员写操作，请先输入管理员密码开启管理员模式。"
            } else {
                "清理缓存属于管理员写操作，请先配置管理员密码并开启管理员模式。"
            }
        }
    }
    return AdminModeRequiredPrompt(
        title = "需要管理员模式",
        message = message,
        confirmText = actionText
    )
}

@Composable
internal fun AdminModeRequiredDialog(
    prompt: AdminModeRequiredPrompt,
    onOpenAdminMode: () -> Unit,
    onDismiss: () -> Unit
) {
    AppBottomSheetDialog(
        onDismissRequest = onDismiss,
        style = AppBottomSheetStyle.Confirm,
        tone = AppBottomSheetTone.Warning,
        icon = { Icon(Icons.Rounded.AdminPanelSettings, null) },
        title = { Text(prompt.title) },
        text = {
            Text(
                prompt.message,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onOpenAdminMode()
                }
            ) {
                Text(prompt.confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
