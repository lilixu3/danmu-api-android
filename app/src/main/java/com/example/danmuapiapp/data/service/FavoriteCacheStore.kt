package com.example.danmuapiapp.data.service

import android.content.Context
import com.example.danmuapiapp.data.util.ShellUtils.shellQuote
import com.example.danmuapiapp.domain.model.RunMode
import java.io.File
import java.io.IOException
import java.util.UUID
import org.json.JSONObject
import org.json.JSONTokener

object FavoriteCacheStore {
    const val FILE_NAME = "favoritesCache"
    const val EXPORT_FILE_NAME = "danmu_api_favorites.json"
    private const val MAX_DOCUMENT_BYTES = 8 * 1024 * 1024
    private const val MAX_FAVORITES = 10_000

    data class Snapshot(
        val content: String,
        val count: Int
    )

    fun readCurrent(context: Context): Result<Snapshot> {
        return read(context, RuntimePaths.currentRunMode(context))
    }

    fun writeCurrent(context: Context, raw: String): Result<Snapshot> {
        return write(context, RuntimePaths.currentRunMode(context), raw)
    }

    fun read(context: Context, mode: RunMode): Result<Snapshot> = runCatching {
        val raw = when (mode) {
            RunMode.Normal -> readNormal(RuntimePaths.normalProjectDir(context))
            RunMode.Root -> readRoot(context, RuntimePaths.rootProjectDir(context))
        }
        snapshotOf(raw)
    }

    fun write(context: Context, mode: RunMode, raw: String): Result<Snapshot> = runCatching {
        val snapshot = snapshotOf(raw)
        when (mode) {
            RunMode.Normal -> writeNormal(RuntimePaths.normalProjectDir(context), snapshot.content)
            RunMode.Root -> writeRoot(context, RuntimePaths.rootProjectDir(context), snapshot.content)
        }
        snapshot
    }

    /**
     * Keeps both runtime modes in sync so switching modes cannot strand newer
     * favorites in the mode that was just left.
     */
    fun synchronizeModes(
        context: Context,
        preferredMode: RunMode,
        otherMode: RunMode
    ): Result<Snapshot> = runCatching {
        val preferred = read(context, preferredMode).getOrThrow().content
        val other = read(context, otherMode).getOrThrow().content
        val merged = snapshotOf(mergeDocuments(preferred, other))
        write(context, preferredMode, merged.content).getOrThrow()
        write(context, otherMode, merged.content).getOrThrow()
        merged
    }

    internal fun snapshotOf(raw: String): Snapshot {
        val root = decodeDocument(raw)
        val keys = root.keys().asSequence().toList()
        require(keys.size <= MAX_FAVORITES) { "收藏数量超过 $MAX_FAVORITES 项" }
        keys.forEach { key ->
            require(key.isNotBlank()) { "收藏关键词不能为空" }
            require(root.opt(key) is JSONObject) { "收藏「$key」的数据格式无效" }
        }
        return Snapshot(
            content = root.toString(2).trimEnd() + "\n",
            count = keys.size
        )
    }

    /**
     * The first document wins ties; otherwise the entry with the latest known
     * refresh timestamp wins. Unknown fields are retained verbatim.
     */
    internal fun mergeDocuments(preferredRaw: String, secondaryRaw: String): String {
        val preferred = decodeDocument(preferredRaw)
        val secondary = decodeDocument(secondaryRaw)
        val merged = JSONObject()
        val orderedKeys = linkedSetOf<String>()
        secondary.keys().let { keys ->
            while (keys.hasNext()) orderedKeys += keys.next()
        }
        preferred.keys().let { keys ->
            while (keys.hasNext()) orderedKeys += keys.next()
        }

        orderedKeys.forEach { key ->
            val preferredEntry = preferred.optJSONObject(key)
            val secondaryEntry = secondary.optJSONObject(key)
            val selected = when {
                preferredEntry == null -> secondaryEntry
                secondaryEntry == null -> preferredEntry
                entryFreshness(preferredEntry) >= entryFreshness(secondaryEntry) -> preferredEntry
                else -> secondaryEntry
            }
            if (selected != null) merged.put(key, selected)
        }
        return merged.toString(2).trimEnd() + "\n"
    }

    private fun decodeDocument(raw: String): JSONObject {
        val bytes = raw.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_DOCUMENT_BYTES) { "收藏文件不能超过 8 MB" }
        require(raw.isNotBlank()) { "收藏文件内容为空" }

        var value: Any? = JSONTokener(raw).nextValue()
        repeat(2) {
            if (value is String) {
                value = JSONTokener(value).nextValue()
            }
        }
        return value as? JSONObject
            ?: throw IllegalArgumentException("收藏文件必须是 JSON 对象")
    }

    private fun entryFreshness(entry: JSONObject): Long {
        return maxOf(
            entry.optLong("lastRefreshAt", 0L),
            entry.optLong("timestamp", 0L),
            entry.optJSONObject("refreshSchedule")?.optLong("lastRunAt", 0L) ?: 0L
        )
    }

    private fun cacheFile(projectDir: File): File = File(projectDir, ".cache/$FILE_NAME")

    private fun readNormal(projectDir: File): String {
        val file = cacheFile(projectDir)
        return if (file.isFile) file.readText(Charsets.UTF_8) else "{}"
    }

    private fun writeNormal(projectDir: File, content: String) {
        val target = cacheFile(projectDir)
        val parent = target.parentFile ?: throw IOException("收藏目录路径无效")
        if (!parent.exists() && !parent.mkdirs()) {
            throw IOException("无法创建收藏目录：${parent.absolutePath}")
        }
        val token = UUID.randomUUID().toString()
        val temporary = File(parent, ".$FILE_NAME.tmp-$token")
        val backup = File(parent, ".$FILE_NAME.backup-$token")
        try {
            temporary.writeText(content, Charsets.UTF_8)
            if (target.exists() && !target.renameTo(backup)) {
                throw IOException("无法备份旧收藏文件")
            }

            try {
                if (!temporary.renameTo(target)) {
                    temporary.copyTo(target, overwrite = false)
                }
            } catch (error: Exception) {
                runCatching { target.delete() }
                if (backup.exists() && !backup.renameTo(target)) {
                    error.addSuppressed(IOException("收藏写入失败，且无法恢复旧收藏文件"))
                }
                throw error
            }
            runCatching { backup.delete() }
        } finally {
            runCatching { temporary.delete() }
            if (target.exists()) runCatching { backup.delete() }
        }
    }

    private fun readRoot(context: Context, projectDir: File): String {
        val temporary = File.createTempFile("favorite-cache-read-", ".json", context.cacheDir)
        runCatching { temporary.delete() }
        try {
            val source = cacheFile(projectDir)
            val script = """
                SRC=${shellQuote(source.absolutePath)}
                DST=${shellQuote(temporary.absolutePath)}
                if [ ! -f "${'$'}SRC" ]; then
                  printf '%s' '{}' > "${'$'}DST" || exit 2
                else
                  cat "${'$'}SRC" > "${'$'}DST" || exit 3
                fi
                chmod 0644 "${'$'}DST" || exit 4
            """.trimIndent()
            val result = RootShell.exec(script, timeoutMs = 8_000L)
            if (!result.ok) {
                throw IOException(result.stderr.ifBlank { "无法读取 Root 收藏数据" })
            }
            return temporary.readText(Charsets.UTF_8)
        } finally {
            runCatching { temporary.delete() }
        }
    }

    private fun writeRoot(context: Context, projectDir: File, content: String) {
        val source = File.createTempFile("favorite-cache-write-", ".json", context.cacheDir)
        try {
            source.writeText(content, Charsets.UTF_8)
            val target = cacheFile(projectDir)
            val token = UUID.randomUUID().toString().replace("-", "")
            val script = """
                SRC=${shellQuote(source.absolutePath)}
                DST=${shellQuote(target.absolutePath)}
                TMP="${'$'}DST.tmp-$token"
                mkdir -p "${'$'}(dirname "${'$'}DST")" || exit 2
                cat "${'$'}SRC" > "${'$'}TMP" || exit 3
                chmod 0644 "${'$'}TMP" || exit 4
                mv -f "${'$'}TMP" "${'$'}DST" || exit 5
            """.trimIndent()
            val result = RootShell.exec(script, timeoutMs = 8_000L)
            if (!result.ok) {
                throw IOException(result.stderr.ifBlank { "无法写入 Root 收藏数据" })
            }
        } finally {
            runCatching { source.delete() }
        }
    }
}
