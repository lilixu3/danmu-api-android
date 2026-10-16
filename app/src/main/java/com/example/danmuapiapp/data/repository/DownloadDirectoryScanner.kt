package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.DanmuDownloadFormat
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.ArrayDeque

/** Directory metadata and bounded format sniffing; full parsing belongs to preview/download. */
internal object DownloadDirectoryScanner {
    const val PREFIX_BYTES = 200 * 1024

    data class Entry(
        val uri: String,
        val name: String,
        val isDirectory: Boolean,
        val lastModified: Long = 0L,
        val bytes: Long = 0L
    )

    data class Candidate(
        val uri: String,
        val name: String,
        val format: DanmuDownloadFormat,
        val relativePath: String,
        val lastModified: Long,
        val bytes: Long
    )

    data class ScanResult(
        val candidates: List<Candidate>,
        val scannedUris: Set<String>,
        val scannedFiles: Int,
        val matchedFiles: Int,
        val skippedFiles: Int,
        val reliable: Boolean,
        val truncated: Boolean
    )

    private data class Directory(val uri: String, val relativePath: String, val depth: Int)

    fun scan(
        rootUri: String,
        existingUris: Set<String>,
        maxFiles: Int,
        maxDepth: Int,
        listChildren: (String) -> List<Entry>?
    ): ScanResult {
        require(maxFiles > 0 && maxDepth >= 0)
        val pending = ArrayDeque<Directory>()
        val visitedDirectories = mutableSetOf<String>()
        val scannedUris = mutableSetOf<String>()
        val candidates = mutableListOf<Candidate>()
        pending.add(Directory(rootUri, "", 0))
        var scannedFiles = 0
        var matchedFiles = 0
        var skippedFiles = 0
        var reliable = true
        var truncated = false

        while (pending.isNotEmpty() && scannedFiles < maxFiles) {
            val directory = pending.removeFirst()
            if (!visitedDirectories.add(directory.uri)) continue
            val children = listChildren(directory.uri)
            if (children == null) {
                reliable = false
                continue
            }
            for (child in children) {
                val name = child.name.trim()
                if (name.isBlank()) continue
                val relativePath = if (directory.relativePath.isBlank()) name
                    else "${directory.relativePath}/$name"
                if (child.isDirectory) {
                    if (directory.depth < maxDepth) {
                        pending.add(Directory(child.uri, relativePath, directory.depth + 1))
                    } else {
                        // Files below this point were not checked, so pruning is unsafe.
                        truncated = true
                    }
                    continue
                }
                if (scannedFiles >= maxFiles) {
                    truncated = true
                    break
                }
                scannedFiles++
                scannedUris += child.uri
                val format = DanmuDownloadFormat.fromFileName(name)
                if (format == null) {
                    skippedFiles++
                    continue
                }
                matchedFiles++
                if (child.uri in existingUris) {
                    skippedFiles++
                    continue
                }
                candidates += Candidate(
                    uri = child.uri,
                    name = name,
                    format = format,
                    relativePath = relativePath,
                    lastModified = child.lastModified.coerceAtLeast(0L),
                    bytes = child.bytes.coerceAtLeast(0L)
                )
            }
        }
        if (pending.isNotEmpty() || scannedFiles >= maxFiles) truncated = true
        return ScanResult(candidates, scannedUris, scannedFiles, matchedFiles, skippedFiles, reliable, truncated)
    }

    fun readPrefix(input: InputStream, limit: Int = PREFIX_BYTES): ByteArray {
        require(limit > 0)
        val output = ByteArrayOutputStream(minOf(DEFAULT_BUFFER_SIZE, limit))
        val buffer = ByteArray(minOf(DEFAULT_BUFFER_SIZE, limit))
        var remaining = limit
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size, remaining))
            if (read < 0) break
            if (read == 0) {
                val byte = input.read()
                if (byte < 0) break
                output.write(byte)
                remaining--
            } else {
                output.write(buffer, 0, read)
                remaining -= read
            }
        }
        return output.toByteArray()
    }

    fun isPlausiblePrefix(prefix: ByteArray, format: DanmuDownloadFormat): Boolean {
        if (prefix.isEmpty()) return false
        if (format == DanmuDownloadFormat.DanuniBinPb) return true
        val text = prefix.toString(Charsets.UTF_8).removePrefix("\uFEFF").trimStart()
        return when (format) {
            DanmuDownloadFormat.Xml,
            DanmuDownloadFormat.BiliXml -> text.startsWith("<?xml") || text.startsWith("<i")
            DanmuDownloadFormat.Json,
            DanmuDownloadFormat.DdplayJson -> text.startsWith("{") && text.contains("\"comments\"")
            DanmuDownloadFormat.ArtplayerJson,
            DanmuDownloadFormat.VodJson -> text.startsWith("{") && text.contains("\"danmuku\"")
            DanmuDownloadFormat.BahaJson ->
                text.startsWith("{") && text.contains("\"data\"") && text.contains("\"danmu\"")
            DanmuDownloadFormat.DanuniJson -> text.startsWith("[")
            DanmuDownloadFormat.DplayerJson -> text.startsWith("{") && text.contains("\"data\"")
            DanmuDownloadFormat.DanuniBinPb -> true
        }
    }
}
