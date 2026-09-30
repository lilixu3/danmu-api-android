package com.example.danmuapiapp.data.tunnel

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream

/** Bounded streaming tar reader: only writes one regular frpc file, never archive paths. */
internal fun extractFrpcArchive(compressed: InputStream, output: OutputStream, checkActive: () -> Unit = {}) {
    GZIPInputStream(compressed).use { input ->
        val buffer = ByteArray(32 * 1024)
        val header = ByteArray(512)
        var expanded = 0L
        var found = false
        fun readFully(bytes: ByteArray, size: Int) {
            var offset = 0
            while (offset < size) {
                checkActive()
                val n = input.read(bytes, offset, size - offset)
                if (n < 0) throw EOFException("tar 内容不完整")
                offset += n; expanded += n
                if (expanded > 256L * 1024 * 1024) throw IOException("解压内容超过限制")
            }
        }
        fun octal(offset: Int, size: Int): Long {
            val text = String(header, offset, size, Charsets.US_ASCII).trim('\u0000', ' ')
            if (text.isEmpty() || text.any { it !in '0'..'7' }) throw IOException("无效的 tar 数字字段")
            return text.toLongOrNull(8) ?: throw IOException("tar 数字溢出")
        }
        fun transfer(size: Long, write: Boolean) {
            var remaining = size
            while (remaining > 0) {
                val n = minOf(remaining, buffer.size.toLong()).toInt()
                readFully(buffer, n)
                if (write) output.write(buffer, 0, n)
                remaining -= n
            }
        }
        var entries = 0
        while (true) {
            readFully(header, 512)
            if (header.all { it == 0.toByte() }) {
                readFully(header, 512)
                require(header.all { it == 0.toByte() }) { "tar 结束标记异常" }
                // Reach GZIP EOF to validate trailer/CRC; accept tar zero padding only.
                while (true) {
                    checkActive()
                    val n = input.read(buffer)
                    if (n < 0) break
                    expanded += n
                    require(expanded <= 256L * 1024 * 1024 && (0 until n).all { buffer[it] == 0.toByte() }) { "tar 尾部异常" }
                }
                require(found) { "压缩包里没有找到 frpc" }
                return
            }
            require(++entries <= 128) { "tar 条目过多" }
            val checksum = octal(148, 8)
            val actual = header.indices.sumOf { if (it in 148..155) 32 else header[it].toInt() and 255 }
            require(checksum == actual.toLong()) { "tar 头部校验失败" }
            val size = octal(124, 12)
            require(size <= 128L * 1024 * 1024) { "tar 条目过大" }
            val name = String(header, 0, 100, Charsets.UTF_8).substringBefore('\u0000')
            val isKernel = name == "frpc" || name.endsWith("/frpc")
            if (isKernel) {
                require(!found && size in 1..FRPC_MAX_KERNEL_BYTES && header[156] in byteArrayOf(0, '0'.code.toByte())) { "frpc 条目无效" }
                found = true
            }
            transfer(size, isKernel)
            transfer((512 - size % 512) % 512, false)
        }
    }
}

/** Kept for small callers/tests; production updates extract directly to a temporary file. */
fun extractFrpcFromTarGz(gz: ByteArray): ByteArray? = try {
    require(gz.size.toLong() <= FRPC_MAX_ARCHIVE_BYTES)
    ByteArrayOutputStream().use { output -> extractFrpcArchive(ByteArrayInputStream(gz), output); output.toByteArray() }
} catch (_: Exception) { null }
