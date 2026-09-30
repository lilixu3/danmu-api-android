package com.example.danmuapiapp.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.ui.common.CoreUpdateChoiceController
import com.example.danmuapiapp.ui.component.liquid.AppGlassButton

@Composable
fun CorePullRequestUpdateChoiceHost(controller: CoreUpdateChoiceController) {
    DisposableEffect(controller) { onDispose { controller.dismiss() } }
    val plan = controller.pendingPlan ?: return
    AppDialog(
        onDismissRequest = controller::dismiss,
        title = { Text("更新后如何处理 PR？") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (plan.notIncludedPullRequestNumbers.isNotEmpty()) {
                    Text("目标版本未找到以下 PR 提交的完整合入记录：" +
                        plan.notIncludedPullRequestNumbers.joinToString("、") { "#$it" })
                }
                if (plan.unknownPullRequestNumbers.isNotEmpty()) {
                    Text("无法确认目标版本完整保留以下 PR 改动：" +
                        plan.unknownPullRequestNumbers.joinToString("、") { "#$it" })
                }
                plan.pullRequestNotes.forEach { (number, reason) ->
                    Text("#$number：$reason", style = MaterialTheme.typography.bodySmall)
                }
                Text("直接更新会使用远端最新版，不保留额外的本地 PR 改动。继续合并会在新版本上合并检查时的 PR；若发生冲突、历史改写或无法确认安全保留，保留当前核心并说明原因。",
                    style = MaterialTheme.typography.bodyMedium)
                AppGlassButton(onClick = { controller.choose(false) }, modifier = Modifier.fillMaxWidth()) {
                    Text("更新到最新版本")
                }
                AppGlassButton(onClick = { controller.choose(true) }, modifier = Modifier.fillMaxWidth()) {
                    Text("更新后继续合并 PR")
                }
            }
        },
        confirmButton = { AppGlassButton(onClick = controller::dismiss) { Text("取消") } }
    )
}
