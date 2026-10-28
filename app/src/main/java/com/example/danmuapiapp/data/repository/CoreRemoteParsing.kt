package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.data.remote.github.normalizeGithubUrl
import com.example.danmuapiapp.domain.model.CoreSourceStatus
import com.example.danmuapiapp.domain.model.GithubRelease
import com.example.danmuapiapp.domain.model.normalizeGithubBranch
import com.example.danmuapiapp.domain.model.normalizeGithubRepo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** Parses GitHub release, commit and tag responses without touching the repository state. */
internal object CoreRemoteParsing {
    private val json = Json { ignoreUnknownKeys = true }

    data class AvailableVersionLabel(
        val version: String = "",
        val commitSha: String = ""
    )

    fun parseRelease(jsonStr: String, onError: (Throwable) -> Unit = {}): GithubRelease? {
        return runCatching {
            val obj = json.parseToJsonElement(jsonStr).jsonObject
            GithubRelease(
                tagName = (obj["tag_name"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                name = (obj["name"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                body = (obj["body"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                publishedAt = (obj["published_at"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                zipballUrl = normalizeGithubUrl((obj["zipball_url"] as? JsonPrimitive)?.contentOrNull.orEmpty())
            )
        }.onFailure(onError).getOrNull()
    }

    fun parseReleaseArray(body: String): List<GithubRelease>? {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonArray
            ?: return null
        return root.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            parseRelease(obj.toString())
        }.takeIf { it.isNotEmpty() }
    }

    fun parseCommitAsRelease(repo: String, obj: JsonObject): GithubRelease? {
        val sha = (obj["sha"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        if (sha.isBlank()) return null

        val shortSha = sha.take(7)
        val commitObj = obj["commit"] as? JsonObject
        val message = (commitObj?.get("message") as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        val title = message.lineSequence().firstOrNull()?.trim().orEmpty().ifBlank { "提交 $shortSha" }
        val authorObj = commitObj?.get("author") as? JsonObject
        val publishedAt = (authorObj?.get("date") as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()

        return GithubRelease(
            tagName = shortSha,
            name = title,
            body = message,
            publishedAt = publishedAt,
            zipballUrl = "https://api.github.com/repos/$repo/zipball/$sha"
        )
    }

    fun parseCommitArray(repo: String, body: String): List<GithubRelease>? {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonArray
            ?: return null
        return root.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            parseCommitAsRelease(repo, obj)
        }.takeIf { it.isNotEmpty() }
    }

    fun parseCommitAtom(repo: String, xmlText: String): List<GithubRelease> {
        val entryRegex = Regex("(?s)<entry>(.*?)</entry>")
        return entryRegex.findAll(xmlText).mapNotNull { match ->
            val entry = match.groupValues.getOrNull(1).orEmpty()
            val sha = extractCommitSha(entry) ?: return@mapNotNull null
            val shortSha = sha.take(7)

            val rawTitle = extractXmlTag(entry, "title")
            val title = decodeXmlEntities(rawTitle).lineSequence()
                .firstOrNull()
                ?.trim()
                .orEmpty()
                .ifBlank { "提交 $shortSha" }
            val publishedAt = decodeXmlEntities(extractXmlTag(entry, "updated")).trim()

            GithubRelease(
                tagName = shortSha,
                name = title,
                body = title,
                publishedAt = publishedAt,
                zipballUrl = "https://api.github.com/repos/$repo/zipball/$sha"
            )
        }.toList()
    }

    fun extractCommitSha(entryXml: String): String? {
        val linkRegex = Regex("""href=["']https://github\.com/[^"']+/commit/([0-9a-fA-F]{7,40})["']""")
        val fromLink = linkRegex.find(entryXml)?.groupValues?.getOrNull(1)?.trim()
        if (!fromLink.isNullOrBlank()) return fromLink

        val idRegex = Regex("""<id>[^<]*/commit/([0-9a-fA-F]{7,40})</id>""")
        val fromId = idRegex.find(entryXml)?.groupValues?.getOrNull(1)?.trim()
        if (!fromId.isNullOrBlank()) return fromId

        return null
    }

    fun extractXmlTag(xmlText: String, tag: String): String {
        val regex = Regex("(?s)<$tag>(.*?)</$tag>")
        return regex.find(xmlText)?.groupValues?.getOrNull(1).orEmpty()
    }

    fun decodeXmlEntities(text: String): String {
        return text
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&amp;", "&")
    }

    fun buildLatestVersionLabel(versionLabel: String, commitSha: String): String {
        val normalizedVersion = versionLabel.trim()
        val shortSha = commitSha.trim().takeIf { it.isNotBlank() }?.take(7).orEmpty()
        return when {
            normalizedVersion.isNotBlank() && shortSha.isNotBlank() -> "$normalizedVersion@$shortSha"
            normalizedVersion.isNotBlank() -> normalizedVersion
            else -> ""
        }
    }

    fun parseAvailableVersionLabel(value: String?): AvailableVersionLabel {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isBlank()) return AvailableVersionLabel()
        if ('@' !in trimmed && trimmed.matches(Regex("^[0-9a-fA-F]{7,40}$"))) {
            return AvailableVersionLabel(commitSha = trimmed)
        }
        val version = trimmed.substringBefore('@').trim()
        val commitSha = trimmed.substringAfter('@', "").trim()
        return AvailableVersionLabel(
            version = version.removePrefix("v").trim(),
            commitSha = commitSha
        )
    }

    fun commitShasEquivalent(left: String, right: String): Boolean {
        val normalizedLeft = left.trim().lowercase()
        val normalizedRight = right.trim().lowercase()
        if (normalizedLeft.isBlank() || normalizedRight.isBlank()) return false
        return normalizedLeft == normalizedRight ||
            normalizedLeft.startsWith(normalizedRight) ||
            normalizedRight.startsWith(normalizedLeft)
    }

    fun extractArchiveCommitSha(zipUrl: String): String? {
        val match = Regex("""/(?:archive|zipball)/([0-9a-fA-F]{7,40})(?:\.zip)?""").find(zipUrl)
        return match?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    fun resolveCoreSourceStatus(
        localMetadata: CoreSourceMetadata?,
        desiredRepo: String,
        desiredBranch: String?
    ): CoreSourceStatus {
        if (desiredRepo.isBlank()) return CoreSourceStatus.NotApplicable
        if (localMetadata == null) return CoreSourceStatus.UnknownLegacy

        val localRepo = normalizeGithubRepo(localMetadata.repo)
        val localBranch = normalizeGithubBranch(localMetadata.branch)
        val targetBranch = normalizeGithubBranch(desiredBranch)
        return when {
            localRepo.isBlank() -> CoreSourceStatus.UnknownLegacy
            !localRepo.equals(desiredRepo, ignoreCase = true) -> CoreSourceStatus.Mismatched
            targetBranch.isBlank() && localBranch.isBlank() -> CoreSourceStatus.UnknownLegacy
            targetBranch.isBlank() -> CoreSourceStatus.Matched
            localBranch.isBlank() -> CoreSourceStatus.Mismatched
            !branchesEquivalent(localBranch, targetBranch) -> CoreSourceStatus.Mismatched
            else -> CoreSourceStatus.Matched
        }
    }

    fun branchesEquivalent(localBranch: String, desiredBranch: String): Boolean {
        if (localBranch.equals(desiredBranch, ignoreCase = true)) return true
        if (!desiredBranch.contains('/')) {
            return localBranch.substringAfterLast('/').equals(desiredBranch, ignoreCase = true) ||
                localBranch.endsWith("/$desiredBranch", ignoreCase = true)
        }
        return false
    }
}
