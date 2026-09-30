package com.example.danmuapiapp.data.service

import com.example.danmuapiapp.domain.model.CorePullRequestInclusion
import kotlinx.serialization.json.*

internal data class PullRequestCommitComparison(
    val inclusion: CorePullRequestInclusion,
    val changedPaths: Set<String> = emptySet(),
    val completeFiles: Boolean = false
) {
    companion object {
        fun parse(body: String): PullRequestCommitComparison? {
            val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
            val inclusion = PullRequestCurrentCorePolicy.fromCompareStatus((root["status"] as? JsonPrimitive)?.contentOrNull)
            val files = root["files"] as? JsonArray
            val paths = mutableSetOf<String>()
            var complete = files != null && files.size < 300 // GitHub compare 最多返回 300 个文件。
            files.orEmpty().forEach { item ->
                val file = item as? JsonObject
                val path = (file?.get("filename") as? JsonPrimitive)?.contentOrNull
                if (path.isNullOrBlank()) complete = false else paths += path
                (file?.get("previous_filename") as? JsonPrimitive)?.contentOrNull
                    ?.takeIf { it.isNotBlank() }?.let(paths::add)
            }
            return PullRequestCommitComparison(inclusion, paths, complete)
        }
    }
}

internal data class PullRequestUpdateCoverage(val inclusion: CorePullRequestInclusion, val reason: String = "")

/** 用实际安装的 PR head 核验，不能用 PR 编号或 merged 状态代替用户已安装的提交。 */
internal object PullRequestUpdateCoveragePolicy {
    private val fullSha = Regex("^[0-9a-fA-F]{40}$")

    suspend fun evaluate(
        installedHead: String,
        installedBase: String,
        currentHead: String,
        mergedCommit: String?,
        target: String,
        compare: suspend (base: String, head: String) -> PullRequestCommitComparison?,
        currentPullRequestPaths: suspend () -> Set<String>?
    ): PullRequestUpdateCoverage {
        fun unknown(reason: String) = PullRequestUpdateCoverage(CorePullRequestInclusion.Unknown, reason)
        if (!fullSha.matches(installedHead) || !fullSha.matches(currentHead) || !fullSha.matches(target)) {
            return unknown("缺少完整提交记录，无法确认已包含用户并入的版本")
        }
        val comparisons = mutableMapOf<Pair<String, String>, PullRequestCommitComparison?>()
        suspend fun comparison(base: String, head: String): PullRequestCommitComparison? {
            if (base.equals(head, true)) return PullRequestCommitComparison(CorePullRequestInclusion.Included, completeFiles = true)
            val key = base.lowercase() to head.lowercase()
            if (!comparisons.containsKey(key)) comparisons[key] = compare(base, head)
            return comparisons[key]
        }

        suspend fun checkHead(head: String): PullRequestUpdateCoverage {
            val isCurrentHead = head.equals(currentHead, true)
            var witness = head
            var evidence = comparison(head, target)
            var incompleteLookup = evidence == null || evidence.inclusion == CorePullRequestInclusion.Unknown
            // squash / rebase 的合入 SHA 只为同一个 PR head 作证，不能替强推前的旧 head 作证。
            if (evidence?.inclusion != CorePullRequestInclusion.Included && isCurrentHead &&
                mergedCommit != null && fullSha.matches(mergedCommit)) {
                witness = mergedCommit
                evidence = comparison(mergedCommit, target)
                incompleteLookup = incompleteLookup || evidence == null || evidence.inclusion == CorePullRequestInclusion.Unknown
            }
            if (evidence?.inclusion != CorePullRequestInclusion.Included) {
                return when {
                    !isCurrentHead -> unknown("PR 后续已变化，无法确认本地并入的旧提交被完整保留")
                    incompleteLookup -> unknown("提交关系查询不完整，无法确认是否包含")
                    evidence?.inclusion == CorePullRequestInclusion.NotIncluded ->
                        PullRequestUpdateCoverage(CorePullRequestInclusion.NotIncluded,
                            "目标历史未包含此 PR 提交；手动移植的等效改动仍需人工核对")
                    else -> unknown("提交关系查询不完整，无法确认是否包含")
                }
            }
            if (witness.equals(target, true)) return PullRequestUpdateCoverage(CorePullRequestInclusion.Included)
            if (!evidence.completeFiles) return unknown("文件差异不完整，无法排除合入后的修改或回退")
            if (evidence.changedPaths.isEmpty()) return PullRequestUpdateCoverage(CorePullRequestInclusion.Included)
            val prPaths = if (isCurrentHead) currentPullRequestPaths() else {
                if (!fullSha.matches(installedBase)) return unknown("缺少原始基线，无法核验旧 PR 的文件")
                comparison(installedBase, head)?.takeIf { it.completeFiles }?.changedPaths
            }
            if (prPaths.isNullOrEmpty()) return unknown("缺少完整 PR 文件记录，无法排除改动已被回退")
            if (prPaths.any { it in evidence.changedPaths }) {
                return unknown("PR 虽在提交历史中，但相关文件有后续修改或回退，不能确认完整保留")
            }
            return PullRequestUpdateCoverage(CorePullRequestInclusion.Included)
        }

        val installed = checkHead(installedHead)
        if (installed.inclusion != CorePullRequestInclusion.Included) return installed
        return if (installedHead.equals(currentHead, true)) installed else checkHead(currentHead)
    }
}
