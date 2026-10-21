package com.example.danmuapiapp.ui.screen.home

import com.example.danmuapiapp.ui.component.AppDialog
import com.example.danmuapiapp.ui.component.AppDialogStyle
import com.example.danmuapiapp.ui.component.AppDialogTone
import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.ui.component.SimpleMarkdownText
import com.example.danmuapiapp.ui.common.formatByteSize
import com.example.danmuapiapp.ui.component.liquid.AppGlassButton
import com.example.danmuapiapp.ui.theme.LocalGlassMaterial

@Composable
internal fun HomeAppUpdatePromptDialogHost(viewModel: HomeViewModel) {
    if (viewModel.showAppUpdatePromptDialog && !viewModel.appUpdatePromptLatestVersion.isNullOrBlank()) {
        AppDialog(
            onDismissRequest = viewModel::dismissForegroundAppUpdatePrompt,
            style = AppDialogStyle.Status,
            tone = AppDialogTone.Info,
            icon = { Icon(Icons.Rounded.SystemUpdate, null) },
            title = { Text("发现应用更新") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "当前版本：v${viewModel.appUpdatePromptCurrentVersion}\n最新版本：v${viewModel.appUpdatePromptLatestVersion}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    val preview = viewModel.appUpdatePromptReleaseNotes.trim()
                    if (preview.isNotBlank()) {
                        SimpleMarkdownText(
                            markdown = preview
                        )
                    }
                }
            },
            confirmButton = {
                if (LocalGlassMaterial.current.enabled) {
                    AppGlassButton(
                        onClick = viewModel::openForegroundAppUpdateMethodDialog,
                        tint = MaterialTheme.colorScheme.primary
                    ) {
                        Text("现在更新")
                    }
                } else {
                    TextButton(onClick = viewModel::openForegroundAppUpdateMethodDialog) {
                        Text("现在更新")
                    }
                }
            },
            dismissButton = {
                AppGlassButton(onClick = viewModel::dismissForegroundAppUpdatePrompt) {
                    Text("今日不提醒")
                }
            }
        )
    }
}

@Composable
internal fun HomeAppUpdateMethodDialogHost(
    viewModel: HomeViewModel,
    activity: Activity?,
    hasInAppDownload: Boolean,
) {
    if (viewModel.showAppUpdateMethodDialog) {
        AppDialog(
            onDismissRequest = viewModel::dismissForegroundAppUpdateMethodDialog,
            style = AppDialogStyle.Selection,
            tone = AppDialogTone.Info,
            title = { Text("选择更新方式") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (LocalGlassMaterial.current.enabled) {
                        AppGlassButton(
                            onClick = viewModel::startInAppUpdateDownload,
                            enabled = hasInAppDownload && !viewModel.isDownloadingAppUpdate && !viewModel.isRefreshingAppUpdatePrompt,
                            modifier = Modifier.fillMaxWidth(),
                            tint = MaterialTheme.colorScheme.primary
                        ) {
                            Icon(Icons.Rounded.Download, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("应用内下载")
                        }
                    } else {
                        FilledTonalButton(
                            onClick = viewModel::startInAppUpdateDownload,
                            enabled = hasInAppDownload && !viewModel.isDownloadingAppUpdate && !viewModel.isRefreshingAppUpdatePrompt,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Download, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("应用内下载")
                        }
                    }
                    if (LocalGlassMaterial.current.enabled) {
                        AppGlassButton(
                            onClick = {
                                val alive = activity
                                if (alive != null) {
                                    viewModel.openBrowserDownload(alive)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("浏览器下载")
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                val alive = activity
                                if (alive != null) {
                                    viewModel.openBrowserDownload(alive)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("浏览器下载")
                        }
                    }
                    if (!hasInAppDownload) {
                        Text(
                            if (viewModel.isRefreshingAppUpdatePrompt) "正在获取安装包信息…"
                            else "暂未获取到兼容的安装包，可重试检查或使用浏览器下载。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!viewModel.isRefreshingAppUpdatePrompt) {
                            AppGlassButton(onClick = viewModel::refreshForegroundAppUpdatePrompt, modifier = Modifier.fillMaxWidth()) {
                                Text("重试检查安装包")
                            }
                        }
                    }
                    Text(
                        "首次安装新版本可能需要“安装未知应用”权限，授权后返回 App 会自动继续安装。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                AppGlassButton(onClick = viewModel::dismissForegroundAppUpdateMethodDialog) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
internal fun HomeAppUpdateDownloadingDialogHost(viewModel: HomeViewModel) {
    if (viewModel.isDownloadingAppUpdate) {
        AppDialog(
            onDismissRequest = {},
            style = AppDialogStyle.Status,
            tone = AppDialogTone.Neutral,
            title = { Text("正在下载更新") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val progress = viewModel.appUpdateDownloadPercent
                    if (progress in 0..100) {
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "$progress% · ${viewModel.appUpdateDownloadDetail}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            viewModel.appUpdateDownloadDetail,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
        )
    }
}

@Composable
internal fun HomeAppUpdateInstallDialogHost(
    viewModel: HomeViewModel,
    activity: Activity?,
) {
    if (viewModel.showInstallAppUpdateDialog && viewModel.downloadedAppUpdate != null) {
        val apk = viewModel.downloadedAppUpdate!!
        AppDialog(
            onDismissRequest = viewModel::dismissInstallAppUpdateDialog,
            style = AppDialogStyle.Form,
            tone = AppDialogTone.Brand,
            title = { Text("下载完成") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("文件：${apk.displayName}")
                    Text("位置：${apk.displayPath}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "大小：${if (apk.sizeBytes > 0L) formatByteSize(apk.sizeBytes) else "未知"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    AppGlassButton(onClick = {
                        val alive = activity
                        if (alive != null) {
                            viewModel.openDownloadsApp(alive)
                        }
                    }) {
                        Icon(Icons.Rounded.Download, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("打开系统下载")
                    }
                }
            },
            confirmButton = {
                AppGlassButton(onClick = {
                    val alive = activity
                    if (alive != null) {
                        viewModel.installDownloadedAppUpdate(alive)
                    }
                }) {
                    Text("立即安装")
                }
            },
            dismissButton = {
                AppGlassButton(onClick = viewModel::dismissInstallAppUpdateDialog) { Text("稍后") }
            }
        )
    }
}

