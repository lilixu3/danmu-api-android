package com.example.danmuapiapp.desktop.app

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.desktop.runtime.DesktopPaths
import com.example.danmuapiapp.desktop.runtime.DesktopRuntimeController
import com.example.danmuapiapp.desktop.runtime.ServicePhase

/** 设计 token：统一间距与圆角，避免各页面随意取值。 */
object Dimens {
    val SidebarWidth = 232.dp
    val StatusStripHeight = 40.dp
    val PagePadding = 24.dp
    val CardCorner = 16.dp
    val ItemCorner = 10.dp
}

private val LightScheme = lightColorScheme()
private val DarkScheme = darkColorScheme()

enum class DesktopPage(val label: String) {
    Overview("概览"),
    Core("核心"),
    Configuration("配置"),
    Downloads("下载"),
    Activity("活动"),
    Tools("工具"),
    Settings("设置"),
    About("关于"),
}

/** 主题偏好：system / light / dark。 */
enum class ThemePreference(val key: String, val label: String) {
    System("system", "跟随系统"),
    Light("light", "浅色"),
    Dark("dark", "深色");

    companion object {
        fun fromKey(raw: String?): ThemePreference =
            entries.firstOrNull { it.key == raw } ?: System
    }
}

@Composable
fun statusColor(phase: ServicePhase, isDark: Boolean): Color = when (phase) {
    ServicePhase.Running -> if (isDark) Color(0xFF6FD598) else Color(0xFF1E8E4E)
    ServicePhase.Failed -> MaterialTheme.colorScheme.error
    ServicePhase.Preparing, ServicePhase.Starting, ServicePhase.Stopping -> MaterialTheme.colorScheme.primary
    ServicePhase.Stopped -> MaterialTheme.colorScheme.outline
}

@Composable
fun DesktopShell(
    controller: DesktopRuntimeController,
    themePreference: ThemePreference,
    onThemeChange: (ThemePreference) -> Unit,
) {
    val state by controller.state.collectAsState()
    val dark = when (themePreference) {
        ThemePreference.Light -> false
        ThemePreference.Dark -> true
        ThemePreference.System -> isSystemInDarkTheme()
    }
    var page by remember { mutableStateOf(DesktopPage.Overview) }

    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column {
                Row(modifier = Modifier.weight(1f)) {
                    NavSidebar(
                        selected = page,
                        onSelect = { page = it },
                        state = state,
                        isDark = dark,
                        modifier = Modifier.width(Dimens.SidebarWidth).fillMaxHeight(),
                    )
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentAlignment = Alignment.TopStart,
                    ) {
                        when (page) {
                            DesktopPage.Overview -> OverviewPage(controller, controller.paths, state, dark)
                            DesktopPage.Settings -> SettingsPage(
                                settings = controller.settings,
                                paths = controller.paths,
                                themePreference = themePreference,
                                onThemeChange = onThemeChange,
                                onRuntimeConfigChanged = controller::applyRuntimeConfiguration,
                            )
                            DesktopPage.About -> AboutPage()
                            else -> PlaceholderPage(page)
                        }
                    }
                }
                StatusStrip(state, dark)
            }
        }
    }
}

@Composable
private fun NavSidebar(
    selected: DesktopPage,
    onSelect: (DesktopPage) -> Unit,
    state: com.example.danmuapiapp.desktop.runtime.ServiceUiState,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize().padding(vertical = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(statusColor(state.phase, isDark), CircleShape),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "弹幕API",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(12.dp))
            DesktopPage.entries.forEach { entry ->
                NavItem(
                    label = entry.label,
                    selected = entry == selected,
                    onClick = { onSelect(entry) },
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "服务状态：" + phaseLabel(state.phase),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }
}

@Composable
private fun NavItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(Dimens.ItemCorner),
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp).height(40.dp),
    ) {
        Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
fun phaseLabel(phase: ServicePhase): String = when (phase) {
    ServicePhase.Stopped -> "已停止"
    ServicePhase.Preparing -> "准备中"
    ServicePhase.Starting -> "启动中"
    ServicePhase.Running -> "运行中"
    ServicePhase.Stopping -> "停止中"
    ServicePhase.Failed -> "失败"
}

@Composable
private fun StatusStrip(
    state: com.example.danmuapiapp.desktop.runtime.ServiceUiState,
    isDark: Boolean,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(Dimens.StatusStripHeight).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(statusColor(state.phase, isDark), CircleShape),
            )
            Text(
                text = phaseLabel(state.phase) + if (state.phase == ServicePhase.Running) {
                    " · 127.0.0.1:${state.port}"
                } else {
                    ""
                },
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "弹幕API 0.1.0",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
