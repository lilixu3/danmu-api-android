package com.example.danmuapiapp.ui.screen.core

import com.example.danmuapiapp.domain.model.CorePullRequestFilter
import com.example.danmuapiapp.domain.model.PullRequestFirstContribution
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val CorePullRequestFilter.shortLabel: String
    get() = when (this) {
        CorePullRequestFilter.Open -> "开放"
        CorePullRequestFilter.Merged -> "已合并"
        CorePullRequestFilter.Closed -> "已关闭"
        CorePullRequestFilter.All -> "全部"
    }

internal val CorePullRequestFilter.sectionTitle: String
    get() = when (this) {
        CorePullRequestFilter.Open -> "开放的 PR"
        CorePullRequestFilter.Merged -> "已合并的 PR"
        CorePullRequestFilter.Closed -> "已关闭的 PR"
        CorePullRequestFilter.All -> "全部 PR"
    }

internal val CorePullRequestFilter.emptyMessage: String
    get() = when (this) {
        CorePullRequestFilter.Open -> "没有开放的 PR"
        CorePullRequestFilter.Merged -> "没有已合并的 PR"
        CorePullRequestFilter.Closed -> "没有未合并而关闭的 PR"
        CorePullRequestFilter.All -> "仓库中还没有 PR"
    }

internal val CorePullRequestFilter.searchPlaceholder: String
    get() = when (this) {
        CorePullRequestFilter.Open -> "搜索开放的 PR、作者或 #编号"
        CorePullRequestFilter.Merged -> "搜索已合并的 PR、作者或 #编号"
        CorePullRequestFilter.Closed -> "搜索已关闭的 PR、作者或 #编号"
        CorePullRequestFilter.All -> "搜索全部 PR、作者或 #编号"
    }

internal val CorePullRequestFilter.searchLoadingMessage: String
    get() = when (this) {
        CorePullRequestFilter.Open -> "正在搜索开放的 PR"
        CorePullRequestFilter.Merged -> "正在搜索已合并的 PR"
        CorePullRequestFilter.Closed -> "正在搜索已关闭的 PR"
        CorePullRequestFilter.All -> "正在搜索全部 PR"
    }

internal val PullRequestFirstContribution.label: String
    get() = when (this) {
        PullRequestFirstContribution.Github -> "首次在 GitHub 贡献"
        PullRequestFirstContribution.Repository -> "首次向此仓库贡献"
    }

internal fun formatPullRequestTime(raw: String): String {
    if (raw.isBlank()) return "时间未知"
    return runCatching {
        val formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.getDefault())
        Instant.parse(raw).atZone(ZoneId.systemDefault()).format(formatter)
    }.getOrDefault(raw.take(10))
}
