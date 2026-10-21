package com.example.danmuapiapp.data.repository

import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

internal data class CoreArchiveExtraction(
    val extractedAny: Boolean,
    val commitSha: String?
)

internal object CoreArchiveLayout {
    fun resolveRootLevelFileName(entryName: String): String? {
        val clean = entryName.replace('\\', '/').trim('/')
        if (clean.isBlank()) return null
        val parts = clean.split('/')
        if (parts.size != 2) return null
        return when (parts[1]) {
            "package.json" -> "package.json"
            else -> null
        }
    }

    fun resolveDanmuRelativePath(entryName: String): String? {
        val clean = entryName.replace('\\', '/').trim('/')
        if (clean.isBlank()) return null

        val parts = clean.split('/')
        val index = parts.indexOfFirst {
            it.equals("danmu_api", ignoreCase = true) ||
                it.equals("danmu-api", ignoreCase = true)
        }
        if (index < 0) return null

        val relativeParts = parts.drop(index + 1)
        if (relativeParts.isEmpty()) return ""
        return relativeParts.joinToString("/")
    }

    fun extractDanmuFolder(zipStream: InputStream, outDir: File): CoreArchiveExtraction {
        var extractedAny = false
        var commitSha: String? = null

        ZipInputStream(BufferedInputStream(zipStream)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name ?: ""
                if (commitSha == null) {
                    commitSha = CoreArchiveSourceResolver.inferCommitSha(name)
                }
                val relative = resolveDanmuRelativePath(name)
                val rootFileName = resolveRootLevelFileName(name)
                if (relative == null && rootFileName == null) {
                    zip.closeEntry()
                    continue
                }

                if (relative != null && relative.isBlank()) {
                    zip.closeEntry()
                    continue
                }

                val outFile = File(outDir, relative ?: rootFileName.orEmpty())
                val canonicalRoot = outDir.canonicalPath
                val canonicalOut = outFile.canonicalPath
                if (canonicalOut != canonicalRoot && !canonicalOut.startsWith(canonicalRoot + File.separator)) {
                    zip.closeEntry()
                    continue
                }

                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { output -> zip.copyTo(output) }
                    extractedAny = true
                }
                zip.closeEntry()
            }
        }
        return CoreArchiveExtraction(extractedAny = extractedAny, commitSha = commitSha)
    }
}

internal class ProgressInputStream(
    private val source: InputStream,
    private val onRead: (Long) -> Unit
) : FilterInputStream(source) {
    private var totalRead = 0L

    override fun read(): Int {
        val value = super.read()
        if (value >= 0) {
            totalRead += 1
            onRead(totalRead)
        }
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val readSize = super.read(buffer, offset, length)
        if (readSize > 0) {
            totalRead += readSize
            onRead(totalRead)
        }
        return readSize
    }
}
