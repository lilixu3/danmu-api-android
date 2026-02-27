package com.example.danmuapiapp.ui.theme

import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

private const val DISABLED_CONTENT_ALPHA = 0.58f

@Composable
private fun disabledContainerColor() = MaterialTheme.colorScheme.surfaceContainerHighest

@Composable
private fun disabledContentColor() = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DISABLED_CONTENT_ALPHA)

@Composable
fun appPrimaryButtonColors(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = MaterialTheme.colorScheme.primary,
    contentColor = MaterialTheme.colorScheme.onPrimary,
    disabledContainerColor = disabledContainerColor(),
    disabledContentColor = disabledContentColor()
)

@Composable
fun appPrimaryIconButtonColors(): IconButtonColors = IconButtonDefaults.filledIconButtonColors(
    containerColor = MaterialTheme.colorScheme.primary,
    contentColor = MaterialTheme.colorScheme.onPrimary,
    disabledContainerColor = disabledContainerColor(),
    disabledContentColor = disabledContentColor()
)

@Composable
fun appTonalButtonColors(): ButtonColors = ButtonDefaults.filledTonalButtonColors(
    containerColor = MaterialTheme.colorScheme.primaryContainer,
    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    disabledContainerColor = disabledContainerColor(),
    disabledContentColor = disabledContentColor()
)

@Composable
fun appTonalIconButtonColors(): IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(
    containerColor = MaterialTheme.colorScheme.primaryContainer,
    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    disabledContainerColor = disabledContainerColor(),
    disabledContentColor = disabledContentColor()
)

@Composable
fun appDangerButtonColors(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = MaterialTheme.colorScheme.error,
    contentColor = MaterialTheme.colorScheme.onError,
    disabledContainerColor = disabledContainerColor(),
    disabledContentColor = disabledContentColor()
)

@Composable
fun appDangerTonalButtonColors(): ButtonColors = ButtonDefaults.filledTonalButtonColors(
    containerColor = MaterialTheme.colorScheme.errorContainer,
    contentColor = MaterialTheme.colorScheme.onErrorContainer,
    disabledContainerColor = disabledContainerColor(),
    disabledContentColor = disabledContentColor()
)
