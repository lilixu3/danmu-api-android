package com.example.danmuapiapp.ui.screen.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.CompatEntryActivity
import com.example.danmuapiapp.data.util.DeviceCompatMode
import com.example.danmuapiapp.data.util.InterfaceMode
import com.example.danmuapiapp.ui.component.AppDialog
import com.example.danmuapiapp.ui.component.AppDialogStyle
import com.example.danmuapiapp.ui.component.AppDialogTone
import com.example.danmuapiapp.ui.component.SettingsGroup
import com.example.danmuapiapp.ui.component.SettingsItem
import com.example.danmuapiapp.ui.component.SettingsPageHeader
import com.example.danmuapiapp.ui.component.liquid.AppGlassButton

/** The interface preference never changes runtime mode, work directory or service state. */
fun applyInterfaceMode(context: Context, mode: InterfaceMode) {
    val wasCompat = DeviceCompatMode.shouldUseCompatMode(context)
    DeviceCompatMode.setInterfaceMode(context, mode)
    if (wasCompat == DeviceCompatMode.shouldUseCompatMode(context)) return
    context.startActivity(Intent(context, CompatEntryActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    })
}

@Composable
fun CompatModeSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(DeviceCompatMode.getInterfaceMode(context)) }
    var pending by remember { mutableStateOf<InterfaceMode?>(null) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsPageHeader(title = "兼容模式", subtitle = "选择适合手机或遥控器的界面", onBack = onBack)
        Text("此项仅切换界面。普通 / Root 运行方式、工作目录和正在运行的服务保持原样。")
        SettingsGroup(title = "界面选择") {
            CompatModeOptions(selected = selected, onSelect = { if (it != selected) pending = it })
        }
        Text("自动选择会在电视、盒子和无触摸屏设备上使用兼容界面。普通界面也支持方向键与确认键操作。")
    }
    pending?.let { mode ->
        AppDialog(
            onDismissRequest = { pending = null },
            style = AppDialogStyle.Confirm,
            tone = AppDialogTone.Info,
            title = { Text("切换为${mode.label}？") },
            text = { Text("界面会按新设置显示，服务继续运行。可随时在“兼容模式”设置中切换回来。") },
            dismissButton = { AppGlassButton(onClick = { pending = null }) { Text("取消") } },
            confirmButton = {
                AppGlassButton(onClick = {
                    pending = null
                    selected = mode
                    applyInterfaceMode(context, mode)
                }) { Text("切换") }
            }
        )
    }
}

@Composable
internal fun CompatModeOptions(selected: InterfaceMode, onSelect: (InterfaceMode) -> Unit) {
    InterfaceMode.entries.forEach { mode ->
        SettingsItem(
            title = mode.label,
            subtitle = when (mode) {
                InterfaceMode.Auto -> "根据设备自动选择，电视 / 盒子优先兼容界面"
                InterfaceMode.Normal -> "完整功能界面，支持触摸及遥控器操作"
                InterfaceMode.Compat -> "大字号与简化布局，适合电视 / 盒子和旧设备"
            },
            icon = Icons.Rounded.Tv,
            onClick = { onSelect(mode) },
            trailing = { if (mode == selected) Icon(Icons.Rounded.Check, contentDescription = "当前选择") }
        )
    }
}

@Composable
internal fun CompatModeSettingsDialog(onDismiss: () -> Unit, onSelect: (InterfaceMode) -> Unit) {
    val context = LocalContext.current
    AppDialog(
        onDismissRequest = onDismiss,
        style = AppDialogStyle.Selection,
        tone = AppDialogTone.Info,
        title = { Text("兼容模式") },
        text = {
            Text("仅切换界面，正在运行的服务保持原样。")
            CompatModeOptions(selected = DeviceCompatMode.getInterfaceMode(context), onSelect = onSelect)
        },
        confirmButton = { AppGlassButton(onClick = onDismiss) { Text("返回") } }
    )
}
