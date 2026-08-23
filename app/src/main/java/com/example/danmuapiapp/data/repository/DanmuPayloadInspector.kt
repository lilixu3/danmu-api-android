package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.DanmuDownloadFormat
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

internal data class DanmuPayloadInspection(
    val valid: Boolean,
    val count: Int? = null,
    val error: String = "",
    val warning: String? = null
)

internal object DanmuPayloadInspector {

    fun inspect(
        payload: ByteArray,
        format: DanmuDownloadFormat,
        contentType: String? = null
    ): DanmuPayloadInspection {
        return when (format) {
            DanmuDownloadFormat.Xml,
            DanmuDownloadFormat.BiliXml -> inspectXml(payload, format)

            DanmuDownloadFormat.DanuniBinPb -> inspectBinary(payload, contentType)

            else -> inspectJson(payload, format)
        }
    }

    private fun inspectXml(
        payload: ByteArray,
        format: DanmuDownloadFormat
    ): DanmuPayloadInspection {
        val prefix = payload
            .take(TEXT_PREFIX_LIMIT)
            .toByteArray()
            .toString(Charsets.UTF_8)
            .removePrefix("\uFEFF")
            .trimStart()
        if (!prefix.startsWith("<?xml") && !prefix.startsWith("<i")) {
            return invalid(format, "返回内容不是 XML")
        }
        val preview = runCatching {
            payload.inputStream().use { input ->
                DanmuFilePreviewParser.parse(
                    input = input,
                    format = format,
                    fileName = "",
                    relativePath = "",
                    bytes = payload.size.toLong(),
                    previewLimit = 1
                )
            }
        }.getOrElse { throwable ->
            return warning(
                format,
                "XML 解析失败：${throwable.message ?: "未知错误"}"
            )
        }
        if (preview.parseError != null) {
            return warning(format, preview.parseError)
        }
        return DanmuPayloadInspection(valid = true, count = preview.count)
    }

    private fun inspectJson(
        payload: ByteArray,
        format: DanmuDownloadFormat
    ): DanmuPayloadInspection {
        val text = payload.toString(Charsets.UTF_8).removePrefix("\uFEFF").trim()
        if (text.isBlank()) return invalid(format, "返回内容为空")
        val root = runCatching { JSONTokener(text).nextValue() }.getOrElse {
            return invalid(format, "返回内容不是有效 JSON")
        }

        val count = when (format) {
            DanmuDownloadFormat.Json -> {
                val obj = root as? JSONObject ?: return invalid(format, "缺少 JSON 根对象")
                val comments = obj.optJSONArray("comments")
                    ?: return invalid(format, "缺少 comments 数组，核心可能未支持该格式")
                nonNegativeInt(obj, "count") ?: comments.length()
            }

            DanmuDownloadFormat.ArtplayerJson -> {
                val comments = (root as? JSONObject)?.optJSONArray("danmuku")
                    ?: return invalid(format, "缺少 danmuku 数组，核心可能回退到了普通 JSON")
                comments.length()
            }

            DanmuDownloadFormat.BahaJson -> {
                val data = (root as? JSONObject)?.optJSONObject("data")
                    ?: return invalid(format, "缺少 data 对象，核心可能回退到了普通 JSON")
                val comments = data.optJSONArray("danmu")
                    ?: return invalid(format, "缺少 data.danmu 数组")
                nonNegativeInt(data, "totalCount") ?: comments.length()
            }

            DanmuDownloadFormat.DanuniJson -> {
                val comments = root as? JSONArray
                    ?: return invalid(format, "DanUni JSON 根节点不是数组，核心可能回退到了普通 JSON")
                comments.length()
            }

            DanmuDownloadFormat.DdplayJson -> {
                val obj = root as? JSONObject ?: return invalid(format, "缺少 JSON 根对象")
                val comments = obj.optJSONArray("comments")
                    ?: return invalid(format, "缺少 comments 数组")
                nonNegativeInt(obj, "count") ?: comments.length()
            }

            DanmuDownloadFormat.DplayerJson -> {
                val comments = (root as? JSONObject)?.optJSONArray("data")
                    ?: return invalid(format, "缺少 data 数组，核心可能回退到了普通 JSON")
                comments.length()
            }

            DanmuDownloadFormat.VodJson -> {
                val obj = root as? JSONObject ?: return invalid(format, "缺少 JSON 根对象")
                val comments = obj.optJSONArray("danmuku")
                    ?: return invalid(format, "缺少 danmuku 数组")
                nonNegativeInt(obj, "danum") ?: comments.length()
            }

            DanmuDownloadFormat.Xml,
            DanmuDownloadFormat.BiliXml,
            DanmuDownloadFormat.DanuniBinPb -> return invalid(format, "格式检查器调用错误")
        }
        return DanmuPayloadInspection(valid = true, count = count)
    }

    private fun inspectBinary(
        payload: ByteArray,
        contentType: String?
    ): DanmuPayloadInspection {
        if (payload.isEmpty()) {
            return invalid(DanmuDownloadFormat.DanuniBinPb, "返回内容为空")
        }
        val normalizedType = contentType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
            .orEmpty()
        val prefix = payload
            .take(TEXT_PREFIX_LIMIT)
            .toByteArray()
            .toString(Charsets.UTF_8)
            .removePrefix("\uFEFF")
            .trimStart()
        if (
            normalizedType == "application/json" ||
            normalizedType == "application/xml" ||
            prefix.startsWith('{') ||
            prefix.startsWith('[') ||
            prefix.startsWith("<?xml") ||
            prefix.startsWith("<i")
        ) {
            return invalid(
                DanmuDownloadFormat.DanuniBinPb,
                "核心返回了文本内容，可能尚未支持 DanUni Protobuf"
            )
        }
        return DanmuPayloadInspection(valid = true)
    }

    private fun nonNegativeInt(obj: JSONObject, key: String): Int? {
        if (!obj.has(key)) return null
        return obj.optInt(key, -1).takeIf { it >= 0 }
    }

    private fun invalid(format: DanmuDownloadFormat, detail: String): DanmuPayloadInspection {
        return DanmuPayloadInspection(
            valid = false,
            error = "${format.label} 格式校验失败：$detail"
        )
    }

    private fun warning(format: DanmuDownloadFormat, detail: String): DanmuPayloadInspection {
        return DanmuPayloadInspection(
            valid = true,
            warning = "${format.label} 格式检查警告：$detail"
        )
    }

    private const val TEXT_PREFIX_LIMIT = 4096
}
