package com.example.danmuapiapp.desktop.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Semantic tone shared by confirmation, information and error dialogs. */
enum class DesktopDialogTone {
    Neutral,
    Info,
    Warning,
    Danger,
    Success,
}

data class DesktopDialogAction(
    val label: String,
    val tone: DesktopDialogTone = DesktopDialogTone.Neutral,
    val isPrimary: Boolean = false,
)

data class DesktopDialogSpec(
    val title: String,
    val description: String? = null,
    val tone: DesktopDialogTone = DesktopDialogTone.Neutral,
    val dismissOnClickOutside: Boolean = true,
    val dismissOnEscape: Boolean = true,
)

/**
 * Compose 内应用模态层。它不创建 DialogWindow，也不经过 Windows 原生标题栏和独立窗口
 * 的 DPI 测量；遮罩、卡片、滚动区和按钮全部绘制在当前主窗口的主题画布中。
 */
@Composable
fun DesktopDialogFrame(
    spec: DesktopDialogSpec,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: DesktopIconGlyph? = null,
    content: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.56f))
            .clickable(
                enabled = spec.dismissOnClickOutside,
                onClick = onDismissRequest,
            ),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            val cardWidth = minOf(maxWidth * 0.92f, 900.dp)
            val cardHeight = minOf(maxHeight * 0.86f, 760.dp)
            Card(
                modifier = modifier
                    .widthIn(min = 320.dp, max = cardWidth)
                    .heightIn(min = 0.dp, max = cardHeight)
                    .wrapContentHeight()
                    .clickable(onClick = {}),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 18.dp),
            ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                    ) {

                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (leadingIcon != null) {
                            val toneColor = dialogToneColor(spec.tone)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = toneColor.copy(alpha = 0.14f),
                                contentColor = toneColor,
                            ) {
                                Box(modifier = Modifier.padding(10.dp)) {
                                    DesktopIcon(leadingIcon, tint = toneColor, size = 20.sp)
                                }
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = spec.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (!spec.description.isNullOrBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = spec.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = cardHeight - 180.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        content()
                    }
                    Spacer(Modifier.height(22.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                        content = actions,
                    )
                }
            }
        }
    }
}

@Composable
fun DesktopDialogButton(
    action: DesktopDialogAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        action.isPrimary && action.tone == DesktopDialogTone.Danger -> Button(
            onClick = onClick,
            modifier = modifier,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) { Text(action.label) }
        action.isPrimary -> Button(onClick = onClick, modifier = modifier) { Text(action.label) }
        action.tone == DesktopDialogTone.Danger -> OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) { Text(action.label) }
        else -> TextButton(onClick = onClick, modifier = modifier) { Text(action.label) }
    }
}

@Composable
private fun dialogToneColor(tone: DesktopDialogTone): Color = when (tone) {
    DesktopDialogTone.Danger -> MaterialTheme.colorScheme.error
    DesktopDialogTone.Warning -> LocalDesktopThemePalette.current.warning.content
    DesktopDialogTone.Info -> LocalDesktopThemePalette.current.info.content
    DesktopDialogTone.Success -> LocalDesktopThemePalette.current.success.content
    DesktopDialogTone.Neutral -> MaterialTheme.colorScheme.primary
}
