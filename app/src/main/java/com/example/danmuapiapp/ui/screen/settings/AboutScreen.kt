package com.example.danmuapiapp.ui.screen.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.core.net.toUri
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.ClipEntry
import android.content.ClipData
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.BuildConfig
import com.example.danmuapiapp.NodeBridge
import com.example.danmuapiapp.domain.model.ServiceStatus
import com.example.danmuapiapp.domain.model.formatCoreVersionValue
import com.example.danmuapiapp.ui.component.AppSnackbarHost
import com.example.danmuapiapp.ui.component.AppGlassSurface
import com.example.danmuapiapp.ui.component.SimpleMarkdownText
import kotlinx.coroutines.launch

private const val ABOUT_PROJECT_URL = "https://github.com/lilixu3/danmu-api-android"

@Composable
fun AboutScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val activity = remember(context) { context.findAboutActivity() }
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val runtime by viewModel.runtimeState.collectAsStateWithLifecycle()
    val cores by viewModel.aboutCoreInfo.collectAsStateWithLifecycle()
    val displayNames by viewModel.aboutCoreDisplayNames.collectAsStateWithLifecycle()
    val core = cores.firstOrNull { it.variant == runtime.variant }
    var nodeVersion by remember { mutableStateOf("读取中…") }
    var updateMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val coreLabel = "${displayNames.resolve(runtime.variant)} · ${when {
        core == null -> "读取中…"
        !core.isInstalled -> "未安装"
        else -> formatCoreVersionValue(core.version)
    }}"
    val statusLabel = when (runtime.status) {
        ServiceStatus.Running -> "服务运行中"
        ServiceStatus.Starting -> "服务启动中"
        ServiceStatus.Stopping -> "服务停止中"
        ServiceStatus.Error -> "服务异常"
        ServiceStatus.Stopped -> "服务未运行"
    }
    val copyInfo: () -> Unit = {
        scope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("版本信息", listOf(
                "弹幕 API v${BuildConfig.VERSION_NAME}",
                "构建：${BuildConfig.VERSION_CODE} · ${BuildConfig.BUILD_TYPE}",
                "Node.js：$nodeVersion", "核心：$coreLabel",
                "核心提交：${core?.sourceCommitSha?.take(7)?.ifBlank { "未记录" } ?: "未记录"}",
                "服务：$statusLabel"
            ).joinToString("\n"))))
            snackbar.showSnackbar("版本信息已复制")
        }
    }
    LifecycleResumeEffect(Unit) {
        viewModel.refreshAboutInfo()
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) { nodeVersion = NodeBridge.nodeVersionOrNull() ?: "暂不可用" }
    LaunchedEffect(viewModel.operationMessage) {
        viewModel.operationMessage?.let { message ->
            if (message.contains("更新") || message.contains("下载") || message.contains("安装")) updateMessage = message
            viewModel.dismissMessage()
            snackbar.showSnackbar(message)
        }
    }

    Scaffold(containerColor = Color.Transparent, snackbarHost = { AppSnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回", Modifier.size(22.dp)) }
                Text("关于", Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = copyInfo) { Icon(Icons.Outlined.CopyAll, "复制版本信息", Modifier.size(21.dp)) }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).fillMaxWidth()
                .padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                AboutBrandIntro()
                Spacer(Modifier.height(26.dp))
                AboutUpdateCard(viewModel, updateMessage, onRelease = { context.openAboutUrl("$ABOUT_PROJECT_URL/releases") },
                    onInstall = { activity?.let(viewModel::installDownloadedAppUpdate) })
                Spacer(Modifier.height(14.dp))
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth < 300.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            AboutActionCard(Icons.Outlined.Code, "项目源码", "在 GitHub 一起完善", Modifier.fillMaxWidth()) { context.openAboutUrl(ABOUT_PROJECT_URL) }
                            AboutActionCard(Icons.Outlined.ChatBubbleOutline, "问题与建议", "让下一版更好用", Modifier.fillMaxWidth()) { context.openAboutUrl("$ABOUT_PROJECT_URL/issues") }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AboutActionCard(Icons.Outlined.Code, "项目源码", "在 GitHub 一起完善", Modifier.weight(1f)) { context.openAboutUrl(ABOUT_PROJECT_URL) }
                            AboutActionCard(Icons.Outlined.ChatBubbleOutline, "问题与建议", "让下一版更好用", Modifier.weight(1f)) { context.openAboutUrl("$ABOUT_PROJECT_URL/issues") }
                        }
                    }
                }
                Spacer(Modifier.height(28.dp))
                Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("了解弹幕 API", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("从第一步，到更多可能", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(12.dp))
                AboutExpandable(Icons.Outlined.Explore, "使用指南", "三步，让弹幕出现在屏幕上") {
                    AboutGuideStep("01", "准备好你的服务", "在首页或核心页安装稳定版、开发版核心，然后点击「启动服务」。在线下载较慢时，到「设置 → 下载与更新」切换 GitHub 线路。")
                    AboutGuideStep("02", "复制接口地址", "点击首页的局域网地址，复制完整接口地址。其他设备需要连接同一局域网。端口与 Token 可在「设置 → 服务配置」中调整。")
                    AboutGuideStep("03", "连接你喜欢的播放器", "在播放器中找到「自定义弹幕 API」，粘贴刚才的地址。播放视频时搜索弹幕，再选择对应剧集即可。")
                    AboutNote("还有更多玩法", "在「工具 → 本地弹幕」导入本地文件，用「弹幕下载」批量保存剧集弹幕。来源、过滤与合并规则可在「工具 → 配置管理」中调整。")
                    Spacer(Modifier.height(12.dp))
                    AboutNote("连接遇到问题？", "确认服务已启动、地址与 Token 正确，再查看「工具 → 运行诊断」和运行日志。反馈问题时可附上本页复制的版本信息。")
                    Spacer(Modifier.height(12.dp))
                    AboutNote("支持的播放器", "Forward · SenPlayer · Hills · 小幻 · Yamby · ePlayerX · AfuseKt · UZ影视 · DSCloud · Lenna · Danmaku-Anywhere · Omnibox · ChaiChaiEmbyTV · MoonTV · CapyPlayer · Kerkerker · LinPlayer，以及其他支持自定义弹幕 API 的客户端。")
                }
                Spacer(Modifier.height(12.dp))
                AboutExpandable(Icons.Outlined.Memory, "运行信息", "查看本机环境与核心版本") {
                    AboutDetail("服务状态", statusLabel)
                    AboutDetail("Node 运行时", nodeVersion)
                    AboutDetail("当前核心", coreLabel)
                    AboutDetail("核心提交", core?.sourceCommitSha?.take(7)?.ifBlank { "未记录" } ?: "未记录")
                    AboutDetail("应用构建", "${BuildConfig.VERSION_CODE} · ${BuildConfig.BUILD_TYPE}")
                    AboutDetail("工作目录", viewModel.workDirInfo.currentBaseDir.absolutePath)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = copyInfo, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Outlined.ContentCopy, null, Modifier.size(17.dp)); Spacer(Modifier.width(7.dp)); Text("复制版本信息", fontSize = 12.5.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                AboutExpandable(Icons.Outlined.FavoriteBorder, "开源与致谢", "感谢每一份让弹幕相遇的贡献") {
                    AboutNote("技术信息", "界面使用 Kotlin 与 Jetpack Compose 构建，弹幕核心通过 nodejs-mobile 在手机上运行。服务与界面分开运行，可选择普通或 Root 运行模式。")
                    Spacer(Modifier.height(16.dp))
                    AboutNote("增强直连组件", "来源增强直连使用 Node.js 原生 TLS/QUIC，GitHub 增强直连使用 OkHttp/DoH。相关源码与第三方许可见项目仓库。")
                    Spacer(Modifier.height(16.dp))
                    AboutLinkRow(Icons.Outlined.Code, "App 源码", "lilixu3/danmu-api-android") { context.openAboutUrl(ABOUT_PROJECT_URL) }
                    AboutLinkRow(Icons.Outlined.Verified, "弹幕核心 · 稳定版", "huangxd-/danmu_api") { context.openAboutUrl("https://github.com/huangxd-/danmu_api") }
                    AboutLinkRow(Icons.Outlined.Science, "弹幕核心 · 开发版", "lilixu3/danmu_api") { context.openAboutUrl("https://github.com/lilixu3/danmu_api") }
                    AboutLinkRow(Icons.Outlined.Hub, "nodejs-mobile", "手机上的 Node.js 运行时") { context.openAboutUrl("https://github.com/nodejs-mobile/nodejs-mobile") }
                    Spacer(Modifier.height(12.dp))
                    AboutNote("许可证", "App：MIT License\n弹幕 API 核心：AGPL-3.0")
                    Spacer(Modifier.height(12.dp))
                    Text("感谢上游项目作者、依赖库维护者，以及每一位提出建议的你。", fontSize = 12.sp, lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(28.dp))
                Icon(Icons.Outlined.FavoriteBorder, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text("因为热爱，所以相遇。", fontSize = 12.sp, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(5.dp))
                Text("DANMU API · MADE WITH KOTLIN", fontSize = 9.sp, letterSpacing = 1.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AboutBrandIntro() {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val appIcon = remember(context) { context.applicationInfo.loadIcon(context.packageManager).toBitmap(240, 240).asImageBitmap() }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(width = 270.dp, height = 154.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val unit = size.width / 270f
                val center = Offset(size.width / 2, size.height / 2)
                drawCircle(colors.primary.copy(alpha = 0.10f), 60 * unit, center, style = Stroke(unit))
                drawOval(colors.primary.copy(alpha = 0.10f), Offset(center.x - 121 * unit, center.y - 59 * unit),
                    Size(242 * unit, 118 * unit), style = Stroke(unit))
                listOf(floatArrayOf(20f, 36f, 52f), floatArrayOf(0f, 57f, 30f),
                    floatArrayOf(207f, 91f, 54f), floatArrayOf(187f, 112f, 32f)).forEach { rect ->
                    drawRoundRect(colors.primary.copy(alpha = 0.18f), Offset(rect[0] * unit, rect[1] * unit),
                        Size(rect[2] * unit, 7 * unit), CornerRadius(4 * unit))
                }
                drawCircle(colors.primary.copy(alpha = 0.5f), 4 * unit, center + Offset(84 * unit, -38 * unit))
                drawCircle(colors.primary.copy(alpha = 0.18f), 3 * unit, center + Offset(-80 * unit, 38 * unit))
            }
            Image(appIcon, "弹幕 API 图标", Modifier.size(80.dp).clip(RoundedCornerShape(24.dp)))
        }
        Text("弹幕 API", fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
        Spacer(Modifier.height(12.dp))
        Text("让每一帧，都有共鸣。", fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))
        Text("本机弹幕接口服务", fontSize = 12.5.sp, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("手机即服务", "连接播放器", "开源共建").forEach { label ->
                Surface(shape = RoundedCornerShape(8.dp), color = colors.primary.copy(alpha = 0.08f)) {
                    Text(label, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold, color = colors.primary)
                }
            }
        }
    }
}

@Composable
private fun AboutCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    AppGlassSurface(modifier = modifier, shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))) {
        Column(content = content)
    }
}

@Composable
private fun AboutUpdateCard(vm: SettingsViewModel, message: String?, onRelease: () -> Unit, onInstall: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val busy = vm.isCheckingAppUpdate || vm.isDownloadingAppUpdate
    val canDownload = vm.appUpdateHasUpdate && vm.appUpdateDownloadUrls.isNotEmpty()
    var notesExpanded by rememberSaveable { mutableStateOf(false) }
    AboutCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("版本与更新", fontSize = 11.5.sp, color = colors.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text("v${BuildConfig.VERSION_NAME}", fontSize = 24.sp, lineHeight = 29.sp, fontWeight = FontWeight.ExtraBold)
                }
                Button(onClick = {
                    when {
                        vm.downloadedAppUpdate != null -> onInstall()
                        canDownload -> vm.startInAppUpdateDownload()
                        else -> vm.checkAppUpdate()
                    }
                }, enabled = !busy, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.height(42.dp)) {
                    if (busy) CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp, color = colors.onPrimary)
                    else Icon(if (canDownload || vm.downloadedAppUpdate != null) Icons.Outlined.Download else Icons.Outlined.Refresh, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(when {
                        vm.isCheckingAppUpdate -> "正在检查…"
                        vm.isDownloadingAppUpdate -> "下载中…"
                        vm.downloadedAppUpdate != null -> "安装更新"
                        canDownload -> "下载更新"
                        else -> "检查更新"
                    }, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (vm.isDownloadingAppUpdate) {
                LinearProgressIndicator(progress = { vm.appUpdateDownloadPercent / 100f },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(4.dp)))
                Text(vm.appUpdateDownloadDetail.ifBlank { "正在下载 · ${vm.appUpdateDownloadPercent}%" }, fontSize = 12.sp, color = colors.onSurfaceVariant)
            } else if (message != null || vm.appUpdateLatestVersion != null) {
                Text(message ?: if (vm.appUpdateHasUpdate) "新版本 v${vm.appUpdateLatestVersion} 已就绪" else "已检查，当前版本无需更新。",
                    fontSize = 12.sp, lineHeight = 18.sp, color = colors.onSurfaceVariant)
            }
            if (vm.appUpdateHasUpdate && vm.appUpdateReleaseNotes.isNotBlank()) {
                TextButton(onClick = { notesExpanded = !notesExpanded }, contentPadding = PaddingValues(0.dp), modifier = Modifier.height(30.dp)) {
                    Text("新版本 v${vm.appUpdateLatestVersion} · 更新说明", fontSize = 12.sp)
                    Icon(if (notesExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.size(17.dp))
                }
                AnimatedVisibility(notesExpanded) { SimpleMarkdownText(vm.appUpdateReleaseNotes, maxLinesPerParagraph = 6, overflow = TextOverflow.Ellipsis) }
            }
            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.45f))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("构建 ${BuildConfig.VERSION_CODE} · ${BuildConfig.BUILD_TYPE}", Modifier.weight(1f), fontSize = 11.5.sp, color = colors.onSurfaceVariant)
                TextButton(onClick = onRelease, modifier = Modifier.height(30.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text("更新日志", fontSize = 12.sp); Spacer(Modifier.width(4.dp)); Icon(Icons.Outlined.NorthEast, null, Modifier.size(14.dp))
                }
                if (vm.appUpdateHasUpdate) {
                    IconButton(onClick = vm::checkAppUpdate, enabled = !busy, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Outlined.Refresh, "重新检查更新", Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutActionCard(icon: ImageVector, title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    AboutCard(modifier.clip(RoundedCornerShape(22.dp)).clickable(role = Role.Button, onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(23.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.weight(1f)); Icon(Icons.Outlined.NorthEast, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(subtitle, fontSize = 11.5.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AboutExpandable(icon: ImageVector, title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    AboutCard(Modifier.fillMaxWidth().animateContentSize()) {
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { expanded = !expanded }.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(38.dp).background(colors.primary.copy(alpha = 0.08f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(20.dp), tint = colors.primary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, fontSize = 11.5.sp, color = colors.onSurfaceVariant)
            }
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.size(19.dp), tint = colors.onSurfaceVariant)
        }
        AnimatedVisibility(expanded) { Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp), content = content) }
    }
}

@Composable
private fun AboutGuideStep(number: String, title: String, body: String) {
    Row(Modifier.padding(top = 10.dp, bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(number, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        Column {
            Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp))
            Text(body, fontSize = 12.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AboutNote(title: String, body: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            Text(body, fontSize = 12.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AboutDetail(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer { Text(value, fontSize = 13.sp, lineHeight = 20.sp) }
    }
}

@Composable
private fun AboutLinkRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick).padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Outlined.NorthEast, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun Context.openAboutUrl(url: String) {
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private tailrec fun Context.findAboutActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findAboutActivity()
    else -> null
}
