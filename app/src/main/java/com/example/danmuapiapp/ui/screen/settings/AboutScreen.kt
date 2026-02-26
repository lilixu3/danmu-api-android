package com.example.danmuapiapp.ui.screen.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.danmuapiapp.BuildConfig
import com.example.danmuapiapp.ui.component.*

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findAboutActivity() }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.operationMessage) {
        viewModel.operationMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SettingsPageHeader(
                title = "关于",
                subtitle = "版本、更新、项目链接与使用指南",
                onBack = onBack
            )

            // ── 版本与更新 ──
            SettingsGroup(title = "版本与更新") {
                SettingsValueItem(
                    title = "当前版本",
                    value = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    icon = Icons.Rounded.Info
                )
                SettingsDivider()
                SettingsValueItem(
                    title = "构建类型",
                    value = BuildConfig.BUILD_TYPE,
                    icon = Icons.Rounded.Build
                )
                SettingsDivider()
                SettingsItem(
                    title = "检查更新",
                    subtitle = when {
                        viewModel.isCheckingAppUpdate -> "正在检查..."
                        viewModel.appUpdateHasUpdate -> "发现新版本 v${viewModel.appUpdateLatestVersion}"
                        viewModel.appUpdateLatestVersion != null -> "已是最新版本"
                        else -> "点击检查是否有新版本可用"
                    },
                    icon = Icons.Rounded.Update,
                    onClick = { viewModel.checkAppUpdate() },
                    trailing = if (viewModel.isCheckingAppUpdate) {
                        {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    } else null
                )
            }

            // 有更新时显示更新操作卡片
            AnimatedVisibility(
                visible = viewModel.appUpdateHasUpdate,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                SettingsGroup {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "新版本 v${viewModel.appUpdateLatestVersion ?: ""}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (viewModel.appUpdateReleaseNotes.isNotBlank() &&
                            viewModel.appUpdateReleaseNotes != "点击下方按钮检查更新"
                        ) {
                            Text(
                                viewModel.appUpdateReleaseNotes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 6,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                        ) {
                            OutlinedButton(
                                onClick = { activity?.let { viewModel.openAppUpdateReleasePage(it) } }
                            ) {
                                Text("查看详情")
                            }
                            if (viewModel.isDownloadingAppUpdate) {
                                Button(onClick = {}, enabled = false) {
                                    Text("下载中 ${viewModel.appUpdateDownloadPercent}%")
                                }
                            } else if (viewModel.downloadedAppUpdate != null) {
                                Button(
                                    onClick = { activity?.let { viewModel.installDownloadedAppUpdate(it) } }
                                ) {
                                    Text("安装更新")
                                }
                            } else {
                                Button(onClick = { viewModel.startInAppUpdateDownload() }) {
                                    Text("下载更新")
                                }
                            }
                        }
                    }
                }
            }

            // ── 项目地址 ──
            SettingsGroup(title = "项目地址") {
                SettingsItem(
                    title = "App 仓库",
                    subtitle = "lilixu3/danmu-api-android",
                    icon = Icons.Rounded.PhoneAndroid,
                    onClick = {
                        context.openUrl("https://github.com/lilixu3/danmu-api-android")
                    }
                )
                SettingsDivider()
                SettingsItem(
                    title = "核心 · 稳定版",
                    subtitle = "huangxd-/danmu_api（上游原版）",
                    icon = Icons.Rounded.Verified,
                    onClick = {
                        context.openUrl("https://github.com/huangxd-/danmu_api")
                    }
                )
                SettingsDivider()
                SettingsItem(
                    title = "核心 · 开发版",
                    subtitle = "lilixu3/danmu_api（探索版）",
                    icon = Icons.Rounded.Science,
                    onClick = {
                        context.openUrl("https://github.com/lilixu3/danmu_api")
                    }
                )
            }

            // ── 使用指南 ──
            SettingsGroup(title = "使用指南") {
                GuideSection(
                    title = "启动服务并复制地址",
                    icon = Icons.Rounded.RocketLaunch,
                    content = "1. 首次打开 App，在首页点击「安装核心」，等待下载完成\n" +
                        "   （下载慢可到「设置 → GitHub 代理」切换线路）\n\n" +
                        "2. 安装完成后点击「启动」，等待状态变为「运行中」\n\n" +
                        "3. 首页会显示局域网地址，例如：\n" +
                        "   http://192.168.1.7:9321/87654321\n" +
                        "   其中 87654321 是默认 Token（默认值可省略不带）\n" +
                        "   如果自定义了 TOKEN，请替换为对应值\n\n" +
                        "4. 点击地址旁的复制按钮，或在 Web UI 界面点击 API 端点直接复制"
                )
                SettingsDivider()
                GuideSection(
                    title = "填入播放器使用",
                    icon = Icons.Rounded.Tv,
                    content = "以 SenPlayer 为例：\n\n" +
                        "1. 打开 SenPlayer → 设置 → 弹幕设置 → 自定义弹幕 API\n" +
                        "2. 粘贴刚才复制的 API 地址\n" +
                        "3. 播放视频时点击弹幕按钮 → 搜索弹幕\n" +
                        "4. 选择你的弹幕 API，会根据标题自动搜索\n" +
                        "5. 等待片刻，选择对应剧集即可加载弹幕\n\n" +
                        "其他播放器操作类似，在弹幕相关设置中填入 API 地址即可。"
                )
                SettingsDivider()
                GuideSection(
                    title = "支持的播放器",
                    icon = Icons.Rounded.Devices,
                    content = "支持以下播放器及所有兼容弹幕 API 的客户端：\n\n" +
                        "Forward · SenPlayer · Hills · 小幻\n" +
                        "Yamby · ePlayerX · AfuseKt · UZ影视\n" +
                        "DSCloud · Lenna · Danmaku-Anywhere\n" +
                        "Omnibox · ChaiChaiEmbyTV · MoonTV\n" +
                        "CapyPlayer · Kerkerker · LinPlayer\n\n" +
                        "只要播放器支持自定义弹幕 API 地址，即可使用。"
                )
            }

            // ── 致谢 ──
            SettingsGroup(title = "致谢") {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "上游核心项目",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "huangxd-/danmu_api",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            context.openUrl("https://github.com/huangxd-/danmu_api")
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "nodejs-mobile · 在 Android 上运行 Node.js",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "以及所有贡献者和依赖项目作者",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── 许可证 ──
            SettingsGroup(title = "许可证") {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        "App：MIT License",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "弹幕 API 核心：AGPL-3.0",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SettingsHintCard(
                text = "如遇问题请在 GitHub 提交 Issue，附上版本号（v${BuildConfig.VERSION_NAME}）与日志信息以便排查。"
            )
        }
    }
}

/** 可展开/收起的使用指南条目 */
@Composable
private fun GuideSection(
    title: String,
    icon: ImageVector,
    content: String
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { expanded = !expanded }
            .animateContentSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                tonalElevation = 0.dp
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
            )
            Icon(
                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Text(
                content,
                modifier = Modifier
                    .padding(top = 10.dp, start = 52.dp)
                    .fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.3f
            )
        }
    }
}

private fun Context.openUrl(url: String) {
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}

private tailrec fun Context.findAboutActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findAboutActivity()
        else -> null
    }
}
