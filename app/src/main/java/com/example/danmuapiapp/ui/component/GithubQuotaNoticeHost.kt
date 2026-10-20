package com.example.danmuapiapp.ui.component

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.data.service.GithubApiQuota
import kotlinx.coroutines.delay

@Composable
fun GithubQuotaNoticeHost(quota: GithubApiQuota, onConfigureToken: () -> Unit) {
    val pending by quota.notice.collectAsStateWithLifecycle()
    val notice = pending ?: return
    var nowSeconds by remember(notice) { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(notice) {
        while (nowSeconds < notice.resetEpochSeconds) {
            delay(1000)
            nowSeconds = System.currentTimeMillis() / 1000
        }
        quota.dismissNotice()
    }
    AppDialog(
        onDismissRequest = quota::dismissNotice,
        title = { Text("GitHub 匿名额度不足") },
        text = { Text(notice.message(nowSeconds)) },
        confirmButton = {
            TextButton(onClick = {
                quota.dismissNotice()
                onConfigureToken()
            }) { Text("配置 GitHub Token") }
        },
        dismissButton = { TextButton(onClick = quota::dismissNotice) { Text("稍后重试") } }
    )
}
