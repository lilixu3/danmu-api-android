package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.RunMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
internal data class CoreSourceMetadata(
    val repo: String = "",
    val branch: String = "",
    /** 远端可比较的主分支基线；不能写成本地生成的 merge SHA。 */
    val commitSha: String = "",
    val commitPublishedAt: String = "",
    val versionLabel: String = "",
    val pullRequestNumbers: List<Int> = emptyList(),
    val pullRequestHeadShas: List<String> = emptyList(),
    val localMergeSha: String = ""
)

internal object CoreSourceMetadataStore {
    private val json = Json { ignoreUnknownKeys = true }

    fun read(mode: RunMode, normalFile: File, rootPath: String, readRoot: (String) -> String?): CoreSourceMetadata? {
        // /data/adb 不允许普通 UID 遍历。Root 核心必须通过 su 读取，不能拿普通目录副本代替。
        val text = when (corePresenceSourceFor(mode)) {
            CorePresenceSource.RootDir -> readRoot(rootPath)
            CorePresenceSource.NormalDir -> runCatching { normalFile.readText() }.getOrNull()
        } ?: return null
        return runCatching { json.decodeFromString<CoreSourceMetadata>(text) }.getOrNull()
    }
}
