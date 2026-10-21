package com.example.danmuapiapp.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.danmuapiapp.ui.theme.LocalAppDarkTheme
import kotlinx.coroutines.delay

private val ToastShape = RoundedCornerShape(16.dp)
private val DefaultToastClearance = 88.dp

internal object AppToastWindowRegistry {
    var platformDialogCount by mutableIntStateOf(0)
        private set

    fun attachPlatformDialog() {
        platformDialogCount += 1
    }

    fun detachPlatformDialog() {
        platformDialogCount = (platformDialogCount - 1).coerceAtLeast(0)
    }
}

/**
 * Render-only host. Message ownership lives in [AppToastController], so every window can host the
 * same toast without creating another queue.
 */
@Composable
fun AppToastHost(
    modifier: Modifier = Modifier,
    controller: AppToastController = AppToast.controller,
    bottomPadding: Dp = DefaultToastClearance,
    applyWindowInsets: Boolean = true,
    renderInPlatformDialogWindow: Boolean = false
) {
    if (!renderInPlatformDialogWindow && AppToastWindowRegistry.platformDialogCount > 0) {
        return
    }
    val message = controller.currentMessage
    val accessibilityManager = LocalAccessibilityManager.current
    val timeoutMillis = message?.let { current ->
        val recommended = accessibilityManager?.calculateRecommendedTimeoutMillis(
            originalTimeoutMillis = current.durationMillis,
            containsIcons = true,
            containsText = true,
            containsControls = current.actionLabel != null
        ) ?: current.durationMillis
        recommended.coerceAtLeast(current.durationMillis)
    }

    LaunchedEffect(message?.id, timeoutMillis) {
        val current = message ?: return@LaunchedEffect
        delay(timeoutMillis ?: current.durationMillis)
        controller.dismiss(current.id)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(100f)
            .then(if (applyWindowInsets) Modifier.imePadding().navigationBarsPadding() else Modifier),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedContent(
            targetState = message,
            modifier = Modifier
                .widthIn(max = 492.dp)
                .padding(horizontal = 16.dp, vertical = bottomPadding),
            contentAlignment = Alignment.BottomCenter,
            transitionSpec = {
                (
                    fadeIn(
                        animationSpec = tween(
                            durationMillis = 160,
                            easing = FastOutSlowInEasing
                        )
                    ) + slideInVertically(
                        animationSpec = tween(
                            durationMillis = 180,
                            easing = FastOutSlowInEasing
                        )
                    ) { height -> height / 3 }
                ).togetherWith(
                    fadeOut(animationSpec = tween(durationMillis = 130)) +
                        slideOutVertically(
                            animationSpec = tween(durationMillis = 130)
                        ) { height -> height / 6 }
                )
            },
            contentKey = { current ->
                current?.let { "${it.kind}:${it.text}:${it.actionLabel.orEmpty()}" } ?: "none"
            },
            label = "appToast"
        ) { current ->
            if (current != null) {
                AppToastBubble(
                    message = current,
                    onAction = current.action,
                    onDismiss = { controller.dismiss(current.id) }
                )
            }
        }
    }
}

@Composable
private fun AppToastBubble(
    message: AppToastMessage,
    onAction: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val statusColor = message.kind.statusColor()
    val containerColor = statusColor.copy(alpha = 0.07f).compositeOver(colors.surfaceContainerHigh)
    val hasAction = message.actionLabel != null && onAction != null
    val textMaxWidth = if (hasAction) 330.dp else 400.dp

    Surface(
        modifier = Modifier
            .widthIn(max = 460.dp)
            .defaultMinSize(minHeight = 44.dp)
            .semantics {
                liveRegion = LiveRegionMode.Polite
            },
        shape = ToastShape,
        color = containerColor,
        contentColor = colors.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.30f))
    ) {
        Row(
            modifier = Modifier.padding(
                start = 14.dp,
                top = 10.dp,
                end = if (hasAction) 6.dp else 14.dp,
                bottom = 10.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = message.kind.icon(),
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = statusColor
            )
            Text(
                text = message.text,
                modifier = Modifier.widthIn(max = textMaxWidth),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (hasAction) {
                TextButton(
                    onClick = {
                        onAction()
                        onDismiss()
                    },
                    modifier = Modifier.defaultMinSize(minWidth = 0.dp, minHeight = 32.dp)
                ) {
                    Text(
                        text = message.actionLabel.orEmpty(),
                        color = statusColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun AppToastKind.statusColor(): Color {
    val colors = MaterialTheme.colorScheme
    val dark = LocalAppDarkTheme.current
    return when (this) {
        AppToastKind.Info -> colors.primary
        AppToastKind.Success -> if (dark) Color(0xFF83D8B0) else Color(0xFF2F805D)
        AppToastKind.Warning -> if (dark) Color(0xFFF3C46F) else Color(0xFF9A641B)
        AppToastKind.Error -> colors.error
    }
}

private fun AppToastKind.icon(): ImageVector = when (this) {
    AppToastKind.Info -> Icons.Rounded.Info
    AppToastKind.Success -> Icons.Rounded.CheckCircle
    AppToastKind.Warning -> Icons.Rounded.WarningAmber
    AppToastKind.Error -> Icons.Rounded.ErrorOutline
}
