package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.CoreUpdateComparison
import com.example.danmuapiapp.domain.model.CoreUpdateRelation

internal object CorePullRequestUpdatePolicy {
    // 即使 package.json 版本号未变，也优先按主分支基线提交判断更新。
    fun branchRelation(localSha: String, remoteSha: String): CoreUpdateRelation? {
        val local = localSha.trim().lowercase()
        val remote = remoteSha.trim().lowercase()
        if (local.isEmpty() || remote.isEmpty()) return null
        return if (local.startsWith(remote) || remote.startsWith(local)) CoreUpdateRelation.Identical
        else CoreUpdateRelation.Changed
    }

    fun changedHeads(installed: CoreSourceMetadata?, remoteHeads: Map<Int, String>): Map<Int, String> {
        if (installed == null) return emptyMap()
        return installed.pullRequestNumbers.mapIndexedNotNull { index, number ->
            val remote = remoteHeads[number]?.trim().orEmpty()
            val local = installed.pullRequestHeadShas.getOrNull(index)?.trim().orEmpty()
            if (remote.isNotBlank() && !remote.equals(local, ignoreCase = true)) number to remote else null
        }.toMap()
    }

    fun relation(baseRelation: CoreUpdateRelation, changedHeads: Map<Int, String>): CoreUpdateRelation =
        if (changedHeads.isNotEmpty() && !baseRelation.hasRemoteUpdate) CoreUpdateRelation.Changed else baseRelation

    fun comparison(base: CoreUpdateComparison, changedHeads: Map<Int, String>): CoreUpdateComparison {
        if (changedHeads.isEmpty()) return base
        val numbers = changedHeads.keys.joinToString("、") { "#$it" }
        return base.copy(
            relation = relation(base.relation, changedHeads),
            summary = base.summary.copy(
                headline = "已合并的 PR $numbers 有新提交",
                highlights = listOf(
                    "更新前会检查最新版是否包含这些 PR，未包含时可选择直接更新或更新后继续合并。",
                    "下方提交与文件列表展示主分支差异，不包含 PR 的独立差异。"
                ) + base.summary.highlights
            )
        )
    }
}
