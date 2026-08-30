package com.example.danmuapiapp.desktop.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Stable, dependency-free icon glyphs for the desktop UI. */
enum class DesktopIconGlyph(val glyph: String, val description: String) {
    App("◈", "应用"),
    Overview("⌂", "概览"),
    Core("◆", "核心"),
    Configuration("☷", "配置"),
    Downloads("⇩", "下载"),
    Activity("≋", "活动"),
    Tools("⚒", "工具"),
    Settings("⚙", "设置"),
    About("ⓘ", "关于"),
    Success("✓", "成功"),
    Warning("!", "注意"),
    Error("×", "错误"),
    Info("i", "信息"),
    Empty("□", "空"),
    Restart("↻", "重启"),
    Start("▶", "启动"),
    Stop("■", "停止"),
    Copy("▣", "复制"),
    Folder("▱", "文件夹"),
    Link("↗", "链接"),
    Expand("›", "展开侧栏"),
    Collapse("‹", "收起侧栏"),
    Back("←", "返回"),
}

object DesktopIcons {
    val App: DesktopIconGlyph = DesktopIconGlyph.App
    val Overview: DesktopIconGlyph = DesktopIconGlyph.Overview
    val Core: DesktopIconGlyph = DesktopIconGlyph.Core
    val Configuration: DesktopIconGlyph = DesktopIconGlyph.Configuration
    val Downloads: DesktopIconGlyph = DesktopIconGlyph.Downloads
    val Activity: DesktopIconGlyph = DesktopIconGlyph.Activity
    val Tools: DesktopIconGlyph = DesktopIconGlyph.Tools
    val Settings: DesktopIconGlyph = DesktopIconGlyph.Settings
    val About: DesktopIconGlyph = DesktopIconGlyph.About
    val Success: DesktopIconGlyph = DesktopIconGlyph.Success
    val Warning: DesktopIconGlyph = DesktopIconGlyph.Warning
    val Error: DesktopIconGlyph = DesktopIconGlyph.Error
    val Info: DesktopIconGlyph = DesktopIconGlyph.Info
    val Empty: DesktopIconGlyph = DesktopIconGlyph.Empty
    val Restart: DesktopIconGlyph = DesktopIconGlyph.Restart
    val Start: DesktopIconGlyph = DesktopIconGlyph.Start
    val Stop: DesktopIconGlyph = DesktopIconGlyph.Stop
    val Copy: DesktopIconGlyph = DesktopIconGlyph.Copy
    val Folder: DesktopIconGlyph = DesktopIconGlyph.Folder
    val Link: DesktopIconGlyph = DesktopIconGlyph.Link
    val Expand: DesktopIconGlyph = DesktopIconGlyph.Expand
    val Collapse: DesktopIconGlyph = DesktopIconGlyph.Collapse
    val Back: DesktopIconGlyph = DesktopIconGlyph.Back
}

/**
 * Text-backed icon renderer. It intentionally uses only Compose foundation APIs,
 * so no material-icons artifact is needed by the desktop module.
 */
@Composable
fun DesktopIcon(
    icon: DesktopIconGlyph,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    size: TextUnit = 18.sp,
    contentDescription: String? = icon.description,
) {
    BasicText(
        text = icon.glyph,
        modifier = modifier,
        style = TextStyle(
            color = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurface else tint,
            fontSize = size,
            fontWeight = FontWeight.SemiBold,
        ),
    )
}

@Composable
fun DesktopIconButtonGlyph(
    icon: DesktopIconGlyph,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
    contentDescription: String = icon.description,
) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(36.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        color = Color.Transparent,
    ) {
        Box(contentAlignment = Alignment.Center) {
            DesktopIcon(
                icon = icon,
                tint = tint,
                contentDescription = contentDescription,
            )
        }
    }
}
