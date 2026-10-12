package com.example.danmuapiapp.data.repository

import android.content.Context
import com.example.danmuapiapp.data.service.RuntimePaths
import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.CoreInfo
import com.example.danmuapiapp.domain.model.CoreBranchSnapshot
import com.example.danmuapiapp.domain.model.CoreRemoteCommit
import com.example.danmuapiapp.domain.model.RunMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** 只用于首次加载时的界面展示，不参与核心是否可启动、更新或删除的判断。 */
@Singleton
class CoreDisplayInfoCache @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("core_display_info", Context.MODE_PRIVATE)

    internal fun scopeFor(mode: RunMode = RuntimePaths.currentRunMode(context)): String {
        val directory = when (mode) {
            RunMode.Normal -> RuntimePaths.normalWorkDirIdentity(context)
            RunMode.Root -> RuntimePaths.rootBaseDir(context).absolutePath
        }
        return "${mode.key}:$directory"
    }

    internal fun read(): CoreDisplayInfoSnapshot? =
        CoreDisplayInfoCodec.decode(prefs.getString("snapshot", null), scopeFor())

    internal fun save(scope: String, info: List<CoreInfo>) {
        prefs.edit().putString("snapshot", CoreDisplayInfoCodec.encode(scope, info)).apply()
    }

    internal fun readRemoteSnapshots(): List<CachedCoreRemoteInfo> =
        CoreRemoteInfoCodec.decode(prefs.getString("remote_snapshots", null))

    internal fun removeRemoteSnapshots(variant: ApiVariant) {
        val entries = readRemoteSnapshots().filterNot { it.variantKey == variant.key }
        prefs.edit().putString("remote_snapshots", CoreRemoteInfoCodec.encode(entries)).apply()
    }

    internal fun saveRemoteSnapshot(variant: ApiVariant, requestedBranch: String,
        snapshot: CoreBranchSnapshot, checkedAt: Long) {
        val entry = CachedCoreRemoteInfo.from(variant, requestedBranch, snapshot, checkedAt)
        val entries = readRemoteSnapshots().filterNot { it.key == entry.key } + entry
        prefs.edit().putString("remote_snapshots", CoreRemoteInfoCodec.encode(entries.takeLast(24))).apply()
    }
}

@Serializable
internal data class CachedCoreRemoteInfo(
    val variantKey: String,
    val repo: String,
    val requestedBranch: String,
    val branch: String,
    val checkedAt: Long,
    val sha: String,
    val title: String,
    val message: String = "",
    val author: String = "",
    val committedAt: String = "",
    val htmlUrl: String = ""
) {
    val key: String get() = "$variantKey:$repo:$requestedBranch"

    fun snapshot(): CoreBranchSnapshot = CoreBranchSnapshot(repo, branch,
        CoreRemoteCommit(sha, title, message, author, committedAt, htmlUrl))

    companion object {
        fun from(variant: ApiVariant, requestedBranch: String, snapshot: CoreBranchSnapshot,
            checkedAt: Long): CachedCoreRemoteInfo = with(snapshot.commit) {
            CachedCoreRemoteInfo(variant.key, snapshot.repo, requestedBranch, snapshot.branch,
                checkedAt, sha, title, message, author, committedAt, htmlUrl)
        }
    }
}

internal object CoreRemoteInfoCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(entries: List<CachedCoreRemoteInfo>): String = json.encodeToString(entries)

    fun decode(raw: String?): List<CachedCoreRemoteInfo> = raw?.let {
        runCatching { json.decodeFromString<List<CachedCoreRemoteInfo>>(it) }.getOrNull()
    }.orEmpty().filter {
        ApiVariant.entries.any { variant -> variant.key == it.variantKey } &&
            it.repo.isNotBlank() && it.branch.isNotBlank() && it.sha.isNotBlank() && it.checkedAt > 0L
    }.distinctBy { it.key }
}

@Serializable
internal data class CoreDisplayInfoSnapshot(
    val scope: String,
    val cores: List<CachedCoreDisplayInfo>,
    val schemaVersion: Int = 1
) {
    fun installedInfo(variant: ApiVariant): CoreInfo? = cores
        .firstOrNull { it.variantKey == variant.key && it.isInstalled }
        ?.let {
            CoreInfo(
                variant = variant,
                version = it.version,
                isInstalled = true,
                sourceCommitSha = it.commitSha,
                pullRequestNumbers = it.pullRequestNumbers,
                sourceInstalledAtEpochMillis = it.installedAtEpochMillis,
                sourceCommitPublishedAt = it.commitPublishedAt
            )
        }
}

@Serializable
internal data class CachedCoreDisplayInfo(
    val variantKey: String,
    val version: String? = null,
    val isInstalled: Boolean,
    val commitSha: String = "",
    val pullRequestNumbers: List<Int> = emptyList(),
    val installedAtEpochMillis: Long = 0L,
    val commitPublishedAt: String = ""
)

internal object CoreDisplayInfoCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(scope: String, cores: List<CoreInfo>): String = json.encodeToString(
        CoreDisplayInfoSnapshot(scope, cores.map {
            CachedCoreDisplayInfo(
                variantKey = it.variant.key,
                version = it.version,
                isInstalled = it.isInstalled,
                commitSha = it.sourceCommitSha,
                pullRequestNumbers = it.pullRequestNumbers,
                installedAtEpochMillis = it.sourceInstalledAtEpochMillis,
                commitPublishedAt = it.sourceCommitPublishedAt
            )
        })
    )

    fun decode(raw: String?, scope: String): CoreDisplayInfoSnapshot? {
        if (raw.isNullOrBlank()) return null
        return runCatching { json.decodeFromString<CoreDisplayInfoSnapshot>(raw) }
            .getOrNull()?.takeIf {
                it.schemaVersion == 1 && it.scope == scope &&
                    it.cores.size == ApiVariant.entries.size &&
                    it.cores.map { core -> core.variantKey }.toSet() == ApiVariant.entries.map { variant -> variant.key }.toSet()
            }
    }
}
