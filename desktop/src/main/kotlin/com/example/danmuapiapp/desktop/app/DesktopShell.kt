package com.example.danmuapiapp.desktop.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.danmuapiapp.desktop.runtime.DesktopRuntimeController
import com.example.danmuapiapp.desktop.runtime.ServicePhase
import com.example.danmuapiapp.desktop.runtime.ServiceUiState
import com.example.danmuapiapp.desktop.core.CoreUpdateCheck
import com.example.danmuapiapp.desktop.core.CoreUpdateCoordinator
import com.example.danmuapiapp.desktop.core.DesktopCoreVariant
import com.example.danmuapiapp.desktop.node.DesktopCoreInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Compatibility dimensions used by the remaining desktop pages. */
object Dimens {
    val SidebarWidth = DesktopTokens.SidebarWidth
    val StatusStripHeight = DesktopTokens.StatusStripHeight
    val PagePadding = DesktopTokens.PagePadding
    val CardCorner = DesktopTokens.CardCornerRadius
    val ItemCorner = DesktopTokens.ItemCornerRadius
}

/** Pages retained for source compatibility; only [DesktopPageRegistry.visible] is navigable. */
enum class DesktopPage(val label: String) {
    Overview("概览"),
    Settings("设置"),
    About("关于"),

    Core("核心"),
    Logs("日志"),

    Configuration("配置"),

    @Deprecated("Placeholder page kept for compatibility only")
    Downloads("下载"),

    @Deprecated("Placeholder page kept for compatibility only")
    Activity("活动"),

    @Deprecated("Placeholder page kept for compatibility only")
    Tools("工具"),
}

data class DesktopPageSpec(
    val page: DesktopPage,
    val title: String,
    val description: String,
    val icon: DesktopIconGlyph,
)

/** Single source of truth for pages exposed by this desktop build. */
object DesktopPageRegistry {
    val visible: List<DesktopPageSpec> = listOf(
        DesktopPageSpec(DesktopPage.Overview, "概览", "运行状态与连接信息", DesktopIcons.Overview),
        DesktopPageSpec(DesktopPage.Core, "核心", "下载、更新与版本变更", DesktopIcons.Core),
        DesktopPageSpec(DesktopPage.Configuration, "配置", "配置核心环境变量", DesktopIcons.Tools),
        DesktopPageSpec(DesktopPage.Logs, "日志", "查看、筛选、复制和导出运行日志", DesktopIcons.Activity),
        DesktopPageSpec(DesktopPage.Settings, "设置", "运行目录、网络与外观", DesktopIcons.Settings),
        DesktopPageSpec(DesktopPage.About, "关于", "版本与项目说明", DesktopIcons.About),
    )
}

/** Theme preference persisted by DesktopSettings. */
enum class ThemePreference(val key: String, val label: String) {
    System("system", "跟随系统"),
    Light("light", "浅色"),
    Dark("dark", "深色");

    companion object {
        fun fromKey(raw: String?): ThemePreference = entries.firstOrNull { it.key == raw } ?: System
    }
}

    /** Desktop page palette bridge retained for existing pages. */

@Suppress("UNUSED_PARAMETER")
@Composable
fun statusColor(phase: ServicePhase, isDark: Boolean): Color =
    LocalDesktopThemePalette.current.colorsFor(phase.toDesktopStatus()).content

fun phaseLabel(phase: ServicePhase): String = when (phase) {
    ServicePhase.Stopped -> "已停止"
    ServicePhase.Preparing -> "准备中"
    ServicePhase.Starting -> "启动中"
    ServicePhase.Running -> "运行中"
    ServicePhase.Stopping -> "停止中"
    ServicePhase.CoreSetupRequired -> "待准备核心"
    ServicePhase.Failed -> "失败"
}

@Composable
fun DesktopShell(
    controller: DesktopRuntimeController,
    themePreference: ThemePreference,
    onThemeChange: (ThemePreference) -> Unit,
    initialPage: DesktopPage = DesktopPage.Overview,
    foregroundTick: Int = 0,
) {
    val state by controller.state.collectAsState()
    val darkTheme = when (themePreference) {
        ThemePreference.Light -> false
        ThemePreference.Dark -> true
        ThemePreference.System -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    var page by remember { mutableStateOf(initialPage) }
    var sidebarCollapsed by remember { mutableStateOf(false) }
    var overviewRefreshNonce by remember { mutableStateOf(0) }
    var automaticCoreUpdate by remember { mutableStateOf<CoreUpdateCheck?>(null) }
    var automaticCoreUpdateError by remember { mutableStateOf<String?>(null) }
    var queuedCoreUpdate by remember { mutableStateOf<CoreUpdateCheck?>(null) }
    val updateScope = rememberCoroutineScope()
    val updateCoordinator = remember(controller) {
        CoreUpdateCoordinator(controller, File(controller.paths.runtimeDir, "nodejs-project"))
    }

    LaunchedEffect(foregroundTick) {
        if (foregroundTick <= 0 || automaticCoreUpdate != null || !controller.settings.githubProxyConfirmed) return@LaunchedEffect
        val now = System.currentTimeMillis()
        updateScope.launch {
            updateCoordinator.installedVariants()
                .filter { updateCoordinator.shouldCheck(it, now) }
                .forEach { variant ->
                    if (automaticCoreUpdate != null) return@forEach
                    val result = withContext(Dispatchers.IO) {
                        runCatching { updateCoordinator.check(variant) }
                    }
                    result.onSuccess { update ->
                        if (update != null) automaticCoreUpdate = update
                    }.onFailure { error ->
                        automaticCoreUpdateError = "${variant.label} 自动检查失败：${shellDiagnostic(error)}"
                    }
                }
        }
    }

    LaunchedEffect(initialPage) {
        page = initialPage
    }

    DesktopTheme(darkTheme = darkTheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val narrowWindow = maxWidth < DesktopTokens.SidebarCollapseBreakpoint
                if (narrowWindow && !sidebarCollapsed) sidebarCollapsed = true
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        val sidebarCoreVersionText = remember(controller, state.phase, state.message) {
                            sidebarCoreVersion(controller)
                        }
                        NavSidebar(
                            selected = page,
                            collapsed = sidebarCollapsed,
                            coreVersionText = sidebarCoreVersionText,
                            onToggleCollapsed = { sidebarCollapsed = !sidebarCollapsed },
                            onSelect = { page = it },
                            modifier = Modifier
                                .width(if (sidebarCollapsed) DesktopTokens.SidebarCollapsedWidth else DesktopTokens.SidebarWidth)
                                .fillMaxHeight(),
                        )
                        MainContent(
                            page = page,
                            controller = controller,
                            state = state,
                            themePreference = themePreference,
                            onThemeChange = onThemeChange,
                            darkTheme = darkTheme,
                            overviewRefreshKey = overviewRefreshNonce,
                            onOverviewRefresh = { overviewRefreshNonce++ },
                            foregroundTick = foregroundTick,
                            automaticCoreUpdate = automaticCoreUpdate,
                            onAutomaticCoreUpdateConsumed = { automaticCoreUpdate = null },
                            queuedCoreUpdate = queuedCoreUpdate,
                            onQueuedCoreUpdateConsumed = { queuedCoreUpdate = null },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                    StatusStrip(state, compact = narrowWindow)
                }
            }
            automaticCoreUpdateError?.let { message ->
                DesktopDialogFrame(
                    spec = DesktopDialogSpec(
                        title = "核心更新检查失败",
                        description = "自动检查未修改本地核心文件。",
                        tone = DesktopDialogTone.Warning,
                        dismissOnClickOutside = true,
                    ),
                    onDismissRequest = { automaticCoreUpdateError = null },
                    leadingIcon = DesktopIcons.Warning,
                    content = { Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    actions = {
                        Spacer(Modifier.weight(1f))
                        DesktopDialogButton(
                            action = DesktopDialogAction("关闭", isPrimary = true),
                            onClick = { automaticCoreUpdateError = null },
                        )
                    },
                )
            }
            automaticCoreUpdate?.let { update ->
                DesktopDialogFrame(
                    spec = DesktopDialogSpec(
                        title = "发现核心更新",
                        description = update.variant.label,
                        tone = DesktopDialogTone.Info,
                        dismissOnClickOutside = false,
                    ),
                    onDismissRequest = { automaticCoreUpdate = null },
                    leadingIcon = DesktopIcons.Restart,
                    content = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            DesktopInfoRow("当前提交", update.localSha.take(12), monospace = true)
                            DesktopDivider()
                            DesktopInfoRow("最新提交", update.remoteSha.take(12), monospace = true)
                            DesktopDivider()
                            Text(update.remote.title, fontWeight = FontWeight.SemiBold)
                            Text(update.remote.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("只在你确认后下载和替换核心，不会自动更新。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    actions = {
                        DesktopDialogButton(
                            action = DesktopDialogAction("稍后"),
                            onClick = { automaticCoreUpdate = null },
                        )
                        DesktopDialogButton(
                            action = DesktopDialogAction("进入核心更新", isPrimary = true),
                            onClick = {
                                queuedCoreUpdate = update
                                automaticCoreUpdate = null
                                page = DesktopPage.Core
                            },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun NavSidebar(
    selected: DesktopPage,
    collapsed: Boolean,
    coreVersionText: String,
    onToggleCollapsed: () -> Unit,
    onSelect: (DesktopPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        if (collapsed) {
            Column(
                modifier = Modifier.fillMaxSize().padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                RailControlButton(
                    icon = DesktopIcons.Expand,
                    onClick = onToggleCollapsed,
                    contentDescription = "展开侧栏",
                )
                Spacer(Modifier.height(14.dp))
                DesktopPageRegistry.visible.forEach { spec ->
                    RailNavItem(
                        spec = spec,
                        selected = spec.page == selected,
                        onClick = { onSelect(spec.page) },
                    )
                }
                Spacer(Modifier.weight(1f))
                CoreVersionRailLabel(coreVersionText)
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(vertical = 16.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(DesktopTokens.SidebarHeaderHeight)
                        .padding(horizontal = DesktopTokens.SidebarHorizontalInset),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                            DesktopIcon(
                                icon = DesktopIcons.App,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                size = 21.sp,
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("弹幕API", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("服务控制台", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DesktopIconButtonGlyph(
                        icon = DesktopIcons.Collapse,
                        onClick = onToggleCollapsed,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        contentDescription = "收起侧栏",
                    )
                }

                Spacer(Modifier.height(12.dp))
                DesktopPageRegistry.visible.forEach { spec ->
                    NavItem(
                        spec = spec,
                        selected = spec.page == selected,
                        onClick = { onSelect(spec.page) },
                    )
                }

                Spacer(Modifier.weight(1f))
                CoreVersionExpandedLabel(coreVersionText)
            }
        }
    }
}

@Composable
private fun RailControlButton(
    icon: DesktopIconGlyph,
    onClick: () -> Unit,
    contentDescription: String,
) {
    DesktopIconButtonGlyph(
        icon = icon,
        onClick = onClick,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        contentDescription = contentDescription,
        modifier = Modifier
            .size(44.dp)
            .semantics { this.contentDescription = contentDescription },
    )
}

@Composable
private fun RailNavItem(
    spec: DesktopPageSpec,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val selectedColor = MaterialTheme.colorScheme.onSecondaryContainer
    val contentColor = if (selected) selectedColor else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        contentAlignment = Alignment.Center,
    ) {
        DesktopSurface(
            modifier = Modifier.size(44.dp),
            shape = RoundedCornerShape(12.dp),
            color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
            contentColor = contentColor,
            onClick = onClick,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                DesktopIcon(spec.icon, tint = contentColor, size = 21.sp)
            }
        }
        if (selected) {
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(width = 3.dp, height = 28.dp),
                shape = RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp),
                color = MaterialTheme.colorScheme.primary,
            ) {}
        }
    }
}

@Composable
private fun CoreVersionExpandedLabel(text: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = DesktopTokens.SidebarHorizontalInset),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "核心版本",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun CoreVersionRailLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .width(DesktopTokens.SidebarCollapsedWidth)
            .padding(bottom = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        softWrap = false,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

private fun sidebarCoreVersion(controller: DesktopRuntimeController): String {
    val runtime = controller.configuredRuntime()
    val variant = DesktopCoreVariant.fromKey(runtime.variant)
    val info = DesktopCoreInstaller.inspect(
        scriptDir = java.io.File(controller.paths.runtimeDir, "nodejs-project"),
        variant = variant,
        repository = variant.defaultRepository.orEmpty(),
        branch = DesktopCoreInstaller.DEFAULT_BRANCH,
    )
    return displaySidebarCoreVersion(info)
}

internal fun displaySidebarCoreVersion(info: com.example.danmuapiapp.desktop.core.DesktopCoreInfo): String = when {
    !info.installed -> "未安装"
    !info.valid -> "未知"
    !info.version.isNullOrBlank() -> info.version
    !info.source?.commitSha.isNullOrBlank() -> info.source.commitSha.take(12)
    else -> "未知"
}

@Composable
private fun NavItem(
    spec: DesktopPageSpec,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    DesktopSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DesktopTokens.SidebarHorizontalInset, vertical = 2.dp),
        shape = DesktopTokens.ItemShape,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        contentColor = contentColor,
        onClick = onClick,
    ) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(
                    horizontal = DesktopTokens.SidebarContentInset,
                    vertical = 9.dp,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier.width(DesktopTokens.SidebarIconSlot),
                    contentAlignment = Alignment.Center,
                ) {
                    DesktopIcon(spec.icon, tint = contentColor, size = 19.sp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = spec.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = contentColor,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = spec.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

@Composable
private fun MainContent(
    page: DesktopPage,
    state: ServiceUiState,
    controller: DesktopRuntimeController,
    themePreference: ThemePreference,
    onThemeChange: (ThemePreference) -> Unit,
    darkTheme: Boolean,
    overviewRefreshKey: Int = 0,
    onOverviewRefresh: () -> Unit = {},
    foregroundTick: Int = 0,
    automaticCoreUpdate: CoreUpdateCheck? = null,
    onAutomaticCoreUpdateConsumed: () -> Unit = {},
    queuedCoreUpdate: CoreUpdateCheck? = null,
    onQueuedCoreUpdateConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    DesktopPageScaffold(
        modifier = modifier,
        title = when (page) {
            DesktopPage.Overview -> "服务控制台"
            DesktopPage.Core -> "核心"
            DesktopPage.Configuration -> "配置"
            DesktopPage.Logs -> "日志"
            DesktopPage.Settings -> "设置"
            DesktopPage.About -> "关于"
            else -> page.label
        },
        subtitle = when (page) {
            DesktopPage.Overview -> "管理服务状态、访问入口与运行诊断"
            DesktopPage.Core -> "手动下载、更新、重装与版本管理"
            DesktopPage.Configuration -> "管理当前核心支持的环境变量"
            DesktopPage.Logs -> "查看、筛选、复制和导出桌面端运行日志"
            DesktopPage.Settings -> "按分类管理运行、网络与应用偏好"
            DesktopPage.About -> "版本、运行时与项目说明"
            else -> null
        },
        status = if (page == DesktopPage.Overview) state.phase.toDesktopStatus() else null,
        leadingContent = {
            DesktopIcon(
                icon = when (page) {
                    DesktopPage.Overview -> DesktopIcons.Overview
                    DesktopPage.Core -> DesktopIcons.Core
                    DesktopPage.Configuration -> DesktopIcons.Tools
                    DesktopPage.Logs -> DesktopIcons.Activity
                    DesktopPage.Settings -> DesktopIcons.Settings
                    DesktopPage.About -> DesktopIcons.About
                    else -> DesktopIcons.Info
                },
                tint = MaterialTheme.colorScheme.primary,
                size = 22.sp,
            )
        },
        actions = if (page == DesktopPage.Overview) {
            {
                DesktopActionButton(
                    label = "刷新状态",
                    onClick = onOverviewRefresh,
                    style = DesktopActionButtonStyle.Outlined,
                    icon = DesktopIcons.Restart,
                )
            }
        } else null,
    ) {
        when (page) {
            DesktopPage.Overview -> OverviewPage(
                controller = controller,
                paths = controller.paths,
                state = state,
                isDark = darkTheme,
                showHeader = false,
                onRefresh = onOverviewRefresh,
                refreshKey = overviewRefreshKey,
            )
            DesktopPage.Core -> CorePage(
                controller = controller,
                paths = controller.paths,
                state = state,
                foregroundTick = foregroundTick,
                initialUpdate = queuedCoreUpdate,
                onInitialUpdateConsumed = onQueuedCoreUpdateConsumed,
            )
            DesktopPage.Configuration -> CoreEnvConfigPage(
                controller = controller,
                paths = controller.paths,
                state = state,
            )
            DesktopPage.Logs -> LogsPage(
                paths = controller.paths,
                state = state,
            )
            DesktopPage.Settings -> SettingsPage(
                settings = controller.settings,
                paths = controller.paths,
                themePreference = themePreference,
                onThemeChange = onThemeChange,
                onRuntimeConfigChanged = controller::applyRuntimeConfiguration,
                showHeader = false,
            )
            DesktopPage.About -> AboutPage()
            else -> OverviewPage(controller, controller.paths, state, darkTheme, showHeader = false)
        }
    }
}

private fun shellDiagnostic(error: Throwable): String =
    error.message?.trim()?.takeIf { it.isNotEmpty() } ?: error::class.java.simpleName

@Composable
private fun StatusStrip(state: ServiceUiState, compact: Boolean = false) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(DesktopTokens.StatusStripHeight)
                .padding(horizontal = DesktopTokens.PagePadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DesktopStatusBadge(
                status = state.phase.toDesktopStatus(),
                label = phaseLabel(state.phase),
                compact = true,
            )
            Text(
                text = if (state.phase == ServicePhase.Running && state.port != null) {
                    "127.0.0.1:${state.port}"
                } else {
                    state.message
                },
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (!compact) {
                Text(
                    text = "弹幕API · Windows",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
