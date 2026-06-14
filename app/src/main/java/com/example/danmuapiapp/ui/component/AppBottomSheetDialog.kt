package com.example.danmuapiapp.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.example.danmuapiapp.domain.model.DialogPresentationPreference

/**
 * 弹窗版式：按场景区分布局。
 *
 * 注意：
 * - Form / Selection / Status 样式内部已自带纵向滚动容器；
 * - 调用方传入 text 内容时不要再额外套 `verticalScroll`，否则容易触发
 *   Compose 的 “infinity maximum height constraints” 崩溃。
 */
enum class AppBottomSheetStyle {
    Confirm,
    Form,
    Selection,
    Status
}

/**
 * 弹窗语义色调：用于图标与强调线。
 */
enum class AppBottomSheetTone {
    Neutral,
    Brand,
    Success,
    Warning,
    Danger,
    Info
}

internal fun resolveSheetGesturesEnabled(
    bottomSheetGesturesEnabled: Boolean?
): Boolean {
    return bottomSheetGesturesEnabled ?: true
}

internal fun resolveDialogPresentation(
    globalPreference: DialogPresentationPreference?,
    presentation: DialogPresentationPreference?
): DialogPresentationPreference {
    return presentation ?: globalPreference ?: DialogPresentationPreference.Popup
}

private data class SheetTonePalette(
    val iconContainer: Color,
    val iconTint: Color,
    val accent: Color,
    val statusTint: Color
)

@Composable
private fun rememberSheetTonePalette(tone: AppBottomSheetTone): SheetTonePalette {
    val c = MaterialTheme.colorScheme
    return when (tone) {
        AppBottomSheetTone.Neutral -> SheetTonePalette(
            iconContainer = c.surfaceContainerHighest,
            iconTint = c.onSurfaceVariant,
            accent = c.onSurfaceVariant,
            statusTint = c.surfaceContainerHigh
        )

        AppBottomSheetTone.Brand -> SheetTonePalette(
            iconContainer = c.primaryContainer,
            iconTint = c.primary,
            accent = c.primary,
            statusTint = c.primaryContainer
        )

        AppBottomSheetTone.Success -> SheetTonePalette(
            iconContainer = c.tertiaryContainer,
            iconTint = c.tertiary,
            accent = c.tertiary,
            statusTint = c.tertiaryContainer
        )

        AppBottomSheetTone.Warning -> SheetTonePalette(
            iconContainer = c.secondaryContainer,
            iconTint = c.onSecondaryContainer,
            accent = c.secondary,
            statusTint = c.secondaryContainer
        )

        AppBottomSheetTone.Danger -> SheetTonePalette(
            iconContainer = c.errorContainer,
            iconTint = c.error,
            accent = c.error,
            statusTint = c.errorContainer
        )

        AppBottomSheetTone.Info -> SheetTonePalette(
            iconContainer = c.secondaryContainer,
            iconTint = c.secondary,
            accent = c.secondary,
            statusTint = c.secondaryContainer
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AppBottomSheetDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    style: AppBottomSheetStyle = AppBottomSheetStyle.Form,
    tone: AppBottomSheetTone = AppBottomSheetTone.Brand,
    presentation: DialogPresentationPreference? = null,
    bottomSheetGesturesEnabled: Boolean? = null,
    sheetGesturesEnabled: Boolean? = null,
    minHeight: Dp = Dp.Unspecified,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    confirmButton: (@Composable () -> Unit)? = null,
    dismissButton: (@Composable () -> Unit)? = null,
    fillBody: Boolean = false,
) {
    val dialogPreferences = LocalDialogPreferences.current
    val resolvedPresentation = resolveDialogPresentation(
        globalPreference = dialogPreferences.presentation,
        presentation = presentation
    )
    val resolvedSheetGesturesEnabled = resolveSheetGesturesEnabled(
        bottomSheetGesturesEnabled = sheetGesturesEnabled
            ?: bottomSheetGesturesEnabled
            ?: dialogPreferences.bottomSheetGesturesEnabled
    )

    when (resolvedPresentation) {
        DialogPresentationPreference.Popup -> AppPopupDialogContent(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            style = style,
            tone = tone,
            minHeight = minHeight,
            icon = icon,
            title = title,
            text = text,
            confirmButton = confirmButton,
            dismissButton = dismissButton,
            fillBody = fillBody || minHeight.isSpecified
        )

        DialogPresentationPreference.BottomSheet -> AppBottomSheetDialogContent(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            style = style,
            tone = tone,
            minHeight = minHeight,
            sheetGesturesEnabled = resolvedSheetGesturesEnabled,
            icon = icon,
            title = title,
            text = text,
            confirmButton = confirmButton,
            dismissButton = dismissButton,
            fillBody = fillBody || minHeight.isSpecified
        )
    }
}

/**
 * 自适应面板弹窗：用于首页/控制台这类自定义内容较多的面板。
 *
 * 与 [AppBottomSheetDialog] 共用全局弹窗偏好：
 * - 默认跟随设置显示为居中弹窗；
 * - 用户在设置中切到底部弹窗时，仍保留原来的底部面板体验；
 * - 底部弹窗手势同样跟随全局开关。
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AppPanelDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    presentation: DialogPresentationPreference? = null,
    bottomSheetGesturesEnabled: Boolean? = null,
    sheetGesturesEnabled: Boolean? = null,
    showDragHandle: Boolean = true,
    sheetMaxHeightFraction: Float = 0.9f,
    popupMaxHeightFraction: Float = 0.86f,
    minHeight: Dp = 320.dp,
    popupMaxWidth: Dp = 560.dp,
    horizontalPadding: Dp = 20.dp,
    sheetTopPadding: Dp = 4.dp,
    sheetBottomPadding: Dp = 10.dp,
    popupVerticalPadding: Dp = 18.dp,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit
) {
    val dialogPreferences = LocalDialogPreferences.current
    val resolvedPresentation = resolveDialogPresentation(
        globalPreference = dialogPreferences.presentation,
        presentation = presentation
    )
    val resolvedSheetGesturesEnabled = resolveSheetGesturesEnabled(
        bottomSheetGesturesEnabled = sheetGesturesEnabled
            ?: bottomSheetGesturesEnabled
            ?: dialogPreferences.bottomSheetGesturesEnabled
    )
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    when (resolvedPresentation) {
        DialogPresentationPreference.Popup -> {
            val dialogMaxHeight = (screenHeight * popupMaxHeightFraction).coerceAtLeast(minHeight)
            BasicAlertDialog(onDismissRequest = onDismissRequest) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = popupMaxWidth),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 3.dp,
                    shadowElevation = 8.dp,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
                    )
                ) {
                    Column(
                        modifier = modifier
                            .fillMaxWidth()
                            .heightIn(min = minHeight, max = dialogMaxHeight)
                            .imePadding()
                            .padding(horizontal = horizontalPadding, vertical = popupVerticalPadding),
                        verticalArrangement = verticalArrangement,
                        horizontalAlignment = horizontalAlignment,
                        content = content
                    )
                }
            }
        }

        DialogPresentationPreference.BottomSheet -> {
            val sheetMaxHeight = (screenHeight * sheetMaxHeightFraction).coerceAtLeast(minHeight)
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = onDismissRequest,
                sheetState = sheetState,
                sheetGesturesEnabled = resolvedSheetGesturesEnabled,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 1.dp,
                dragHandle = if (showDragHandle) {
                    {
                        BottomSheetDefaults.DragHandle(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.34f)
                        )
                    }
                } else {
                    null
                }
            ) {
                Column(
                    modifier = modifier
                        .fillMaxWidth()
                        .heightIn(min = minHeight, max = sheetMaxHeight)
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(horizontal = horizontalPadding)
                        .padding(top = sheetTopPadding, bottom = sheetBottomPadding),
                    verticalArrangement = verticalArrangement,
                    horizontalAlignment = horizontalAlignment,
                    content = content
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AppBottomSheetDialogContent(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    style: AppBottomSheetStyle = AppBottomSheetStyle.Form,
    tone: AppBottomSheetTone = AppBottomSheetTone.Brand,
    minHeight: Dp = Dp.Unspecified,
    sheetGesturesEnabled: Boolean,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    confirmButton: (@Composable () -> Unit)? = null,
    dismissButton: (@Composable () -> Unit)? = null,
    fillBody: Boolean = false,
) {
    val tonePalette = rememberSheetTonePalette(tone)
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val sheetMaxHeight = (screenHeight * 0.9f).coerceAtLeast(320.dp)
    val bodyMaxHeight = when (style) {
        AppBottomSheetStyle.Confirm -> (screenHeight * 0.32f).coerceAtLeast(140.dp)
        AppBottomSheetStyle.Status -> (screenHeight * 0.42f).coerceAtLeast(220.dp)
        AppBottomSheetStyle.Selection -> (screenHeight * 0.56f).coerceAtLeast(280.dp)
        AppBottomSheetStyle.Form -> (screenHeight * 0.62f).coerceAtLeast(320.dp)
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        sheetGesturesEnabled = sheetGesturesEnabled,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 1.dp,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.34f)
            )
        }
    ) {
        AppDialogScaffoldContent(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = minHeight, max = sheetMaxHeight)
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 4.dp, bottom = 10.dp),
            style = style,
            tonePalette = tonePalette,
            bodyMaxHeight = bodyMaxHeight,
            icon = icon,
            title = title,
            text = text,
            confirmButton = confirmButton,
            dismissButton = dismissButton,
            fillBody = fillBody || minHeight.isSpecified
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AppPopupDialogContent(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    style: AppBottomSheetStyle = AppBottomSheetStyle.Form,
    tone: AppBottomSheetTone = AppBottomSheetTone.Brand,
    minHeight: Dp = Dp.Unspecified,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    confirmButton: (@Composable () -> Unit)? = null,
    dismissButton: (@Composable () -> Unit)? = null,
    fillBody: Boolean = false,
) {
    val tonePalette = rememberSheetTonePalette(tone)
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val dialogMaxHeight = (screenHeight * 0.86f).coerceAtLeast(320.dp)
    val bodyMaxHeight = when (style) {
        AppBottomSheetStyle.Confirm -> (screenHeight * 0.36f).coerceAtLeast(140.dp)
        AppBottomSheetStyle.Status -> (screenHeight * 0.46f).coerceAtLeast(220.dp)
        AppBottomSheetStyle.Selection -> (screenHeight * 0.58f).coerceAtLeast(280.dp)
        AppBottomSheetStyle.Form -> (screenHeight * 0.64f).coerceAtLeast(320.dp)
    }

    BasicAlertDialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
            )
        ) {
            AppDialogScaffoldContent(
                modifier = modifier
                    .fillMaxWidth()
                    .heightIn(min = minHeight, max = dialogMaxHeight)
                    .imePadding()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                style = style,
                tonePalette = tonePalette,
                bodyMaxHeight = bodyMaxHeight,
                icon = icon,
                title = title,
                text = text,
                confirmButton = confirmButton,
                dismissButton = dismissButton,
                fillBody = fillBody
            )
        }
    }
}

@Composable
private fun AppDialogScaffoldContent(
    modifier: Modifier,
    style: AppBottomSheetStyle,
    tonePalette: SheetTonePalette,
    bodyMaxHeight: Dp,
    icon: (@Composable () -> Unit)?,
    title: (@Composable () -> Unit)?,
    text: (@Composable () -> Unit)?,
    confirmButton: (@Composable () -> Unit)?,
    dismissButton: (@Composable () -> Unit)?,
    fillBody: Boolean = false
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (icon != null || title != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon != null) {
                    Surface(
                        shape = CircleShape,
                        color = tonePalette.iconContainer
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CompositionLocalProvider(LocalContentColor provides tonePalette.iconTint) {
                                icon()
                            }
                        }
                    }
                }
                if (title != null) {
                    Box(modifier = Modifier.weight(1f)) {
                        title()
                    }
                }
            }

            Box(
                modifier = Modifier
                    .height(2.dp)
                    .widthIn(max = 120.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(tonePalette.accent.copy(alpha = 0.22f))
            )
        }

        if (text != null) {
            val contentModifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = fillBody)
                .heightIn(max = bodyMaxHeight)

            when (style) {
                AppBottomSheetStyle.Form -> {
                    Column(
                        modifier = contentModifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        text()
                    }
                }

                AppBottomSheetStyle.Confirm -> {
                    Box(
                        modifier = contentModifier
                    ) {
                        text()
                    }
                }

                AppBottomSheetStyle.Selection -> {
                    Surface(
                        modifier = contentModifier,
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            text()
                        }
                    }
                }

                AppBottomSheetStyle.Status -> {
                    Surface(
                        modifier = contentModifier,
                        shape = RoundedCornerShape(18.dp),
                        color = tonePalette.statusTint.copy(alpha = 0.46f),
                        border = BorderStroke(
                            1.dp,
                            tonePalette.accent.copy(alpha = 0.22f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            text()
                        }
                    }
                }
            }
        }

        if (dismissButton != null || confirmButton != null) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (dismissButton != null) {
                    dismissButton()
                }
                if (confirmButton != null) {
                    confirmButton()
                }
            }
        }
    }
}
