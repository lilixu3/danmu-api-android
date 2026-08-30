package com.example.danmuapiapp.desktop.logs

import com.example.danmuapiapp.desktop.runtime.DesktopPaths
import com.example.danmuapiapp.desktop.runtime.HealthReadState
import com.example.danmuapiapp.desktop.runtime.RuntimeHealthSnapshot
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.Instant

class DesktopLogReader(
    private val maxBytesPerSource: Long = DEFAULT_MAX_BYTES_PER_SOURCE,
) {
    init {
        require(maxBytesPerSource > 0) { "日志读取上限必须大于 0" }
    }

    fun read(
        paths: DesktopPaths,
        healthState: HealthReadState = HealthReadState.Idle,
    ): DesktopLogReadResult {
        val healthSnapshot = (healthState as? HealthReadState.Ready)?.snapshot
        val sources = discoverSources(paths, healthSnapshot)
        val diagnostics = buildList {
            if (healthState is HealthReadState.Unavailable || healthState is HealthReadState.Failed) {
                add("核心业务日志路径未从当前健康快照确认，已尝试运行目录约定路径。")
            }
        }
        var sequence = 0L
        val sourceResults = sources.map { source ->
            val result = readSource(source, sequence)
            sequence += result.entries.size
            result
        }
        return DesktopLogReadResult(
            entries = sourceResults.flatMap { it.entries },
            sources = sourceResults,
            readAt = Instant.now(),
            diagnostics = diagnostics,
        )
    }

    private fun readSource(source: DesktopLogSource, sequenceStart: Long): DesktopLogSourceResult {
        val file = source.file
        if (!file.exists()) return DesktopLogSourceResult(source, DesktopLogSourceStatus.Missing, emptyList())
        if (!file.isFile) {
            return DesktopLogSourceResult(
                source,
                DesktopLogSourceStatus.Failed("路径不是普通文件：${file.absolutePath}"),
                emptyList(),
            )
        }
        return try {
            val (text, truncated) = readUtf8Tail(file)
            if (text.isEmpty()) {
                DesktopLogSourceResult(source, DesktopLogSourceStatus.Empty, emptyList())
            } else {
                val lines = text.split('\n').map { it.removeSuffix("\r") }
                val entries = lines
                    .filter { it.isNotEmpty() }
                    .mapIndexed { index, line ->
                        DesktopLogLineParser.parse(
                            source = source,
                            rawLine = line,
                            sequence = sequenceStart + index,
                        )
                    }
                DesktopLogSourceResult(
                    source,
                    DesktopLogSourceStatus.Readable(entries.size, truncated),
                    entries,
                )
            }
        } catch (error: CharacterCodingException) {
            DesktopLogSourceResult(
                source,
                DesktopLogSourceStatus.Failed("文件不是有效的 UTF-8：${file.absolutePath}", error),
                emptyList(),
            )
        } catch (error: IOException) {
            DesktopLogSourceResult(
                source,
                DesktopLogSourceStatus.Failed("读取失败：${error.message ?: error::class.simpleName}", error),
                emptyList(),
            )
        } catch (error: Throwable) {
            DesktopLogSourceResult(
                source,
                DesktopLogSourceStatus.Failed("读取失败：${error.message ?: error::class.simpleName}", error),
                emptyList(),
            )
        }
    }

    private fun readUtf8Tail(file: File): Pair<String, Boolean> {
        val length = file.length()
        val truncated = length > maxBytesPerSource
        val start = if (truncated) length - maxBytesPerSource else 0L
        file.inputStream().use { input ->
            if (start > 0) {
                var remaining = start
                while (remaining > 0) {
                    val skipped = input.skip(remaining)
                    if (skipped <= 0) throw IOException("无法定位到日志尾部")
                    remaining -= skipped
                }
            }
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var remaining = maxBytesPerSource
            while (remaining > 0) {
                val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (count < 0) break
                output.write(buffer, 0, count)
                remaining -= count
            }
            val bytes = output.toByteArray()
            val decoded = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
            val normalized = if (truncated) {
                val afterFirstLine = decoded.substringAfter('\n', decoded)
                if (afterFirstLine.isNotEmpty()) afterFirstLine else decoded.trimEnd('\r', '\n')
            } else {
                decoded
            }
            return normalized to truncated
        }
    }

    private fun discoverSources(paths: DesktopPaths, health: RuntimeHealthSnapshot?): List<DesktopLogSource> {
        val runtimeLogs = File(paths.runtimeDir, "nodejs-project/logs")
        val candidates = listOf(
            DesktopLogSource("tray", "托盘", File(paths.logsDir, "tray.log"), "桌面托盘与窗口生命周期日志"),
            DesktopLogSource("headless", "后台模式", File(paths.logsDir, "headless.log"), "开机自启后台实例日志"),
            DesktopLogSource("node-stdout", "Node 标准输出", File(runtimeLogs, "node-stdout.log"), "Node 进程标准输出"),
            DesktopLogSource("node-stderr", "Node 标准错误", File(runtimeLogs, "node-stderr.log"), "Node 进程标准错误"),
            DesktopLogSource(
                "core",
                "核心业务",
                health?.logFile?.trim()?.takeIf { it.isNotEmpty() }?.let(::File)
                    ?: File(runtimeLogs, "danmuapi.log"),
                "danmu_api 核心业务日志",
            ),
        )
        val seen = mutableSetOf<String>()
        return candidates.filter { seen.add(it.file.absoluteFile.normalize().path.lowercase()) }
    }

    companion object {
        const val DEFAULT_MAX_BYTES_PER_SOURCE: Long = 2L * 1024L * 1024L
    }
}

private fun File.normalize(): File = try {
    canonicalFile
} catch (_: IOException) {
    absoluteFile
}
