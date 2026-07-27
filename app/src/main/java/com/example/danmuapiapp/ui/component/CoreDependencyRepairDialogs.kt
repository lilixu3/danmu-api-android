package com.example.danmuapiapp.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.domain.model.CoreDependencyRepairRequest

@Composable
fun CoreDependencyRequiredDialog(
    request: CoreDependencyRepairRequest,
    onRepair: () -> Unit,
    onDismiss: () -> Unit
) {
    AppBottomSheetDialog(
        onDismissRequest = onDismiss,
        style = AppBottomSheetStyle.Confirm,
        tone = AppBottomSheetTone.Warning,
        icon = { Icon(Icons.Rounded.WarningAmber, contentDescription = null) },
        title = { Text("${request.actionLabel}需要补充依赖") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("候选核心尚未替换正式核心。修复并校验通过后，将自动继续${request.actionLabel}。")
                Text(
                    "缺失依赖",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    request.missingDependencies.joinToString("\n") { "• $it" },
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onRepair) {
                Icon(Icons.Rounded.Build, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("修复依赖")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("稍后") } }
    )
}

@Composable
fun CoreDependencyRepairDialog(
    request: CoreDependencyRepairRequest,
    onOnlineRepair: () -> Unit,
    onImportArchive: () -> Unit,
    onCancelMutation: () -> Unit,
    onDismiss: () -> Unit
) {
    AppBottomSheetDialog(
        onDismissRequest = onDismiss,
        style = AppBottomSheetStyle.Selection,
        tone = AppBottomSheetTone.Info,
        icon = { Icon(Icons.Rounded.Build, contentDescription = null) },
        title = { Text("修复运行时依赖") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "请选择依赖来源。仅修复当前候选核心，不会改动 App 公共 node_modules。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onOnlineRepair,
                    enabled = request.onlineRepairSupported,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.CloudDownload, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("在线修复")
                }
                if (!request.onlineRepairSupported) {
                    Text(
                        "自定义核心没有固定签名依赖通道，请导入本地依赖包。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = onImportArchive,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.FolderZip, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("导入本地 ZIP")
                }
                Text(
                    "ZIP 文件名可以任意，并可包含一层或两层外目录。" +
                        "本地文件由用户选择，App 会拒绝路径越界、原生模块、安装脚本和不完整依赖闭包。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = onCancelMutation,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("取消此次${request.actionLabel}")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("稍后") } }
    )
}
