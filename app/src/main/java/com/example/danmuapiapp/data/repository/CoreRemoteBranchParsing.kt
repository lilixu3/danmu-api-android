package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.normalizeGithubBranch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Pure parsing for GitHub branch metadata and HTML fallbacks. */
internal object CoreRemoteBranchParsing {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseDefaultBranch(body: String): String? {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject
            ?: return null
        return (root["default_branch"] as? JsonPrimitive)?.contentOrNull
            ?.let(::normalizeGithubBranch)
            ?.takeIf { it.isNotBlank() }
    }

    fun parseDefaultBranchFromHtml(body: String): String? {
        return Regex("""[\"']defaultBranch[\"']\s*:\s*[\"']([^\"']+)[\"']""")
            .find(body)
            ?.groupValues
            ?.getOrNull(1)
            ?.let(::normalizeGithubBranch)
            ?.takeIf { it.isNotBlank() }
    }

    fun parseBranchList(body: String): List<String>? {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonArray
            ?: return null
        return root.mapNotNull { element ->
            ((element as? JsonObject)?.get("name") as? JsonPrimitive)?.contentOrNull?.trim()
                ?.takeIf { it.isNotBlank() }
        }
    }

    fun parseBranchListFromHtml(repo: String, body: String): List<String>? {
        val escapedRepo = Regex.escape(repo)
        val branchRegex = Regex("""href=["']/""" + escapedRepo + """/tree/([^"'?#]+)["']""")
        return branchRegex.findAll(body)
            .mapNotNull { match ->
                match.groupValues.getOrNull(1)
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
            }
            .toList()
            .distinct()
            .takeIf { it.isNotEmpty() }
    }

    fun parseBranchHead(body: String): BranchHeadInfo? {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject
            ?: return null
        val sha = (root["sha"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        if (sha.isBlank()) return null
        val commitObj = root["commit"] as? JsonObject
        val message = (commitObj?.get("message") as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        val title = message.lineSequence().firstOrNull()?.trim().orEmpty().ifBlank { "提交 ${sha.take(7)}" }
        val authorObj = commitObj?.get("author") as? JsonObject
        val publishedAt = (authorObj?.get("date") as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        val accountObj = root["author"] as? JsonObject
        val author = (authorObj?.get("name") as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            .ifBlank {
                (accountObj?.get("login") as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            }
        return BranchHeadInfo(
            sha = sha,
            publishedAt = publishedAt,
            title = title,
            message = message,
            author = author.ifBlank { "未知作者" },
            htmlUrl = (root["html_url"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        )
    }

    fun branchCacheKey(repo: String, branch: String): String =
        "${repo.lowercase()}:${branch.lowercase()}"
}
