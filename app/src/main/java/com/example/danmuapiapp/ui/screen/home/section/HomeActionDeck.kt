package com.example.danmuapiapp.ui.screen.home

import com.example.danmuapiapp.ui.component.AppGlassSurface
import com.example.danmuapiapp.ui.component.liquid.AppLiquidButton
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.domain.model.ServiceStatus
import com.example.danmuapiapp.ui.component.GradientButton
import com.example.danmuapiapp.ui.screen.home.support.resolveCoreActionButtonText
import com.example.danmuapiapp.ui.theme.appDangerTonalButtonColors
import com.example.danmuapiapp.ui.theme.appTonalButtonColors
import com.example.danmuapiapp.ui.theme.LocalGlassMaterial

@Composable
internal fun ActionDeck(
    status: ServiceStatus,
    isRunning: Boolean,
    isTransitioning: Boolean,
    isStarting: Boolean,
    isInstalling: Boolean,
    isSwitching: Boolean,
    isUpdating: Boolean,
    isCoreInfoLoading: Boolean,
    isDarkTheme: Boolean,
    onToggle: () -> Unit,
    onRestart: () -> Unit,
    onOpenVariantPicker: () -> Unit,
    onOpenCoreDownload: () -> Unit,
    onOpenUpdatePrompt: () -> Unit,
    isCoreInstalled: Boolean,
    hasVersionUpdate: Boolean,
    sourceMismatch: Boolean,
    sourceUnknownLegacy: Boolean,
    coreOperationMessage: String?
) {
    val glassEnabled = LocalGlassMaterial.current.enabled
    val isStopping = status == ServiceStatus.Stopping
    val coreActionEnabled = !isTransitioning && !isCoreInfoLoading &&
        (!isCoreInstalled || sourceMismatch || sourceUnknownLegacy || hasVersionUpdate)
    val coreActionText = resolveCoreActionButtonText(
        isCoreInfoLoading = isCoreInfoLoading,
        isCoreInstalled = isCoreInstalled,
        hasVersionUpdate = hasVersionUpdate,
        sourceMismatch = sourceMismatch,
        sourceUnknownLegacy = sourceUnknownLegacy,
        isInstalling = isInstalling,
        isUpdating = isUpdating
    )
    val serviceButtonText = when {
        isStarting && coreOperationMessage.isNullOrBlank() -> "取消启动"
        isStarting -> "启动中"
        isStopping -> "停止中"
        isRunning -> "停止服务"
        else -> "启动服务"
    }
    val serviceButtonEnabled = when {
        isStopping -> false
        isStarting && coreOperationMessage.isNullOrBlank() -> true
        else -> !isTransitioning
    }
    val serviceActionIsStop = isRunning || isStopping || (isStarting && coreOperationMessage.isNullOrBlank())

    AppGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDarkTheme) 0.34f else 0.24f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GradientButton(
                    text = serviceButtonText,
                    onClick = onToggle,
                    enabled = serviceButtonEnabled,
                    modifier = Modifier.weight(1f),
                    colors = if (serviceActionIsStop) {
                        if (isDarkTheme) {
                            listOf(Color(0xFFDC2626), Color(0xFFEA580C))
                        } else {
                            listOf(Color(0xFFD63E2F), Color(0xFFF06A3A))
                        }
                    } else {
                        if (isDarkTheme) {
                            listOf(Color(0xFF2563EB), Color(0xFF1D4ED8))
                        } else {
                            listOf(Color(0xFF1E88E5), Color(0xFF4457D2))
                        }
                    },
                    disabledColors = if (isStarting) {
                        if (isDarkTheme) {
                            listOf(Color(0xFF1A3A5C), Color(0xFF264064))
                        } else {
                            listOf(Color(0xFF899FC4), Color(0xFF9097C9))
                        }
                    } else {
                        listOf(
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    }
                )
                AnimatedVisibility(
                    visible = isRunning,
                    enter = expandHorizontally() + fadeIn(),
                    exit = shrinkHorizontally() + fadeOut()
                ) {
                    val restartEnabled = !isTransitioning
                    val restartContent: @Composable RowScope.() -> Unit = {
                        Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("重启")
                    }
                    if (glassEnabled) {
                        AppLiquidButton(
                            onClick = onRestart,
                            enabled = restartEnabled,
                            height = 52.dp,
                            surfaceColor = if (restartEnabled) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.42f)
                            },
                            contentColor = if (restartEnabled) {
                                if (isDarkTheme) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                            },
                            content = restartContent
                        )
                    } else {
                        FilledTonalButton(
                            onClick = onRestart,
                            enabled = restartEnabled,
                            modifier = Modifier.height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (isDarkTheme) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.98f)
                                },
                                contentColor = if (isDarkTheme) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                },
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                            ),
                            content = restartContent
                        )
                    }
                }
            }

            if (!coreOperationMessage.isNullOrBlank()) {
                Text(
                    text = coreOperationMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val variantPickerContent: @Composable RowScope.() -> Unit = {
                    Icon(Icons.Rounded.SwapHoriz, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "切换核心",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                val dangerousCoreAction = !isCoreInfoLoading &&
                    (!isCoreInstalled || sourceMismatch || sourceUnknownLegacy || hasVersionUpdate)
                if (glassEnabled) {
                    AppLiquidButton(
                        onClick = onOpenVariantPicker,
                        enabled = !isTransitioning,
                        modifier = Modifier.weight(1f),
                        surfaceColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.42f),
                        contentColor = if (isTransitioning) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        content = variantPickerContent
                    )
                } else {
                    OutlinedButton(
                        onClick = onOpenVariantPicker,
                        enabled = !isTransitioning,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        content = variantPickerContent
                    )
                }

                val coreActionContent: @Composable RowScope.() -> Unit = {
                    if (isInstalling || isUpdating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = when {
                                isCoreInfoLoading -> Icons.Rounded.HourglassTop
                                !isCoreInstalled -> Icons.Rounded.Download
                                else -> Icons.Rounded.SystemUpdateAlt
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        coreActionText,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                if (glassEnabled) {
                    AppLiquidButton(
                        onClick = {
                            if (!isCoreInstalled) {
                                onOpenCoreDownload()
                            } else {
                                onOpenUpdatePrompt()
                            }
                        },
                        enabled = coreActionEnabled,
                        modifier = Modifier.weight(1f),
                        surfaceColor = if (!coreActionEnabled) {
                            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.42f)
                        } else if (dangerousCoreAction) {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.56f)
                        } else {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f)
                        },
                        contentColor = if (!coreActionEnabled) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        } else if (dangerousCoreAction) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        content = coreActionContent
                    )
                } else {
                    FilledTonalButton(
                        onClick = {
                            if (!isCoreInstalled) {
                                onOpenCoreDownload()
                            } else {
                                onOpenUpdatePrompt()
                            }
                        },
                        enabled = coreActionEnabled,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = if (dangerousCoreAction) {
                            appDangerTonalButtonColors()
                        } else {
                            appTonalButtonColors()
                        },
                        content = coreActionContent
                    )
                }
            }
        }
    }
}

