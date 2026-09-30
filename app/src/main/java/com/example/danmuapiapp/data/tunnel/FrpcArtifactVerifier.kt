package com.example.danmuapiapp.data.tunnel

import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

internal const val FRPC_MAX_ARCHIVE_BYTES = 64L * 1024 * 1024
internal const val FRPC_MAX_KERNEL_BYTES = 64L * 1024 * 1024
internal data class VerifiedFrpcAsset(val url: String, val size: Long, val sha256: String)

/** Must be parsed from the official HTTPS API, never from a reverse-proxy response. */
internal fun verifiedFrpcAsset(payload: String, version: String, platform: String): VerifiedFrpcAsset {
    require(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][A-Za-z0-9.-]+)?").matches(version)) { "无效的内核版本" }
    require(platform in setOf("arm64", "arm", "amd64"))
    val json = JSONObject(payload)
    require(json.optString("tag_name") == "v$version" && !json.optBoolean("draft") && !json.optBoolean("prerelease")) { "官方发布信息不匹配" }
    val name = "frp_${version}_linux_${platform}.tar.gz"
    val url = "https://github.com/fatedier/frp/releases/download/v$version/$name"
    val assets = json.getJSONArray("assets")
    val asset = (0 until assets.length()).map { assets.getJSONObject(it) }.singleOrNull { it.optString("name") == name }
        ?: error("官方发布中没有对应内核")
    val digest = asset.optString("digest")
    require(Regex("sha256:[0-9a-fA-F]{64}").matches(digest)) { "官方发布未提供有效 SHA-256，已拒绝执行下载内核" }
    require(asset.optString("browser_download_url") == url) { "官方资产地址不匹配" }
    val size = asset.optLong("size", -1)
    require(size in 1..FRPC_MAX_ARCHIVE_BYTES) { "内核压缩包大小不符合限制" }
    return VerifiedFrpcAsset(url, size, digest.substringAfter(':').lowercase())
}

internal fun copyVerifiedFrpcArchive(input: InputStream, output: OutputStream, asset: VerifiedFrpcAsset, checkActive: () -> Unit = {}) {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(32 * 1024)
    var count = 0L
    while (true) {
        checkActive()
        val n = input.read(buffer)
        if (n < 0) break
        count += n
        if (count > asset.size || count > FRPC_MAX_ARCHIVE_BYTES) throw IOException("下载内容超过官方资产大小")
        digest.update(buffer, 0, n)
        output.write(buffer, 0, n)
    }
    check(count == asset.size) { "内核下载不完整" }
    check(digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } == asset.sha256) { "内核 SHA-256 校验失败" }
}

/** Check architecture before any root execution; FRP's official Go binaries are ET_EXEC or PIE. */
internal fun verifyFrpcElf(file: File, platform: String) {
    require(file.length() in 20..FRPC_MAX_KERNEL_BYTES) { "内核大小不符合限制" }
    val header = ByteArray(20)
    file.inputStream().use { input ->
        var offset = 0
        while (offset < header.size) {
            val n = input.read(header, offset, header.size - offset)
            require(n > 0) { "ELF 文件不完整" }; offset += n
        }
    }
    require(header.take(4) == listOf(0x7f.toByte(), 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte()) && header[5] == 1.toByte() && header[6] == 1.toByte()) { "下载内容不是有效 ELF" }
    val machine = (header[18].toInt() and 255) or ((header[19].toInt() and 255) shl 8)
    val type = (header[16].toInt() and 255) or ((header[17].toInt() and 255) shl 8)
    val expected = when (platform) { "arm64" -> 183; "arm" -> 40; "amd64" -> 62; else -> error("不支持的架构") }
    require(machine == expected && header[4] == (if (platform == "arm") 1 else 2).toByte() && type in setOf(2, 3)) { "下载内核架构不匹配" }
}
