package com.example.danmuapiapp.ui.compat

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.example.danmuapiapp.data.util.AppAppearancePrefs
import com.example.danmuapiapp.data.util.DeviceCompatMode
import com.example.danmuapiapp.data.util.InterfaceMode
import com.example.danmuapiapp.domain.model.resolveCustomCoreSource

@Composable
internal fun CompatCustomSourceDialog(state: CompatModeUiState, actions: CompatModeActions, onDismiss: () -> Unit) {
    var repo by rememberSaveable { mutableStateOf(state.customRepo) }
    var branch by rememberSaveable { mutableStateOf(state.customRepoBranch) }
    val valid = repo.isBlank() || resolveCustomCoreSource(repo, branch).isValidRepo
    CompatDialog("自定义核心来源", onDismiss, "保存", {
        actions.onSaveCustomCore(repo.trim(), branch.trim()); onDismiss()
    }, confirmEnabled = valid && !state.isOperating) {
        Text("填写 GitHub 仓库和分支。留空仓库可清除自定义来源。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(repo, { repo = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("仓库") }, placeholder = { Text("用户名 / 仓库名") }, isError = !valid)
        OutlinedTextField(branch, { branch = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("分支（可选）") }, placeholder = { Text("留空使用默认分支") })
        if (!valid) Text("请填写有效的 GitHub 仓库地址或 用户名/仓库名。", color = MaterialTheme.colorScheme.error)
    }
}

@Composable
internal fun CompatScaleDialog(current: Int, onApply: (Int) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val system = AppAppearancePrefs.systemDensityDpi(context)
    var selected by rememberSaveable { mutableIntStateOf(current) }
    var input by rememberSaveable { mutableStateOf(if (current > 0) current.toString() else system.toString()) }
    val parsed = input.toIntOrNull()
    val valid = selected == AppAppearancePrefs.APP_DPI_SYSTEM || parsed in AppAppearancePrefs.APP_DPI_MIN..AppAppearancePrefs.APP_DPI_MAX
    CompatDialog("兼容界面缩放", onDismiss, "应用缩放", {
        val target = if (selected == AppAppearancePrefs.APP_DPI_SYSTEM) selected else parsed ?: return@CompatDialog
        onDismiss(); onApply(target)
    }, confirmEnabled = valid) {
        Text("只影响兼容模式，普通界面的 DPI 不会改变。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("系统 DPI：$system。数值越大，界面文字和控件越大。", style = MaterialTheme.typography.bodySmall)
        CompatActions {
            CompatButton("跟随系统", { selected = AppAppearancePrefs.APP_DPI_SYSTEM; input = system.toString() },
                tone = if (selected <= 0) CompatActionTone.Primary else CompatActionTone.Neutral)
            listOf(0.85f to "紧凑", 1f to "标准", 1.15f to "放大").forEach { (scale, label) ->
                val value = AppAppearancePrefs.normalizeAppDpiOverride((system * scale).toInt())
                CompatButton(label, { selected = value; input = value.toString() },
                    tone = if (selected == value) CompatActionTone.Primary else CompatActionTone.Neutral)
            }
        }
        OutlinedTextField(input, {
            input = it.filter(Char::isDigit).take(4)
            selected = input.toIntOrNull() ?: 0
        }, Modifier.fillMaxWidth(), label = { Text("自定义 DPI") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = !valid)
        if (!valid) Text("请输入 ${AppAppearancePrefs.APP_DPI_MIN}–${AppAppearancePrefs.APP_DPI_MAX} 之间的数值。",
            color = MaterialTheme.colorScheme.error)
    }
}

@Composable
internal fun CompatInterfaceDialog(onApply: (InterfaceMode) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var selectedKey by rememberSaveable { mutableStateOf(DeviceCompatMode.getInterfaceMode(context).key) }
    CompatDialog("兼容模式", onDismiss, "应用", {
        val mode = InterfaceMode.entries.first { it.key == selectedKey }
        onDismiss(); onApply(mode)
    }) {
        Text("仅切换界面，服务与工作目录保持原样。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        InterfaceMode.entries.forEach { mode ->
            CompatButton(mode.label, { selectedKey = mode.key }, Modifier.fillMaxWidth(),
                icon = if (selectedKey == mode.key) Icons.Rounded.Check else null,
                tone = if (selectedKey == mode.key) CompatActionTone.Primary else CompatActionTone.Neutral)
        }
    }
}
