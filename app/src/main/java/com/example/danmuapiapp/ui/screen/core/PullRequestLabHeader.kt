package com.example.danmuapiapp.ui.screen.core

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.domain.model.CorePullRequestFilter
import com.example.danmuapiapp.ui.component.liquid.AppGlassIconButton

@Composable
internal fun PullRequestLabHeader(
    title: String,
    subtitle: String,
    isLoading: Boolean,
    canRefresh: Boolean,
    searchActive: Boolean,
    onBack: () -> Unit,
    onSearchToggle: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppGlassIconButton(onClick = onBack, size = 36.dp) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", Modifier.size(18.dp))
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AppGlassIconButton(
                onClick = onSearchToggle,
                enabled = canRefresh,
                size = 36.dp
            ) {
                Icon(
                    if (searchActive) Icons.Rounded.Close else Icons.Rounded.Search,
                    if (searchActive) "关闭搜索" else "搜索 PR",
                    Modifier.size(18.dp)
                )
            }
            AppGlassIconButton(
                onClick = onRefresh,
                enabled = canRefresh && !isLoading,
                size = 36.dp
            ) {
                if (isLoading) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.Refresh, "刷新", Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
internal fun PullRequestSearchBar(
    query: String,
    filter: CorePullRequestFilter,
    isLoading: Boolean,
    enabled: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        enabled = enabled,
        singleLine = true,
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (query.isNotBlank()) {
                    AppGlassIconButton(
                        onClick = onClear,
                        enabled = !isLoading,
                        size = 32.dp
                    ) {
                        Icon(Icons.Rounded.Close, "清空搜索")
                    }
                }
                AppGlassIconButton(
                    onClick = onSearch,
                    enabled = !isLoading && query.isNotBlank(),
                    size = 32.dp
                ) {
                    Icon(Icons.Rounded.Search, "执行 GitHub PR 搜索")
                }
            }
        },
        placeholder = { Text(filter.searchPlaceholder) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) onSearch() }),
        shape = RoundedCornerShape(8.dp)
    )
}

@Composable
internal fun PullRequestFilterSelector(
    selected: CorePullRequestFilter,
    enabled: Boolean,
    onSelected: (CorePullRequestFilter) -> Unit
) {
    val filters = CorePullRequestFilter.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        filters.forEachIndexed { index, filter ->
            SegmentedButton(
                selected = selected == filter,
                onClick = { onSelected(filter) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index = index, count = filters.size),
                label = {
                    Text(
                        text = filter.shortLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Clip
                    )
                }
            )
        }
    }
}
