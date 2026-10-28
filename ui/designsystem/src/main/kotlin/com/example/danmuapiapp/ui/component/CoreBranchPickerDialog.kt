package com.example.danmuapiapp.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.domain.model.CoreBranchCatalog
import com.example.danmuapiapp.ui.component.liquid.AppGlassButton

@Composable
fun CoreBranchPickerDialog(
    variantLabel: String,
    catalog: CoreBranchCatalog?,
    currentBranch: String,
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val initialBranch = catalog?.branches
        ?.firstOrNull { it.equals(currentBranch, ignoreCase = true) }
        ?: catalog?.defaultBranch.orEmpty()
    var selectedBranch by remember(catalog?.repo, currentBranch, initialBranch) {
        mutableStateOf(initialBranch)
    }

    AppDialog(
        onDismissRequest = onDismiss,
        style = AppDialogStyle.Selection,
        tone = AppDialogTone.Brand,
        scrollContent = false,
        icon = { Icon(Icons.Rounded.AccountTree, null) },
        title = { Text("切换分支") },
        supportingText = {
            Text(
                catalog?.repo?.ifBlank { variantLabel } ?: variantLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            when {
                isLoading -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                    }
                }

                errorMessage != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 150.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        AppGlassButton(onClick = onRetry) {
                            Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                            Text("重新读取", modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }

                catalog != null -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(catalog.branches, key = { it }) { branch ->
                            val selected = branch.equals(selectedBranch, ignoreCase = true)
                            AppDialogOption(
                                selected = selected,
                                onClick = { selectedBranch = branch },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selected,
                                        onClick = { selectedBranch = branch }
                                    )
                                    Text(
                                        text = branch,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (branch.equals(catalog.defaultBranch, ignoreCase = true)) {
                                        Text(
                                            text = "默认",
                                            modifier = Modifier.padding(start = 8.dp, end = 5.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (selected) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.primary
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            AppGlassButton(
                onClick = { onConfirm(selectedBranch) },
                enabled = !isLoading && errorMessage == null && selectedBranch.isNotBlank()
            ) {
                Text("切换并重装")
            }
        },
        dismissButton = {
            AppGlassButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
