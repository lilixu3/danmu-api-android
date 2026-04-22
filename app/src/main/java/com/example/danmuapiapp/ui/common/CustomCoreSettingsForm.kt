package com.example.danmuapiapp.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.domain.model.ResolvedCustomCoreSource
import com.example.danmuapiapp.domain.model.normalizeGithubBranch
import com.example.danmuapiapp.domain.model.resolveCustomCoreSource

data class CustomCoreSettingsInput(
    val displayName: String = "",
    val repo: String = "",
    val branch: String = ""
)

@Stable
class CustomCoreSettingsFormState internal constructor(
    initialDisplayName: String,
    initialRepo: String,
    initialBranch: String
) {
    private val baselineDisplayName = initialDisplayName.trim()
    private val baselineSource = resolveCustomCoreSource(initialRepo, initialBranch)

    var displayNameText by mutableStateOf(initialDisplayName)
    var repoText by mutableStateOf(initialRepo)
    var branchText by mutableStateOf(initialBranch)
    var branchEditedManually by mutableStateOf(false)
    internal var lastSuggestedBranch by mutableStateOf(baselineSource.suggestedBranch)

    val resolvedSource: ResolvedCustomCoreSource
        get() = resolveCustomCoreSource(repoText, branchText)

    val normalizedRepoPreview: String
        get() = resolvedSource.repo

    val normalizedBranchPreview: String
        get() = resolvedSource.branch

    val canSaveConfig: Boolean
        get() = repoText.trim().isBlank() || resolvedSource.isValidRepo

    val canInstall: Boolean
        get() = resolvedSource.isValidRepo

    val isDirty: Boolean
        get() = displayNameText.trim() != baselineDisplayName ||
            resolvedSource.repo != baselineSource.repo ||
            resolvedSource.branch != baselineSource.branch

    fun updateDisplayName(value: String) {
        displayNameText = value
    }

    fun updateRepo(value: String) {
        repoText = value
    }

    fun updateBranch(value: String) {
        branchEditedManually = true
        branchText = value
    }

    fun toInput(): CustomCoreSettingsInput {
        return CustomCoreSettingsInput(
            displayName = displayNameText.trim(),
            repo = repoText.trim(),
            branch = branchText.trim()
        )
    }
}

@Composable
fun rememberCustomCoreSettingsFormState(
    initialDisplayName: String,
    initialRepo: String,
    initialBranch: String
): CustomCoreSettingsFormState {
    val state = remember(initialDisplayName, initialRepo, initialBranch) {
        CustomCoreSettingsFormState(
            initialDisplayName = initialDisplayName,
            initialRepo = initialRepo,
            initialBranch = initialBranch
        )
    }
    val suggestedBranch = remember(state.repoText) {
        resolveCustomCoreSource(state.repoText, "").suggestedBranch
    }

    LaunchedEffect(state.repoText, state.branchEditedManually, suggestedBranch) {
        if (state.branchEditedManually) return@LaunchedEffect
        val normalizedCurrentBranch = normalizeGithubBranch(state.branchText)
        val canAutoReplace = normalizedCurrentBranch.isBlank() ||
            normalizedCurrentBranch == normalizeGithubBranch(state.lastSuggestedBranch)
        if (canAutoReplace && state.branchText != suggestedBranch) {
            state.branchText = suggestedBranch
        }
        state.lastSuggestedBranch = suggestedBranch
    }

    return state
}

@Composable
fun CustomCoreSettingsForm(
    state: CustomCoreSettingsFormState,
    modifier: Modifier = Modifier,
    displayNamePlaceholder: String = "自定义版"
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "支持 owner/repo、仓库首页链接，以及带 /tree/<branch> 的 GitHub 链接；文件页链接不会自动识别分支。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "支持格式",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "• lilixu3/danmu_api\n• https://github.com/lilixu3/danmu_api\n• https://github.com/lilixu3/danmu_api/tree/main",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        OutlinedTextField(
            value = state.displayNameText,
            onValueChange = state::updateDisplayName,
            label = { Text("显示名称") },
            placeholder = { Text(displayNamePlaceholder) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.repoText,
            onValueChange = state::updateRepo,
            label = { Text("仓库地址") },
            placeholder = { Text("owner/repo 或 GitHub 链接") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            supportingText = {
                Text("例如：https://github.com/Celestials316/danmu_api/tree/douban-cookie-hardening")
            },
            isError = state.repoText.isNotBlank() && !state.resolvedSource.isValidRepo
        )
        OutlinedTextField(
            value = state.branchText,
            onValueChange = state::updateBranch,
            label = { Text("分支") },
            placeholder = { Text("留空=默认 main") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            supportingText = {
                Text("留空时：优先使用链接里的分支；否则默认 main")
            }
        )
        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "解析预览",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "仓库：${state.normalizedRepoPreview.ifBlank { "未填写" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "分支：${state.normalizedBranchPreview.ifBlank { "--" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
