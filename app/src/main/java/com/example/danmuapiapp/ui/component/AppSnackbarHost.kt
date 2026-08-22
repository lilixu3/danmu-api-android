package com.example.danmuapiapp.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.ui.theme.glassBorderColor
import com.example.danmuapiapp.ui.theme.glassSurfaceColor
import com.example.danmuapiapp.ui.component.liquid.AppGlassButton
import com.example.danmuapiapp.ui.component.liquid.AppGlassIconButton

private val BottomBarClearance = 80.dp

val LocalFloatingBottomBarVisible = staticCompositionLocalOf { false }

@Composable
fun AppSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    aboveBottomBar: Boolean = LocalFloatingBottomBarVisible.current
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .then(
                if (aboveBottomBar) {
                    Modifier.padding(bottom = BottomBarClearance)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 16.dp),
        snackbar = { data ->
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AppSnackbar(data)
            }
        }
    )
}

@Composable
private fun AppSnackbar(data: SnackbarData) {
    val hasTrailingAction = data.visuals.actionLabel != null || data.visuals.withDismissAction
    val containerColor = glassSurfaceColor()

    AppGlassSurface(
        modifier = Modifier
            .widthIn(max = 520.dp)
            .defaultMinSize(minHeight = 56.dp),
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, glassBorderColor()),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = data.visuals.message,
                modifier = if (hasTrailingAction) {
                    Modifier.weight(1f)
                } else {
                    Modifier.widthIn(max = 420.dp)
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            data.visuals.actionLabel?.let { label ->
                AppGlassButton(
                    onClick = data::performAction,
                    modifier = Modifier.height(34.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    Text(label)
                }
            }
            if (data.visuals.withDismissAction) {
                AppGlassIconButton(onClick = data::dismiss, size = 34.dp) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "关闭提示"
                    )
                }
            }
        }
    }
}
