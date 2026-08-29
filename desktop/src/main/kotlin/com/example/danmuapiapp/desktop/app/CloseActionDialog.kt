package com.example.danmuapiapp.desktop.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.danmuapiapp.desktop.APP_NAME

/**
 * 关闭窗口时的行为选择对话框：后台运行（主选，右侧主位）/ 退出并关闭服务。
 * 可勾选"记住我的选择"，此后关闭窗口不再询问（可在 设置 → 关闭窗口行为 中更改）。
 */
@Composable
fun CloseActionDialog(
    onChoose: (action: String, rememberChoice: Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    var dontAsk by remember { mutableStateOf(false) }

    Dialog(onCloseRequest = onCancel, title = APP_NAME) {
        Card(shape = RoundedCornerShape(Dimens.CardCorner)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "关闭窗口时执行什么？",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "服务正在后台运行中；关闭窗口不会影响服务状态。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = dontAsk, onCheckedChange = { dontAsk = it })
                    Text(
                        text = "记住我的选择，不再提示",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedButton(onClick = onCancel) { Text("取消") }
                    Spacer(Modifier.weight(1f))
                    // 主选动作放右侧末位（用户高频选择），用主按钮强调
                    Button(onClick = { onChoose("tray", dontAsk) }) { Text("后台运行") }
                    OutlinedButton(onClick = { onChoose("exit", dontAsk) }) { Text("退出并关闭服务") }
                }
            }
        }
    }
}
