package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.CoreRemoteCommit
import com.example.danmuapiapp.domain.model.CoreRevisionFileChange
import com.example.danmuapiapp.domain.model.CoreUpdateComparison
import com.example.danmuapiapp.domain.model.CoreUpdateRelation
import com.example.danmuapiapp.domain.model.CoreUpdateSummary
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

internal object CoreUpdateComparisonParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(
        raw: String,
        repo: String,
        branch: String,
        localCommitSha: String,
        fallbackRemoteCommit: CoreRemoteCommit
    ): CoreUpdateComparison? {
        val root = runCatching { json.parseToJsonElement(raw) }.getOrNull() as? JsonObject
            ?: return null
        val relation = relationFromStatus(root.string("status"))
        val commits = (root["commits"] as? JsonArray).orEmpty().mapNotNull(::parseCommit)
        val remoteCommit = commits.lastOrNull()
            ?.takeIf { commitsEquivalent(it.sha, fallbackRemoteCommit.sha) }
            ?: fallbackRemoteCommit
        val files = (root["files"] as? JsonArray).orEmpty().mapNotNull(::parseFile)
        val aheadBy = root.int("ahead_by")
        val behindBy = root.int("behind_by")
        val totalCommits = root.int("total_commits").takeIf { it > 0 } ?: commits.size
        val additions = files.sumOf { it.additions }
        val deletions = files.sumOf { it.deletions }

        return CoreUpdateComparison(
            repo = repo,
            branch = branch,
            localCommitSha = localCommitSha,
            remoteCommit = remoteCommit,
            relation = relation,
            aheadBy = aheadBy,
            behindBy = behindBy,
            totalCommits = totalCommits,
            commits = commits,
            files = files,
            additions = additions,
            deletions = deletions,
            changedFiles = files.size,
            summary = buildSummary(
                relation = relation,
                aheadBy = aheadBy,
                behindBy = behindBy,
                totalCommits = totalCommits,
                commits = commits,
                files = files
            ),
            isTruncated = totalCommits > commits.size || files.size >= 300
        )
    }

    fun identical(
        repo: String,
        branch: String,
        commit: CoreRemoteCommit
    ): CoreUpdateComparison = CoreUpdateComparison(
        repo = repo,
        branch = branch,
        localCommitSha = commit.sha,
        remoteCommit = commit,
        relation = CoreUpdateRelation.Identical,
        aheadBy = 0,
        behindBy = 0,
        totalCommits = 0,
        commits = emptyList(),
        files = emptyList(),
        additions = 0,
        deletions = 0,
        changedFiles = 0,
        summary = CoreUpdateSummary("已安装提交与远程最新提交一致")
    )

    private fun parseCommit(element: kotlinx.serialization.json.JsonElement): CoreRemoteCommit? {
        val root = element as? JsonObject ?: return null
        val sha = root.string("sha")
        if (sha.isBlank()) return null
        val commit = root["commit"] as? JsonObject
        val message = commit?.string("message").orEmpty().trim()
        val author = commit?.get("author") as? JsonObject
        val account = root["author"] as? JsonObject
        return CoreRemoteCommit(
            sha = sha,
            title = message.lineSequence().firstOrNull()?.trim().orEmpty()
                .ifBlank { "提交 ${sha.take(7)}" },
            message = message,
            author = author?.string("name").orEmpty()
                .ifBlank { account?.string("login").orEmpty() }
                .ifBlank { "未知作者" },
            committedAt = author?.string("date").orEmpty(),
            htmlUrl = root.string("html_url")
        )
    }

    private fun parseFile(element: kotlinx.serialization.json.JsonElement): CoreRevisionFileChange? {
        val file = element as? JsonObject ?: return null
        val path = file.string("filename")
        if (path.isBlank()) return null
        val status = file.string("status").ifBlank { "modified" }
        val patch = file.string("patch")
        return CoreRevisionFileChange(
            path = path,
            previousPath = file.string("previous_filename").ifBlank { null },
            status = status,
            additions = file.int("additions"),
            deletions = file.int("deletions"),
            changes = file.int("changes"),
            lines = CoreRevisionParser.parsePatch(patch),
            patchUnavailableReason = if (patch.isBlank()) {
                "GitHub 未返回文本补丁，文件可能是二进制或变动过大"
            } else {
                null
            }
        )
    }

    private fun buildSummary(
        relation: CoreUpdateRelation,
        aheadBy: Int,
        behindBy: Int,
        totalCommits: Int,
        commits: List<CoreRemoteCommit>,
        files: List<CoreRevisionFileChange>
    ): CoreUpdateSummary {
        val headline = when (relation) {
            CoreUpdateRelation.Identical -> "已安装提交与远程最新提交一致"
            CoreUpdateRelation.RemoteAhead ->
                "远程领先 $aheadBy 个提交，涉及 ${files.size} 个文件"
            CoreUpdateRelation.LocalAhead ->
                "本地领先远程 $behindBy 个提交，远程没有更新"
            CoreUpdateRelation.Diverged ->
                "本地与远程已分叉，远程有 $aheadBy 个新提交"
            CoreUpdateRelation.Changed ->
                "远程提交已变化，涉及 ${files.size} 个文件"
            CoreUpdateRelation.Unknown ->
                "共 $totalCommits 个提交，涉及 ${files.size} 个文件"
        }
        val highlights = commits.asSequence()
            .map { it.title.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(5)
            .toList()
        return CoreUpdateSummary(
            headline = headline,
            highlights = highlights,
            affectedAreas = summarizeAffectedAreas(files)
        )
    }

    private fun summarizeAffectedAreas(files: List<CoreRevisionFileChange>): List<String> {
        val counts = linkedMapOf(
            "核心代码" to 0,
            "配置" to 0,
            "依赖" to 0,
            "测试" to 0,
            "文档" to 0,
            "其他" to 0
        )
        files.forEach { file ->
            val path = file.path.lowercase()
            val area = when {
                path.endsWith("package.json") || path.contains("lock") ||
                    path.contains("node_modules") -> "依赖"
                path.contains("/config") || path.startsWith("config") ||
                    path.endsWith(".env") -> "配置"
                path.contains("test") || path.contains("spec") -> "测试"
                path.contains("/docs/") || path.startsWith("docs/") ||
                    path.substringAfterLast('/').startsWith("readme") ||
                    path.endsWith(".md") -> "文档"
                path.endsWith(".js") || path.endsWith(".ts") ||
                    path.endsWith(".mjs") || path.endsWith(".cjs") ||
                    path.endsWith(".json") -> "核心代码"
                else -> "其他"
            }
            counts[area] = counts.getValue(area) + 1
        }
        return counts.mapNotNull { (area, count) ->
            "$area $count 个文件".takeIf { count > 0 }
        }
    }

    private fun relationFromStatus(status: String): CoreUpdateRelation = when (status.lowercase()) {
        "identical" -> CoreUpdateRelation.Identical
        "ahead" -> CoreUpdateRelation.RemoteAhead
        "behind" -> CoreUpdateRelation.LocalAhead
        "diverged" -> CoreUpdateRelation.Diverged
        else -> CoreUpdateRelation.Unknown
    }

    private fun commitsEquivalent(left: String, right: String): Boolean {
        val normalizedLeft = left.trim().lowercase()
        val normalizedRight = right.trim().lowercase()
        return normalizedLeft.isNotBlank() && normalizedRight.isNotBlank() &&
            (normalizedLeft == normalizedRight ||
                normalizedLeft.startsWith(normalizedRight) ||
                normalizedRight.startsWith(normalizedLeft))
    }

    private fun JsonObject.string(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

    private fun JsonObject.int(key: String): Int =
        (this[key] as? JsonPrimitive)?.intOrNull ?: 0
}
