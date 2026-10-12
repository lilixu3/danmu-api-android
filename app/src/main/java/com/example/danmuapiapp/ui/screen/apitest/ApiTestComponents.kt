package com.example.danmuapiapp.ui.screen.apitest

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

// Geometry follows the Flutter tool pages; colors follow the Android app theme.
@Composable
internal fun ApiPageHeader(title: String, onBack: () -> Unit, subtitle: String = "",
    actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", Modifier.size(22.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

@Composable
internal fun ApiCard(modifier: Modifier = Modifier, radius: Int = 18, accent: Color? = null,
    onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(radius.dp)
    Surface(modifier = modifier.fillMaxWidth().clip(shape)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier), shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accent?.copy(alpha = 0.22f) ?: MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
internal fun ApiLabel(text: String) { Text(text, fontSize = 12.5.sp, fontWeight = FontWeight.Bold) }

@Composable
internal fun ApiHint(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, fontSize = 11.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun ApiInput(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier,
    multiline: Boolean = false, mono: Boolean = false, leading: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null, onSubmit: (() -> Unit)? = null) {
    OutlinedTextField(value = value, onValueChange = onChange, modifier = modifier.fillMaxWidth(), singleLine = !multiline,
        minLines = if (multiline) 5 else 1, maxLines = if (multiline) 9 else 1,
        textStyle = TextStyle(fontSize = if (mono) 11.5.sp else 13.sp,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default),
        placeholder = { Text(hint, fontSize = 12.sp) }, shape = RoundedCornerShape(12.dp),
        leadingIcon = leading?.let { { Icon(it, null, Modifier.size(18.dp)) } }, trailingIcon = trailing,
        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        keyboardOptions = KeyboardOptions(imeAction = if (onSubmit != null) ImeAction.Search else ImeAction.Default),
        keyboardActions = KeyboardActions(onSearch = { onSubmit?.invoke() }))
}

@Composable
internal fun ApiAction(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier,
    busy: Boolean = false, enabled: Boolean = true, outlined: Boolean = false) {
    val content: @Composable RowScope.() -> Unit = {
        if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        else Icon(icon, null, Modifier.size(17.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, fontSize = 12.5.sp, maxLines = 1)
    }
    if (outlined) OutlinedButton(onClick, modifier.heightIn(min = 44.dp), enabled = enabled && !busy,
        shape = RoundedCornerShape(13.dp), contentPadding = PaddingValues(horizontal = 10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)), content = content)
    else Button(onClick, modifier.heightIn(min = 44.dp), enabled = enabled && !busy,
        shape = RoundedCornerShape(13.dp), contentPadding = PaddingValues(horizontal = 10.dp), content = content)
}

@Composable
internal fun ApiTag(text: String, color: Color = MaterialTheme.colorScheme.primary, selected: Boolean = false,
    onClick: (() -> Unit)? = null) {
    Surface(modifier = Modifier.clip(RoundedCornerShape(7.dp))
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier), shape = RoundedCornerShape(7.dp),
        color = if (selected) color.copy(alpha = 0.17f) else color.copy(alpha = 0.08f),
        border = if (selected) BorderStroke(1.dp, color.copy(alpha = 0.35f)) else null) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = color, fontSize = 10.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
internal fun ApiEmpty(message: String, hint: String = "", action: @Composable (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(message, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (hint.isNotBlank()) ApiHint(hint)
            action?.invoke()
        }
    }
}

@Composable
internal fun ApiLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp) }
}

internal fun apiTime(seconds: Double): String {
    val value = seconds.toLong().coerceAtLeast(0)
    return if (value >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", value / 3600, value / 60 % 60, value % 60)
    else String.format(Locale.ROOT, "%02d:%02d", value / 60, value % 60)
}

internal fun apiBytes(bytes: Int): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0)
    bytes >= 1024 -> String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}
