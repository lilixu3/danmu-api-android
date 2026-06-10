package com.example.danmuapiapp.ui.screen.apitest

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.math.BigInteger
import java.net.URI
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.TimeUnit

internal data class UrlDanmuMetadataResult(
    val metadata: UrlDanmuMetadata?,
    val trace: List<DanmuRequestTrace> = emptyList()
)

internal class UrlDanmuMetadataResolver(
    httpClient: OkHttpClient
) {
    private val metadataClient = httpClient.newBuilder()
        .callTimeout(6, TimeUnit.SECONDS)
        .build()

    suspend fun resolve(inputUrl: String): UrlDanmuMetadataResult {
        if (!inputUrl.startsWith("http", ignoreCase = true)) {
            return UrlDanmuMetadataResult(metadata = null)
        }
        return withContext(Dispatchers.IO) {
            when (UrlDanmuMetadataPlatform.detect(inputUrl)) {
                UrlDanmuMetadataPlatform.Tencent -> resolveByHtml(
                    inputUrl = inputUrl,
                    label = "腾讯元数据",
                    platformLabel = "腾讯视频",
                    userAgent = DESKTOP_UA
                )
                UrlDanmuMetadataPlatform.Iqiyi -> withHtmlFallback(
                    primary = resolveIqiyi(inputUrl),
                    inputUrl = inputUrl,
                    label = "爱奇艺页面元数据",
                    platformLabel = "爱奇艺",
                    userAgent = DESKTOP_UA
                )
                UrlDanmuMetadataPlatform.Youku -> withHtmlFallback(
                    primary = resolveYouku(inputUrl),
                    inputUrl = inputUrl,
                    label = "优酷页面元数据",
                    platformLabel = "优酷",
                    userAgent = DESKTOP_UA
                )
                UrlDanmuMetadataPlatform.Mango -> withHtmlFallback(
                    primary = resolveMango(inputUrl),
                    inputUrl = inputUrl,
                    label = "芒果页面元数据",
                    platformLabel = "芒果TV",
                    userAgent = DESKTOP_UA
                )
                UrlDanmuMetadataPlatform.Bilibili -> withHtmlFallback(
                    primary = resolveBilibili(inputUrl),
                    inputUrl = inputUrl,
                    label = "B站页面元数据",
                    platformLabel = "哔哩哔哩",
                    userAgent = DESKTOP_UA
                )
                UrlDanmuMetadataPlatform.Migu -> withHtmlFallback(
                    primary = resolveMigu(inputUrl),
                    inputUrl = inputUrl,
                    label = "咪咕页面元数据",
                    platformLabel = "咪咕视频",
                    userAgent = DESKTOP_UA
                )
                UrlDanmuMetadataPlatform.Sohu -> withHtmlFallback(
                    primary = resolveSohu(inputUrl),
                    inputUrl = inputUrl,
                    label = "搜狐页面元数据",
                    platformLabel = "搜狐视频",
                    userAgent = DESKTOP_UA
                )
                UrlDanmuMetadataPlatform.Leshi -> withHtmlFallback(
                    primary = resolveLeshi(inputUrl),
                    inputUrl = inputUrl,
                    label = "乐视页面元数据",
                    platformLabel = "乐视视频",
                    userAgent = DESKTOP_UA
                )
                UrlDanmuMetadataPlatform.Xigua -> withHtmlFallback(
                    primary = resolveXigua(inputUrl),
                    inputUrl = inputUrl,
                    label = "西瓜页面元数据",
                    platformLabel = "西瓜视频",
                    userAgent = DESKTOP_UA
                )
                UrlDanmuMetadataPlatform.Unknown -> resolveByHtml(
                    inputUrl = inputUrl,
                    label = "页面元数据",
                    platformLabel = "URL",
                    userAgent = MOBILE_UA
                )
            }
        }
    }

    private fun resolveIqiyi(inputUrl: String): UrlDanmuMetadataResult? {
        val videoId = Regex("""/v_([A-Za-z0-9]+)\.html""")
            .find(inputUrl)
            ?.groupValues
            ?.getOrNull(1)
            ?: return null
        val entityId = UrlDanmuMetadataPlatformParsers.iqiyiEntityIdFromVideoId(videoId) ?: return null
        val params = linkedMapOf(
            "entity_id" to entityId,
            "device_id" to "qd5fwuaj4hunxxdgzwkcqmefeb3ww5hx",
            "auth_cookie" to "",
            "user_id" to "0",
            "vip_type" to "-1",
            "vip_status" to "0",
            "conduit_id" to "",
            "pcv" to "13.082.22866",
            "app_version" to "13.082.22866",
            "ext" to "",
            "app_mode" to "standard",
            "scale" to "100",
            "timestamp" to System.currentTimeMillis().toString(),
            "src" to "pca_tvg",
            "os" to "",
            "ad_ext" to "{\"r\":\"2.2.0-ares6-pure\"}"
        )
        params["sign"] = UrlDanmuMetadataPlatformParsers.iqiyiSign(params)
        val apiUrl = buildUrl("https://www.iqiyi.com/prelw/tvg/v2/lw/base_info", params)
        val trace = trace("爱奇艺详情", "GET", apiUrl, inputUrl)
        val body = fetchText(apiUrl, userAgent = DESKTOP_UA, referer = "https://www.iqiyi.com/") ?: return UrlDanmuMetadataResult(null, listOf(trace))
        val metadata = UrlDanmuMetadataPlatformParsers.parseIqiyiBaseInfoJson(inputUrl, body)
        return UrlDanmuMetadataResult(metadata = metadata, trace = listOf(trace))
    }

    private fun resolveYouku(inputUrl: String): UrlDanmuMetadataResult? {
        val videoId = Regex("""id_([^./?#]+)""")
            .find(inputUrl)
            ?.groupValues
            ?.getOrNull(1)
            ?: return null
        val videoApi = buildUrl(
            "https://openapi.youku.com/v2/videos/show.json",
            linkedMapOf(
                "client_id" to YOUKU_CLIENT_ID,
                "video_id" to videoId,
                "package" to "com.huawei.hwvplayer.youku",
                "ext" to "show"
            )
        )
        val traces = mutableListOf(trace("优酷视频详情", "GET", videoApi, inputUrl))
        val videoJson = fetchText(videoApi, userAgent = DESKTOP_UA) ?: return UrlDanmuMetadataResult(null, traces)
        val showId = runCatching {
            JSONObject(videoJson).optJSONObject("show")?.optString("id").orEmpty().trim()
        }.getOrDefault("")
        val showJson = if (showId.isNotBlank()) {
            val showApi = buildUrl(
                "https://openapi.youku.com/v2/shows/show.json",
                linkedMapOf(
                    "client_id" to YOUKU_CLIENT_ID,
                    "show_id" to showId,
                    "package" to "com.huawei.hwvplayer.youku",
                    "ext" to "show"
                )
            )
            traces += trace("优酷剧集详情", "GET", showApi, showId)
            fetchText(showApi, userAgent = DESKTOP_UA)
        } else {
            null
        }
        return UrlDanmuMetadataResult(
            metadata = UrlDanmuMetadataPlatformParsers.parseYoukuJson(inputUrl, videoJson, showJson),
            trace = traces
        )
    }

    private fun resolveMango(inputUrl: String): UrlDanmuMetadataResult? {
        val match = Regex("""/b/(\d+)/(\d+)\.html""").find(inputUrl) ?: return null
        val cid = match.groupValues[1]
        val vid = match.groupValues[2]
        val apiUrl = buildUrl(
            "https://pcweb.api.mgtv.com/video/info",
            linkedMapOf("cid" to cid, "vid" to vid)
        )
        val trace = trace("芒果视频详情", "GET", apiUrl, inputUrl)
        val body = fetchText(apiUrl, userAgent = DESKTOP_UA, referer = "https://www.mgtv.com/") ?: return UrlDanmuMetadataResult(null, listOf(trace))
        return UrlDanmuMetadataResult(
            metadata = UrlDanmuMetadataPlatformParsers.parseMangoVideoInfoJson(inputUrl, body),
            trace = listOf(trace)
        )
    }

    private fun resolveBilibili(inputUrl: String): UrlDanmuMetadataResult? {
        val epId = Regex("""/bangumi/play/ep(\d+)""").find(inputUrl)?.groupValues?.getOrNull(1)
            ?: Regex("""[?&]ep_id=(\d+)""").find(inputUrl)?.groupValues?.getOrNull(1)
        if (!epId.isNullOrBlank()) {
            val apiUrl = buildUrl("https://api.bilibili.com/pgc/view/web/season", linkedMapOf("ep_id" to epId))
            val trace = trace("B站番剧详情", "GET", apiUrl, inputUrl)
            val body = fetchText(apiUrl, userAgent = DESKTOP_UA, referer = "https://www.bilibili.com/") ?: return UrlDanmuMetadataResult(null, listOf(trace))
            return UrlDanmuMetadataResult(
                metadata = UrlDanmuMetadataPlatformParsers.parseBilibiliSeasonJson(inputUrl, body, epId),
                trace = listOf(trace)
            )
        }

        val seasonId = Regex("""/bangumi/play/ss(\d+)""").find(inputUrl)?.groupValues?.getOrNull(1)
            ?: Regex("""[?&]season_id=(\d+)""").find(inputUrl)?.groupValues?.getOrNull(1)
        if (!seasonId.isNullOrBlank()) {
            val apiUrl = buildUrl("https://api.bilibili.com/pgc/view/web/season", linkedMapOf("season_id" to seasonId))
            val trace = trace("B站番剧详情", "GET", apiUrl, inputUrl)
            val body = fetchText(apiUrl, userAgent = DESKTOP_UA, referer = "https://www.bilibili.com/") ?: return UrlDanmuMetadataResult(null, listOf(trace))
            return UrlDanmuMetadataResult(
                metadata = UrlDanmuMetadataPlatformParsers.parseBilibiliSeasonJson(inputUrl, body, null),
                trace = listOf(trace)
            )
        }

        val bvid = Regex("""/(BV[0-9A-Za-z]+)""").find(inputUrl)?.groupValues?.getOrNull(1)
        if (!bvid.isNullOrBlank()) {
            val apiUrl = buildUrl("https://api.bilibili.com/x/web-interface/view", linkedMapOf("bvid" to bvid))
            val trace = trace("B站视频详情", "GET", apiUrl, inputUrl)
            val body = fetchText(apiUrl, userAgent = DESKTOP_UA, referer = "https://www.bilibili.com/") ?: return UrlDanmuMetadataResult(null, listOf(trace))
            return UrlDanmuMetadataResult(
                metadata = UrlDanmuMetadataPlatformParsers.parseBilibiliViewJson(inputUrl, body),
                trace = listOf(trace)
            )
        }
        return null
    }

    private fun resolveMigu(inputUrl: String): UrlDanmuMetadataResult? {
        val cid = queryParam(inputUrl, "cid")
            ?: Regex("""cid=(\d+)""").find(inputUrl)?.groupValues?.getOrNull(1)
            ?: return null
        val apiUrl = "https://v3-sc.miguvideo.com/program/v4/cont/content-info/$cid/1"
        val trace = trace("咪咕内容详情", "GET", apiUrl, inputUrl)
        val body = fetchText(apiUrl, userAgent = DESKTOP_UA, referer = "https://www.miguvideo.com/") ?: return UrlDanmuMetadataResult(null, listOf(trace))
        return UrlDanmuMetadataResult(
            metadata = UrlDanmuMetadataPlatformParsers.parseMiguContentInfoJson(inputUrl, body),
            trace = listOf(trace)
        )
    }

    private fun resolveSohu(inputUrl: String): UrlDanmuMetadataResult? {
        val traces = mutableListOf<DanmuRequestTrace>()
        traces += trace("搜狐页面元数据", "GET", inputUrl, inputUrl)
        val html = fetchText(inputUrl, userAgent = DESKTOP_UA, referer = "https://tv.sohu.com/") ?: return UrlDanmuMetadataResult(null, traces)
        val pageMetadata = parseUrlDanmuMetadata(inputUrl, html)
        val playlistId = UrlDanmuMetadataPlatformParsers.extractSohuPlaylistId(html)
        if (playlistId.isNullOrBlank()) {
            return UrlDanmuMetadataResult(pageMetadata?.copy(platformLabel = "搜狐视频"), traces)
        }
        val apiUrl = buildUrl("https://pl.hd.sohu.com/videolist", linkedMapOf("playlistid" to playlistId))
        traces += trace("搜狐专辑详情", "GET", apiUrl, playlistId)
        val json = fetchText(apiUrl, userAgent = DESKTOP_UA, referer = inputUrl)
        val apiMetadata = json?.let { UrlDanmuMetadataPlatformParsers.parseSohuVideoListJson(inputUrl, it) }
        return UrlDanmuMetadataResult(
            metadata = mergeMetadata(primary = apiMetadata, fallback = pageMetadata, platformLabel = "搜狐视频"),
            trace = traces
        )
    }

    private fun resolveLeshi(inputUrl: String): UrlDanmuMetadataResult? {
        val videoId = Regex("""/vplay/(\d+)\.html""").find(inputUrl)?.groupValues?.getOrNull(1)
            ?: return null
        val pageUrl = "https://www.le.com/ptv/vplay/$videoId.html"
        val trace = trace("乐视页面元数据", "GET", pageUrl, inputUrl)
        val html = fetchText(pageUrl, userAgent = DESKTOP_UA, referer = "https://www.le.com/") ?: return UrlDanmuMetadataResult(null, listOf(trace))
        val pageMetadata = parseUrlDanmuMetadata(inputUrl, html)
        val leMetadata = UrlDanmuMetadataPlatformParsers.parseLeshiHtml(inputUrl, html)
        return UrlDanmuMetadataResult(
            metadata = mergeMetadata(primary = leMetadata, fallback = pageMetadata, platformLabel = "乐视视频"),
            trace = listOf(trace)
        )
    }

    private fun resolveXigua(inputUrl: String): UrlDanmuMetadataResult? {
        if (inputUrl.contains("douyin.com", ignoreCase = true)) return null
        val gid = Regex("""/video/(\d+)""").find(inputUrl)?.groupValues?.getOrNull(1)
            ?: Regex("""/(\d{10,})(?:[/?#]|$)""").find(inputUrl)?.groupValues?.getOrNull(1)
            ?: return null
        val pageUrl = "https://m.ixigua.com/video/$gid"
        val trace = trace("西瓜页面元数据", "GET", pageUrl, inputUrl)
        val html = fetchText(pageUrl, userAgent = MOBILE_UA, referer = "https://m.ixigua.com/") ?: return UrlDanmuMetadataResult(null, listOf(trace))
        return UrlDanmuMetadataResult(
            metadata = parseUrlDanmuMetadata(inputUrl, html)?.copy(platformLabel = "西瓜视频"),
            trace = listOf(trace)
        )
    }

    private fun resolveByHtml(
        inputUrl: String,
        label: String,
        platformLabel: String,
        userAgent: String
    ): UrlDanmuMetadataResult {
        val trace = trace(label, "GET", inputUrl, inputUrl)
        val html = fetchText(inputUrl, userAgent = userAgent)
        val metadata = html?.let { parseUrlDanmuMetadata(inputUrl, it)?.copy(platformLabel = platformLabel) }
        return UrlDanmuMetadataResult(metadata = metadata, trace = listOf(trace))
    }

    private fun withHtmlFallback(
        primary: UrlDanmuMetadataResult?,
        inputUrl: String,
        label: String,
        platformLabel: String,
        userAgent: String
    ): UrlDanmuMetadataResult {
        if (primary?.metadata != null) return primary
        val fallback = resolveByHtml(
            inputUrl = inputUrl,
            label = label,
            platformLabel = platformLabel,
            userAgent = userAgent
        )
        return UrlDanmuMetadataResult(
            metadata = fallback.metadata,
            trace = primary?.trace.orEmpty() + fallback.trace
        )
    }

    private fun fetchText(
        url: String,
        userAgent: String,
        referer: String? = null,
        maxBytes: Long = 768L * 1024L
    ): String? {
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .apply {
                    if (!referer.isNullOrBlank()) header("Referer", referer)
                    header("Accept", "text/html,application/json,text/plain,*/*")
                }
                .get()
                .build()
            metadataClient.newCall(request).execute().use { response ->
                if (response.code !in 200..299) return@use null
                val contentLength = response.body.contentLength()
                if (contentLength > maxBytes) return@use null
                response.peekBody(maxBytes).string()
            }
        }.getOrNull()
    }

    private fun buildUrl(baseUrl: String, params: Map<String, String>): String {
        val builder = baseUrl.toHttpUrl().newBuilder()
        params.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        return builder.build().toString()
    }

    private fun queryParam(url: String, name: String): String? {
        return runCatching {
            URI(url).rawQuery
                ?.split('&')
                ?.firstNotNullOfOrNull { part ->
                    val index = part.indexOf('=')
                    val key = if (index >= 0) part.substring(0, index) else part
                    val value = if (index >= 0) part.substring(index + 1) else ""
                    if (key == name && value.isNotBlank()) value else null
                }
        }.getOrNull()
    }

    private fun trace(label: String, method: String, url: String, inputValue: String): DanmuRequestTrace {
        return DanmuRequestTrace(
            label = label,
            method = method,
            url = url,
            inputLabel = "URL",
            inputValue = inputValue
        )
    }

    private fun mergeMetadata(
        primary: UrlDanmuMetadata?,
        fallback: UrlDanmuMetadata?,
        platformLabel: String
    ): UrlDanmuMetadata? {
        if (primary == null && fallback == null) return null
        val p = primary ?: UrlDanmuMetadata()
        val f = fallback ?: UrlDanmuMetadata()
        return UrlDanmuMetadata(
            title = p.title.ifBlank { f.title },
            episodeTitle = p.episodeTitle.ifBlank { f.episodeTitle },
            posterUrl = p.posterUrl.ifBlank { f.posterUrl },
            year = p.year.ifBlank { f.year },
            episodeLabel = p.episodeLabel.ifBlank { f.episodeLabel },
            platformLabel = platformLabel
        )
    }

    private companion object {
        private const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
        private const val MOBILE_UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/125 Mobile Safari/537.36"
        private const val YOUKU_CLIENT_ID = "53e6cc67237fc59a"
    }
}

internal enum class UrlDanmuMetadataPlatform {
    Tencent,
    Iqiyi,
    Youku,
    Mango,
    Bilibili,
    Migu,
    Sohu,
    Leshi,
    Xigua,
    Unknown;

    companion object {
        fun detect(inputUrl: String): UrlDanmuMetadataPlatform {
            val lower = inputUrl.lowercase(Locale.ROOT)
            return when {
                ".qq.com" in lower -> Tencent
                ".iqiyi.com" in lower -> Iqiyi
                ".youku.com" in lower -> Youku
                ".mgtv.com" in lower -> Mango
                ".bilibili.com" in lower || "b23.tv" in lower -> Bilibili
                ".miguvideo.com" in lower -> Migu
                ".sohu.com" in lower -> Sohu
                ".le.com" in lower -> Leshi
                ".ixigua.com" in lower || ".douyin.com" in lower -> Xigua
                else -> Unknown
            }
        }
    }
}

internal object UrlDanmuMetadataPlatformParsers {
    private val iqiyiXorKey = BigInteger("75706971676c", 16)
    private const val iqiyiSignSalt = "howcuteitis"
    private const val iqiyiSignKeyName = "secret_key"

    fun iqiyiEntityIdFromVideoId(videoId: String): String? {
        return runCatching {
            val decoded = BigInteger(videoId, 36)
            val xorResult = decoded.xor(iqiyiXorKey)
            val threshold = BigInteger.valueOf(900000L)
            val finalResult = if (xorResult < threshold) {
                xorResult.add(threshold).multiply(BigInteger.valueOf(100L))
            } else {
                xorResult
            }
            finalResult.toString()
        }.getOrNull()
    }

    fun iqiyiSign(params: Map<String, String>): String {
        val signInput = params
            .filterKeys { it != "sign" }
            .toSortedMap()
            .entries
            .joinToString("&") { (key, value) -> "$key=$value" } + "&$iqiyiSignKeyName=$iqiyiSignSalt"
        return md5(signInput).uppercase(Locale.ROOT)
    }

    fun parseIqiyiBaseInfoJson(inputUrl: String, json: String): UrlDanmuMetadata? {
        val base = runCatching {
            JSONObject(json).optJSONObject("data")?.optJSONObject("base_data")
        }.getOrNull() ?: return null
        val title = base.stringValue("title")
        val episodeTitle = base.stringValue("current_video_title").ifBlank { title }
        val order = base.stringValue("current_video_order")
        val phaseTitle = base.stringValue("current_phase_title")
        val episodeLabel = phaseTitle.takeIf { containsEpisodeLabel(it) }
            ?: order.toIntOrNull()?.takeIf { it > 0 }?.let { "第${it}集" }
            ?: ""
        val year = extractYear(base.stringValue("current_video_year"))
            .ifBlank { extractYear(base.stringValue("publish_date")) }
        val poster = normalizeFirstPoster(
            inputUrl,
            base.stringValue("image_url"),
            base.stringValue("horizontal_image_url")
        )
        return UrlDanmuMetadata(
            title = title,
            episodeTitle = episodeTitle,
            posterUrl = poster,
            year = year,
            episodeLabel = episodeLabel,
            platformLabel = "爱奇艺"
        ).takeIf { it.hasUsefulValue() }
    }

    fun parseYoukuJson(inputUrl: String, videoJson: String, showJson: String?): UrlDanmuMetadata? {
        val video = runCatching { JSONObject(videoJson) }.getOrNull() ?: return null
        val showFromVideo = video.optJSONObject("show")
        val show = showJson?.let { runCatching { JSONObject(it) }.getOrNull() }
        val title = show?.stringValue("name")
            ?: showFromVideo?.stringValue("name")
            ?: cleanEpisodeSuffix(video.stringValue("title"))
        val episodeTitle = video.stringValue("title").ifBlank { title }
        val episodeLabel = findEpisodeLabel(episodeTitle)
        val year = extractYear(
            show?.stringValue("releasedate_mainland")
                ?: show?.stringValue("released")
                ?: video.stringValue("published")
        )
        val poster = normalizeFirstPoster(
            inputUrl,
            show?.stringValue("poster_large").orEmpty(),
            show?.stringValue("poster").orEmpty(),
            video.stringValue("bigThumbnail"),
            video.stringValue("thumbnail")
        )
        return UrlDanmuMetadata(
            title = title,
            episodeTitle = episodeTitle,
            posterUrl = poster,
            year = year,
            episodeLabel = episodeLabel,
            platformLabel = "优酷"
        ).takeIf { it.hasUsefulValue() }
    }

    fun parseMangoVideoInfoJson(inputUrl: String, json: String): UrlDanmuMetadata? {
        val info = runCatching {
            JSONObject(json).optJSONObject("data")?.optJSONObject("info")
        }.getOrNull() ?: return null
        val title = info.stringValue("title")
        val episodeTitle = info.stringValue("videoName").ifBlank { title }
        val releaseTime = info.optJSONObject("detail")?.stringValue("releaseTime").orEmpty()
        val poster = normalizeFirstPoster(
            inputUrl,
            info.stringValue("clipImage2"),
            info.stringValue("clipImage"),
            info.stringValue("videoImage")
        )
        return UrlDanmuMetadata(
            title = title,
            episodeTitle = episodeTitle,
            posterUrl = poster,
            year = extractYear(releaseTime),
            episodeLabel = findEpisodeLabel(episodeTitle),
            platformLabel = "芒果TV"
        ).takeIf { it.hasUsefulValue() }
    }

    fun parseBilibiliSeasonJson(inputUrl: String, json: String, preferredEpId: String?): UrlDanmuMetadata? {
        val result = runCatching { JSONObject(json).optJSONObject("result") }.getOrNull() ?: return null
        val title = result.stringValue("season_title").ifBlank { result.stringValue("title") }
        val episodes = result.optJSONArray("episodes")
        val selectedEpisode = (0 until (episodes?.length() ?: 0))
            .asSequence()
            .mapNotNull { episodes?.optJSONObject(it) }
            .firstOrNull { ep -> preferredEpId != null && ep.stringValue("id") == preferredEpId }
            ?: episodes?.optJSONObject(0)
        val episodeIndex = selectedEpisode?.stringValue("title").orEmpty()
        val episodeLongTitle = selectedEpisode?.stringValue("long_title").orEmpty()
        val episodeLabel = episodeIndex.toIntOrNull()?.takeIf { it > 0 }?.let { "第${it}集" }
            ?: findEpisodeLabel(episodeIndex)
        val episodeTitle = listOf(title, episodeLabel, episodeLongTitle)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { title }
        val publish = result.optJSONObject("publish")
        val year = extractYear(publish?.stringValue("pub_time").orEmpty())
            .ifBlank { timestampToYear(selectedEpisode?.optLong("pub_time", 0L) ?: 0L) }
        return UrlDanmuMetadata(
            title = title,
            episodeTitle = episodeTitle,
            posterUrl = normalizeFirstPoster(inputUrl, result.stringValue("cover"), selectedEpisode?.stringValue("cover").orEmpty()),
            year = year,
            episodeLabel = episodeLabel,
            platformLabel = "哔哩哔哩"
        ).takeIf { it.hasUsefulValue() }
    }

    fun parseBilibiliViewJson(inputUrl: String, json: String): UrlDanmuMetadata? {
        val data = runCatching { JSONObject(json).optJSONObject("data") }.getOrNull() ?: return null
        val title = data.stringValue("title")
        val year = timestampToYear(data.optLong("pubdate", 0L))
        return UrlDanmuMetadata(
            title = title,
            episodeTitle = title,
            posterUrl = normalizeFirstPoster(inputUrl, data.stringValue("pic")),
            year = year,
            episodeLabel = "",
            platformLabel = "哔哩哔哩"
        ).takeIf { it.hasUsefulValue() }
    }

    fun parseMiguContentInfoJson(inputUrl: String, json: String): UrlDanmuMetadata? {
        val data = runCatching {
            JSONObject(json).optJSONObject("body")?.optJSONObject("data")
        }.getOrNull() ?: return null
        val playing = data.optJSONObject("playing")
        val h5pics = data.optJSONObject("h5pics")
        val pics = data.optJSONObject("pics")
        val title = data.stringValue("name").ifBlank { playing?.stringValue("name").orEmpty() }
        val year = extractYear(data.stringValue("year")).ifBlank { extractYear(data.stringValue("publishTime")) }
        val poster = normalizeFirstPoster(
            inputUrl,
            h5pics?.stringValue("highResolutionV").orEmpty(),
            pics?.stringValue("highResolutionV").orEmpty(),
            h5pics?.stringValue("highResolutionH").orEmpty(),
            pics?.stringValue("highResolutionH").orEmpty()
        )
        return UrlDanmuMetadata(
            title = title,
            episodeTitle = title,
            posterUrl = poster,
            year = year,
            episodeLabel = findEpisodeLabel(title),
            platformLabel = "咪咕视频"
        ).takeIf { it.hasUsefulValue() }
    }

    fun extractSohuPlaylistId(html: String): String? {
        return Regex("""\b(?:sid|plid)\s*:\s*["']?(\d+)["']?""", RegexOption.IGNORE_CASE)
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
    }

    fun parseSohuVideoListJson(inputUrl: String, json: String): UrlDanmuMetadata? {
        val root = runCatching { JSONObject(stripJsonp(json)) }.getOrNull() ?: return null
        val title = root.stringValue("albumName")
        val firstVideo = root.optJSONArray("videos")?.optJSONObject(0)
        val year = extractYear(root.stringValue("publishYear"))
            .ifBlank { extractYear(firstVideo?.stringValue("publishTime").orEmpty()) }
        return UrlDanmuMetadata(
            title = title,
            episodeTitle = firstVideo?.stringValue("videoName").orEmpty().ifBlank { title },
            posterUrl = normalizeFirstPoster(
                inputUrl,
                root.stringValue("largeVerPicUrl"),
                root.stringValue("albumPicUrl"),
                root.stringValue("largePicUrl"),
                root.stringValue("largeHorPicUrl")
            ),
            year = year,
            episodeLabel = firstVideo?.stringValue("order")?.toIntOrNull()?.takeIf { it > 0 }?.let { "第${it}集" }
                ?: findEpisodeLabel(firstVideo?.stringValue("videoName").orEmpty()),
            platformLabel = "搜狐视频"
        ).takeIf { it.hasUsefulValue() }
    }

    fun parseLeshiHtml(inputUrl: String, html: String): UrlDanmuMetadata? {
        val title = findJsString(html, "pTitle")
            .ifBlank { findMetaContent(html, "irAlbumName") }
            .ifBlank { cleanEpisodeSuffix(findTitle(html)) }
        val episodeTitle = findJsString(html, "title").ifBlank { findTitle(html) }.ifBlank { title }
        val releaseDate = findJsString(html, "releasedate")
        val poster = normalizeFirstPoster(
            inputUrl,
            findJsString(html, "pPicShutu"),
            findJsString(html, "pPic"),
            findJsString(html, "videoPic")
        )
        return UrlDanmuMetadata(
            title = title,
            episodeTitle = episodeTitle,
            posterUrl = poster,
            year = extractYear(releaseDate),
            episodeLabel = findEpisodeLabel(episodeTitle),
            platformLabel = "乐视视频"
        ).takeIf { it.hasUsefulValue() }
    }

    private fun JSONObject.stringValue(name: String): String {
        return opt(name)?.toString()?.trim().orEmpty().takeUnless { it == "null" }.orEmpty()
    }

    private fun UrlDanmuMetadata.hasUsefulValue(): Boolean {
        return title.isNotBlank() || episodeTitle.isNotBlank() || posterUrl.isNotBlank() || year.isNotBlank()
    }

    private fun normalizeFirstPoster(inputUrl: String, vararg candidates: String): String {
        return candidates.asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && it != "null" }
            .map { normalizeUrlDanmuPosterUrl(inputUrl, it) }
            .firstOrNull { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
            .orEmpty()
    }

    private fun containsEpisodeLabel(value: String): Boolean {
        return Regex("""第\s*\d+\s*[集话話期]""").containsMatchIn(value)
    }

    private fun findEpisodeLabel(value: String): String {
        val match = Regex("""第\s*0*(\d{1,4})\s*[集话話期]""").find(value)
            ?: Regex("""(?:^|\s|[_-])0*(\d{1,4})(?:\s|$)""").find(value)
        val number = match?.groupValues?.getOrNull(1)?.toIntOrNull()?.takeIf { it > 0 }
        return number?.let { "第${it}集" }.orEmpty()
    }

    private fun cleanEpisodeSuffix(raw: String): String {
        return raw
            .replace(Regex("""[\r\n\t]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .substringBefore("_高清")
            .substringBefore("_在线观看")
            .substringBefore(" - 在线观看")
            .substringBefore("-在线观看")
            .substringBefore("- 乐视视频")
            .replace(Regex("""\s+0*\d{1,4}$"""), "")
            .replace(Regex("""第\s*\d+\s*[集话話期].*$"""), "")
            .trim(' ', '-', '_', '｜', '|', '《', '》')
    }

    private fun extractYear(value: String): String {
        return Regex("""(?:19|20)\d{2}""").find(value)?.value.orEmpty()
    }

    private fun timestampToYear(seconds: Long): String {
        if (seconds <= 0L) return ""
        return runCatching {
            Instant.ofEpochSecond(seconds).atZone(ZoneId.systemDefault()).year.toString()
        }.getOrDefault("")
    }

    private fun stripJsonp(value: String): String {
        val trimmed = value.trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        return if (start >= 0 && end > start) trimmed.substring(start, end + 1) else trimmed
    }

    private fun findJsString(html: String, key: String): String {
        val escapedKey = Regex.escape(key)
        return Regex("""["']?$escapedKey["']?\s*[:=]\s*(["'])(.*?)\1""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)
            ?.groupValues
            ?.getOrNull(2)
            ?.let { decodeTextEscapes(it).trim() }
            .orEmpty()
    }

    private fun findMetaContent(html: String, property: String): String {
        val wanted = property.lowercase(Locale.ROOT)
        val metaRegex = Regex("""<meta\b[^>]*>""", RegexOption.IGNORE_CASE)
        return metaRegex.findAll(html)
            .firstNotNullOfOrNull { match ->
                val tag = match.value
                val name = readHtmlAttribute(tag, "name").lowercase(Locale.ROOT)
                val propertyValue = readHtmlAttribute(tag, "property").lowercase(Locale.ROOT)
                if (wanted == name || wanted == propertyValue) readHtmlAttribute(tag, "content").takeIf { it.isNotBlank() } else null
            }
            .orEmpty()
    }

    private fun readHtmlAttribute(tag: String, name: String): String {
        return Regex("""\b${Regex.escape(name)}\s*=\s*(["'])(.*?)\1""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(tag)
            ?.groupValues
            ?.getOrNull(2)
            ?.let { decodeTextEscapes(it).trim() }
            .orEmpty()
    }

    private fun findTitle(html: String): String {
        return Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { decodeTextEscapes(it).replace(Regex("\\s+"), " ").trim() }
            .orEmpty()
    }

    private fun decodeTextEscapes(raw: String): String {
        return raw
            .replace("\\/", "/")
            .replace("\\u002F", "/")
            .replace("\\u002f", "/")
            .replace("\\u003A", ":")
            .replace("\\u003a", ":")
            .replace("\\\"", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }
}
