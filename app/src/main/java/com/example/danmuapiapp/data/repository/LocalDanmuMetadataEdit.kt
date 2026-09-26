package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.LocalDanmuEditScope
import com.example.danmuapiapp.domain.model.LocalDanmuMetadataPatch
import com.example.danmuapiapp.domain.model.LocalDanmuResource
import com.example.danmuapiapp.domain.model.LocalDanmuType
import org.json.JSONObject

/**
 * 构造核心 PATCH /api/v2/local-danmu/:resourceKey 的请求体。
 *
 * 与核心字段约定保持一致：
 * - scope=resource 只看 episode / filename，其中 episode 必须显式出现（电影传 null 表示无集数）
 * - scope=group 只看 title / year / type / season，缺省字段沿用资源当前值
 */
internal fun buildLocalDanmuPatchBody(patch: LocalDanmuMetadataPatch): String {
    val body = JSONObject().put("scope", patch.scope.wire)
    when (patch.scope) {
        LocalDanmuEditScope.Resource -> {
            body.put("episode", patch.episode ?: JSONObject.NULL)
            patch.filename?.let { body.put("filename", it) }
        }

        LocalDanmuEditScope.Group -> {
            patch.title?.let { body.put("title", it) }
            patch.year?.let { body.put("year", it) }
            patch.type?.let { body.put("type", it.wire) }
            patch.season?.let { body.put("season", it) }
        }
    }
    return body.toString()
}

/**
 * 本地校验，规则与核心 handleLocalDanmuUpdate 一致，用来在发请求前拦住明显错误。
 * 返回 null 表示可以提交。
 */
internal fun validateLocalDanmuEdit(
    scope: LocalDanmuEditScope,
    title: String,
    year: Int?,
    type: LocalDanmuType,
    season: Int?,
    episode: Int?,
    filename: String,
    currentYear: Int
): String? {
    return when (scope) {
        LocalDanmuEditScope.Resource -> {
            if (type == LocalDanmuType.Tv && (episode == null || episode <= 0)) {
                "电视剧集数必须是大于 0 的整数"
            } else if (episode != null && episode <= 0) {
                "集数必须是大于 0 的整数"
            } else if (filename.isBlank()) {
                "文件名不能为空"
            } else if (filename.length > 240) {
                // 核心会静默截断到 240，这里直接提示，避免用户以为没生效。
                "文件名不能超过 240 个字符"
            } else {
                null
            }
        }

        LocalDanmuEditScope.Group -> {
            if (title.isBlank()) {
                "标题为必填项"
            } else if (year == null || year < 1900 || year > currentYear) {
                "年份必须在 1900–$currentYear 年之间"
            } else if (season == null || season <= 0) {
                "季数必须是大于 0 的整数"
            } else {
                null
            }
        }
    }
}

/**
 * 编辑会重算 resourceKey（旧 key 文件会被删除），但 videoId 不变。
 * 这里把「编辑前的资源」映射到「编辑后的新 resourceKey」，用于迁移本地来源记录，
 * 保证改完标题/集数后详情页预览仍然找得到原文件。
 *
 * 只返回确实变化的 key 对，避免无意义地重写来源表。
 */
internal fun matchMigratedResourceKeys(
    before: Collection<LocalDanmuResource>,
    after: Collection<LocalDanmuResource>
): Map<String, String> {
    if (before.isEmpty() || after.isEmpty()) return emptyMap()
    val result = LinkedHashMap<String, String>()
    before.forEach { resource ->
        val match = matchUpdatedResource(resource, after) ?: return@forEach
        if (match.resourceKey.isNotBlank() && match.resourceKey != resource.resourceKey) {
            result[resource.resourceKey] = match.resourceKey
        }
    }
    return result
}

private fun matchUpdatedResource(
    before: LocalDanmuResource,
    after: Collection<LocalDanmuResource>
): LocalDanmuResource? {
    val videoId = before.videoId.trim()
    if (videoId.isNotEmpty()) {
        after.firstOrNull { it.videoId.trim() == videoId }?.let { return it }
    }
    after.firstOrNull { it.resourceKey == before.resourceKey }?.let { return it }
    // 旧核心可能没有 videoId：只有一条结果时直接认领，否则按文件名+集数兜底。
    if (after.size == 1) return after.first()
    return after.firstOrNull { it.filename == before.filename && it.episode == before.episode }
}
