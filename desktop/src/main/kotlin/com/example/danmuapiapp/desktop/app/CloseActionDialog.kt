package com.example.danmuapiapp.desktop.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.desktop.APP_NAME

/** Close policy dialog shared by the window and the desktop settings vocabulary. */
@Composable
fun CloseActionDialog(
    onChoose: (action: String, rememberChoice: Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    var rememberChoice by remember { mutableStateOf(false) }
    DesktopDialogFrame(
        spec = DesktopDialogSpec(
            title = "关闭 $APP_NAME",
            description = "服务正在运行。选择关闭窗口后的行为。",
            tone = DesktopDialogTone.Warning,
            dismissOnClickOutside = false,
            dismissOnEscape = true,
        ),
        onDismissRequest = onCancel,
        leadingIcon = DesktopIcons.Warning,
        content = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = rememberChoice, onCheckedChange = { rememberChoice = it })
                Text("记住我的选择，不再提示")
            }
        },
        actions = {
            DesktopDialogButton(
                action = DesktopDialogAction("取消"),
                onClick = onCancel,
            )
            Spacer(Modifier.weight(1f))
            DesktopDialogButton(
                action = DesktopDialogAction("退出并关闭服务", tone = DesktopDialogTone.Danger),
                onClick = { onChoose("exit", rememberChoice) },
            )
            DesktopDialogButton(
                action = DesktopDialogAction("后台运行", isPrimary = true),
                onClick = { onChoose("tray", rememberChoice) },
            )
        },
    )
}
