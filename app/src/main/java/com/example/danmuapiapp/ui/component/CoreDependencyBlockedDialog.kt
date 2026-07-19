package com.example.danmuapiapp.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.ui.common.CoreDependencyBlockedPrompt

@Composable
internal fun CoreDependencyBlockedDialog(
    prompt: CoreDependencyBlockedPrompt,
    onDismiss: () -> Unit
) {
    AppBottomSheetDialog(
        onDismissRequest = onDismiss,
        style = AppBottomSheetStyle.Confirm,
        tone = AppBottomSheetTone.Warning,
        icon = { Icon(Icons.Rounded.WarningAmber, contentDescription = null) },
        title = { Text(prompt.title) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "检测到候选核心需要额外运行时依赖，但当前 App 无法安全提供匹配依赖。" +
                        "为避免更新后服务无法启动，本次操作已在替换正式核心前取消，" +
                        "正式核心未被替换，原有版本（如有）保持不变。",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (prompt.missingDependencies.isNotEmpty()) {
                    Text(
                        "缺失依赖",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        prompt.missingDependencies.joinToString("\n") { "• $it" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                prompt.unavailableReason?.let { reason ->
                    Text(
                        "原因：$reason",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    prompt.guidance,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("我知道了")
            }
        }
    )
}
