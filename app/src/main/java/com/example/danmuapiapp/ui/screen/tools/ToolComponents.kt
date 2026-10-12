package com.example.danmuapiapp.ui.screen.tools

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun ToolHeader(title: String, onBack: () -> Unit, subtitle: String = "",
    actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        ToolIcon(Icons.AutoMirrored.Outlined.ArrowBack, "返回", onBack, size = 44)
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) ToolHint(subtitle, maxLines = 1)
        }
        actions()
    }
}
@Composable internal fun ToolIcon(icon: ImageVector, label: String, onClick: () -> Unit,
    enabled: Boolean = true, busy: Boolean = false, danger: Boolean = false, size: Int = 32) {
    IconButton(onClick, Modifier.size(size.dp), enabled = enabled && !busy) {
        if (busy) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
        else Icon(icon, label, Modifier.size(if (size >= 40) 22.dp else 18.dp),
            tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable internal fun ToolCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Surface(modifier.fillMaxWidth().clip(shape).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = shape, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}
@Composable internal fun ToolTitle(text: String, modifier: Modifier = Modifier, size: Int = 13, maxLines: Int = 2) {
    Text(text, modifier, fontSize = size.sp, lineHeight = (size + 5).sp, fontWeight = FontWeight.Bold,
        maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}
@Composable internal fun ToolHint(text: String, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) {
    Text(text, modifier, fontSize = 11.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}
@Composable internal fun ToolInput(value: String, onChange: (String) -> Unit, hint: String,
    modifier: Modifier = Modifier, icon: ImageVector? = null, number: Boolean = false,
    onSubmit: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    BasicTextField(value, onChange, modifier.fillMaxWidth(), singleLine = true,
        textStyle = TextStyle(color = colors.onSurface, fontSize = 13.sp), cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text,
            imeAction = if (onSubmit != null) ImeAction.Search else ImeAction.Done),
        keyboardActions = KeyboardActions(onSearch = { onSubmit?.invoke() }, onDone = { onSubmit?.invoke() }),
        decorationBox = { inner ->
            Row(Modifier.defaultMinSize(minHeight = 40.dp).clip(RoundedCornerShape(12.dp)).background(colors.surface)
                .border(1.dp, colors.outlineVariant.copy(alpha = .7f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (icon != null) Icon(icon, null, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(hint, fontSize = 12.sp, color = colors.onSurfaceVariant)
                    inner()
                }
            }
        })
}
@Composable internal fun ToolChip(label: String, selected: Boolean = false, onClick: () -> Unit,
    modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick), shape = RoundedCornerShape(8.dp),
        color = if (selected) colors.primary.copy(alpha = .13f) else colors.surfaceContainerHigh,
        border = if (selected) BorderStroke(1.dp, colors.primary.copy(alpha = .35f)) else null) {
        Text(label, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = TextStyle(
            fontSize = 11.sp, lineHeight = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) colors.primary else colors.onSurfaceVariant, platformStyle = PlatformTextStyle(includeFontPadding = false)), maxLines = 1)
    }
}
@Composable internal fun ToolButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    icon: ImageVector? = null, outlined: Boolean = false, enabled: Boolean = true, busy: Boolean = false) {
    val body: @Composable RowScope.() -> Unit = {
        if (busy) { CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp); Spacer(Modifier.width(6.dp)) }
        else if (icon != null) { Icon(icon, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
        Text(label, fontSize = 12.5.sp, maxLines = 1)
    }
    if (outlined) OutlinedButton(onClick, modifier.heightIn(min = 36.dp), enabled = enabled && !busy,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), shape = RoundedCornerShape(12.dp), content = body)
    else Button(onClick, modifier.heightIn(min = 36.dp), enabled = enabled && !busy,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), shape = RoundedCornerShape(12.dp), content = body)
}
@Composable internal fun ToolEmpty(text: String, icon: ImageVector, hint: String = "") {
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .5f))
        Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (hint.isNotBlank()) ToolHint(hint)
    }
}
@Composable internal fun ToolError(message: String, onDismiss: (() -> Unit)? = null) {
    Surface(color = MaterialTheme.colorScheme.errorContainer.copy(alpha = .5f), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, Modifier.weight(1f), fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            if (onDismiss != null) TextButton(onDismiss) { Text("关闭", fontSize = 11.sp) }
        }
    }
}
@Composable internal fun ToolListRow(leading: String, secondary: String, title: String, subtitle: String,
    onClick: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Column {
        Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(57.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(leading, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(secondary, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) ToolHint(subtitle, maxLines = 2)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(0.dp), content = actions)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))
    }
}
