package com.example.danmuapiapp.ui.screen.apitest

import com.example.danmuapiapp.data.network.newOutboundCall
import com.example.danmuapiapp.data.repository.runCatchingCancellable
import com.example.danmuapiapp.data.repository.useCancellableResponse

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlinx.coroutines.CancellationException
import java.math.BigInteger
import java.net.URI
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.TimeUnit

internal class UrlDanmuMetadataResolver(
    httpClient: OkHttpClient
) {
    private val metadataClient = httpClient.newBuilder()
        .callTimeout(6, TimeUnit.SECONDS)
        .build()

    suspend fun resolve(originalUrl: String, apiBase: String? = null): UrlDanmuMetadata? {
        val inputUrl = expandShortUrl(originalUrl)
        val appMetadata = apiBase?.let { resolveFromApp(it, inputUrl) }
        if (appMetadata != null && appMetadata.title.isNotBlank() && appMetadata.posterUrl.isNotBlank()) return appMetadata
        if (!inputUrl.startsWith("http", ignoreCase = true)) {
            return null
        }
        return withContext(Dispatchers.IO) {
            val result = when (UrlDanmuMetadataPlatform.detect(inputUrl)) {
                UrlDanmuMetadataPlatform.Tencent -> resolveByHtml(
                    inputUrl = inputUrl,
                    platformLabel = "腾讯视频",
                    userAgent = DESKTOP_UA
                )
                UrlDanmuMetadataPlatform.Iqiyi -> withHtmlFallback(
                    primary = resolveIqiyi(inputUrl),
                    inputUrl = inputUrl,
                    platformLabel = "爱奇艺",
                    userAgent = DESKTOP_UA
                )
                UrlDanmuMetadataPlatform.Youku -> withHtmlFallback(
                    primary = resolveYouku(inputUrl),
                    inputUrl = inputUrl,
                    platformLabel = "优酷",
                    userAgent = DESKTOP_UA
                )
                UrlDanmuMetadataPlatform.Mango -> withHtmlFallback(
                    primary = resolveMango(inputUrl),
                    inputUrl = inputUrl,
                    platformLabel = "芒果TV",
                    userAgent = DESKTOP_UA
                )
                UrlDanmuMetadataPlatform.Bilibili -> withHtmlFallback(
                    primary = resolveBilibili(inputUrl),
                    inputUrl = inputUrl,
                    platformLabel = "哔哩哔哩",
                    userAgent = DESKTOP_UA
                )
                UrlDanmuMetadataPlatform.Migu -> withHtmlFallback(
                    primary = resolveMigu(inputUrl),
                    inputUrl = inputUrl,
                    platformLabel = "咪咕视频",
                    userAgent = DESKTOP_UA
                )
                UrlDanmuMetadataPlatform.Sohu -> withHtmlFallback(
                    primary = resolveSohu(inputUrl),
                    inputUrl = inputUrl,
                    platformLabel = "搜狐视频",
                    userAgent = DESKTOP_UA
                )
                UrlDanmuMetadataPlatform.Leshi -> withHtmlFallback(
                    primary = resolveLeshi(inputUrl),
                    inputUrl = inputUrl,
                    platformLabel = "乐视视频",
                    userAgent = DESKTOP_UA
                )
                UrlDanmuMetadataPlatform.Xigua -> withHtmlFallback(
                    primary = resolveXigua(inputUrl),
                    inputUrl = inputUrl,
                    platformLabel = "西瓜视频",
                    userAgent = DESKTOP_UA
                )
                UrlDanmuMetadataPlatform.Unknown -> resolveByHtml(
                    inputUrl = inputUrl,
                    platformLabel = "URL",
                    userAgent = MOBILE_UA
                )
            }
            if (appMetadata != null) appMetadata.copy(
                title = appMetadata.title.ifBlank { result?.title.orEmpty() },
                posterUrl = appMetadata.posterUrl.ifBlank { result?.posterUrl.orEmpty() },
                year = appMetadata.year.ifBlank { result?.year.orEmpty() }
            ) else result?.copy(canonicalUrl = result.canonicalUrl.ifBlank { inputUrl })
        }
    }

    private suspend fun expandShortUrl(input: String): String = withContext(Dispatchers.IO) {
        val host = runCatching { URI(input).host?.lowercase(Locale.ROOT) }.getOrNull()
        if (host !in setOf("b23.tv", "bili2233.cn", "v.douyin.com", "iqiyi.cn")) return@withContext input
        val client = metadataClient.newBuilder().followRedirects(false).followSslRedirects(false).build()
        var current = input
        repeat(5) {
            val next = runCatchingCancellable {
                val req = Request.Builder().url(current).header("User-Agent", DESKTOP_UA).build()
                client.newOutboundCall(req).useCancellableResponse { response ->
                    response.header("Location")?.takeIf { response.code in 300..399 }
                        ?.let { response.request.url.resolve(it)?.toString() }
                }
            }.getOrNull() ?: return@withContext current
            if (UrlDanmuMetadataPlatform.detect(next) == UrlDanmuMetadataPlatform.Unknown) return@withContext current
            current = next
            if (runCatching { URI(current).host }.getOrNull() != host) return@withContext current
        }
        current
    }

    private suspend fun resolveFromApp(base: String, input: String): UrlDanmuMetadata? = withContext(Dispatchers.IO) {
        // Remote/older cores simply omit appUrlMetadata; use the native resolver in that case.
        val url = buildUrl("${base.trimEnd('/')}/api/v2/search/anime",
            linkedMapOf("keyword" to input, "app_url_metadata" to "1"))
        try {
            val client = metadataClient.newBuilder().callTimeout(30, TimeUnit.SECONDS).build()
            client.newOutboundCall(Request.Builder().url(url).build()).useCancellableResponse { response ->
                if (!response.isSuccessful) return@useCancellableResponse null
                val body = response.peekBody(2L * 1024L * 1024L).string()
                parseAppUrlMetadata(body, input)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) { null }
    }

    private suspend fun resolveIqiyi(inputUrl: String): UrlDanmuMetadata? {
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
        val body = fetchText(apiUrl, userAgent = DESKTOP_UA, referer = "https://www.iqiyi.com/")
            ?: return null
        return UrlDanmuMetadataPlatformParsers.parseIqiyiBaseInfoJson(inputUrl, body)
    }

    private suspend fun resolveYouku(inputUrl: String): UrlDanmuMetadata? {
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
        val videoJson = fetchText(videoApi, userAgent = DESKTOP_UA) ?: return null
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
            fetchText(showApi, userAgent = DESKTOP_UA)
        } else {
            null
        }
        return UrlDanmuMetadataPlatformParsers.parseYoukuJson(inputUrl, videoJson, showJson)
    }

    private suspend fun resolveMango(inputUrl: String): UrlDanmuMetadata? {
        val match = Regex("""/b/(\d+)/(\d+)\.html""").find(inputUrl) ?: return null
        val cid = match.groupValues[1]
        val vid = match.groupValues[2]
        val apiUrl = buildUrl(
            "https://pcweb.api.mgtv.com/video/info",
            linkedMapOf("cid" to cid, "vid" to vid)
        )
        val body = fetchText(apiUrl, userAgent = DESKTOP_UA, referer = "https://www.mgtv.com/")
            ?: return null
        return UrlDanmuMetadataPlatformParsers.parseMangoVideoInfoJson(inputUrl, body)
    }

    private suspend fun resolveBilibili(inputUrl: String): UrlDanmuMetadata? {
        val isVideo = Regex("""/video/(?:BV[0-9A-Za-z]+|av\d+)""").containsMatchIn(inputUrl)
        val epId = Regex("""/bangumi/play/ep(\d+)""").find(inputUrl)?.groupValues?.getOrNull(1)
            ?: if (isVideo) null else Regex("""[?&]ep_id=(\d+)""").find(inputUrl)?.groupValues?.getOrNull(1)
        if (!epId.isNullOrBlank()) {
            val apiUrl = buildUrl("https://api.bilibili.com/pgc/view/web/season", linkedMapOf("ep_id" to epId))
            val body = fetchText(apiUrl, userAgent = DESKTOP_UA, referer = "https://www.bilibili.com/")
                ?: return null
            return UrlDanmuMetadataPlatformParsers.parseBilibiliSeasonJson(inputUrl, body, epId)
        }

        val seasonId = Regex("""/bangumi/play/ss(\d+)""").find(inputUrl)?.groupValues?.getOrNull(1)
            ?: if (isVideo) null else Regex("""[?&]season_id=(\d+)""").find(inputUrl)?.groupValues?.getOrNull(1)
        if (!seasonId.isNullOrBlank()) {
            val apiUrl = buildUrl("https://api.bilibili.com/pgc/view/web/season", linkedMapOf("season_id" to seasonId))
            val body = fetchText(apiUrl, userAgent = DESKTOP_UA, referer = "https://www.bilibili.com/")
                ?: return null
            return UrlDanmuMetadataPlatformParsers.parseBilibiliSeasonJson(inputUrl, body, null)
        }

        val bvid = Regex("""/(BV[0-9A-Za-z]+)""").find(inputUrl)?.groupValues?.getOrNull(1)
        val aid = Regex("""/av(\d+)""").find(inputUrl)?.groupValues?.getOrNull(1)
        if (!bvid.isNullOrBlank() || !aid.isNullOrBlank()) {
            val params = if (!bvid.isNullOrBlank()) linkedMapOf("bvid" to bvid) else linkedMapOf("aid" to aid!!)
            val apiUrl = buildUrl("https://api.bilibili.com/x/web-interface/view", params)
            val body = fetchText(apiUrl, userAgent = DESKTOP_UA, referer = "https://www.bilibili.com/")
                ?: return null
            return UrlDanmuMetadataPlatformParsers.parseBilibiliViewJson(inputUrl, body)
        }
        return null
    }

    private suspend fun resolveMigu(inputUrl: String): UrlDanmuMetadata? {
        val cid = queryParam(inputUrl, "cid")
            ?: Regex("""cid=(\d+)""").find(inputUrl)?.groupValues?.getOrNull(1)
            ?: return null
        val apiUrl = "https://v3-sc.miguvideo.com/program/v4/cont/content-info/$cid/1"
        val body = fetchText(apiUrl, userAgent = DESKTOP_UA, referer = "https://www.miguvideo.com/")
            ?: return null
        return UrlDanmuMetadataPlatformParsers.parseMiguContentInfoJson(inputUrl, body)
    }

    private suspend fun resolveSohu(inputUrl: String): UrlDanmuMetadata? {
        val html = fetchText(inputUrl, userAgent = DESKTOP_UA, referer = "https://tv.sohu.com/")
            ?: return null
        val pageMetadata = parseUrlDanmuMetadata(inputUrl, html)
        val playlistId = UrlDanmuMetadataPlatformParsers.extractSohuPlaylistId(html)
        if (playlistId.isNullOrBlank()) {
            return pageMetadata?.copy(platformLabel = "搜狐视频")
        }
        val apiUrl = buildUrl("https://pl.hd.sohu.com/videolist", linkedMapOf("playlistid" to playlistId))
        val json = fetchText(apiUrl, userAgent = DESKTOP_UA, referer = inputUrl)
        val apiMetadata = json?.let { UrlDanmuMetadataPlatformParsers.parseSohuVideoListJson(inputUrl, it) }
        return mergeMetadata(primary = apiMetadata, fallback = pageMetadata, platformLabel = "搜狐视频")
    }

    private suspend fun resolveLeshi(inputUrl: String): UrlDanmuMetadata? {
        val videoId = Regex("""/vplay/(\d+)\.html""").find(inputUrl)?.groupValues?.getOrNull(1)
            ?: return null
        val pageUrl = "https://www.le.com/ptv/vplay/$videoId.html"
        val html = fetchText(pageUrl, userAgent = DESKTOP_UA, referer = "https://www.le.com/")
            ?: return null
        val pageMetadata = parseUrlDanmuMetadata(inputUrl, html)
        val leMetadata = UrlDanmuMetadataPlatformParsers.parseLeshiHtml(inputUrl, html)
        return mergeMetadata(primary = leMetadata, fallback = pageMetadata, platformLabel = "乐视视频")
    }

    private suspend fun resolveXigua(inputUrl: String): UrlDanmuMetadata? {
        if (inputUrl.contains("douyin.com", ignoreCase = true)) return null
        val gid = Regex("""/video/(\d+)""").find(inputUrl)?.groupValues?.getOrNull(1)
            ?: Regex("""/(\d{10,})(?:[/?#]|$)""").find(inputUrl)?.groupValues?.getOrNull(1)
            ?: return null
        val pageUrl = "https://m.ixigua.com/video/$gid"
        val html = fetchText(pageUrl, userAgent = MOBILE_UA, referer = "https://m.ixigua.com/")
            ?: return null
        return parseUrlDanmuMetadata(inputUrl, html)?.copy(platformLabel = "西瓜视频")
    }

    private suspend fun resolveByHtml(
        inputUrl: String,
        platformLabel: String,
        userAgent: String
    ): UrlDanmuMetadata? {
        val html = fetchText(inputUrl, userAgent = userAgent)
        return html?.let { parseUrlDanmuMetadata(inputUrl, it)?.copy(platformLabel = platformLabel) }
    }

    private suspend fun withHtmlFallback(
        primary: UrlDanmuMetadata?,
        inputUrl: String,
        platformLabel: String,
        userAgent: String
    ): UrlDanmuMetadata? {
        if (primary != null) return primary
        return resolveByHtml(
            inputUrl = inputUrl,
            platformLabel = platformLabel,
            userAgent = userAgent
        )
    }

    private suspend fun fetchText(
        url: String,
        userAgent: String,
        referer: String? = null,
        maxBytes: Long = 768L * 1024L
    ): String? {
        return runCatchingCancellable {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .apply {
                    if (!referer.isNullOrBlank()) header("Referer", referer)
                    header("Accept", "text/html,application/json,text/plain,*/*")
                }
                .get()
                .build()
            metadataClient.newOutboundCall(request).useCancellableResponse { response ->
                if (response.code !in 200..299) return@useCancellableResponse null
                val contentLength = response.body.contentLength()
                if (contentLength > maxBytes) return@useCancellableResponse null
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

    private fun mergeMetadata(
        primary: UrlDanmuMetadata?,
        fallback: UrlDanmuMetadata?,
        platformLabel: String
    ): UrlDanmuMetadata? {
        if (primary == null && fallback == null) return null
        val p = primary ?: UrlDanmuMetadata()
        val f = fallback ?: UrlDanmuMetadata()
        return p.copy(
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
            val host = runCatching { URI(inputUrl).host?.lowercase(Locale.ROOT) }.getOrNull() ?: return Unknown
            fun matches(domain: String) = host == domain || host.endsWith(".$domain")
            return when {
                matches("qq.com") -> Tencent
                matches("iqiyi.com") || matches("iqiyi.cn") -> Iqiyi
                matches("youku.com") -> Youku
                matches("mgtv.com") -> Mango
                matches("bilibili.com") || matches("b23.tv") || matches("bili2233.cn") -> Bilibili
                matches("miguvideo.com") -> Migu
                matches("sohu.com") -> Sohu
                matches("le.com") -> Leshi
                matches("ixigua.com") || matches("douyin.com") -> Xigua
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
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        if (root.optInt("code", 0) != 0) return null
        val result = root.optJSONObject("result") ?: return null
        val expectedSeason = Regex("""/ss(\d+)""").find(inputUrl)?.groupValues?.get(1)
        if (expectedSeason != null && result.has("season_id") && expectedSeason != result.stringValue("season_id")) return null
        val title = result.stringValue("season_title").ifBlank { result.stringValue("title") }
        val poster = normalizeFirstPoster(inputUrl, result.stringValue("cover"))
        val episodes = buildList {
            fun addEpisodes(array: org.json.JSONArray?, group: String) {
                for (i in 0 until (array?.length() ?: 0)) {
                    val ep = array?.optJSONObject(i) ?: continue
                    val id = ep.stringValue("id").ifBlank { ep.stringValue("ep_id") }
                    if (!id.matches(Regex("\\d+"))) continue
                    val index = ep.stringValue("title")
                    val label = if (index.matches(Regex("\\d+(?:\\.\\d+)?"))) "第${index}集" else index
                    add(UrlDanmuEpisode(id, listOf(title, label, ep.stringValue("long_title"))
                        .filter { it.isNotBlank() }.joinToString(" "),
                        "https://www.bilibili.com/bangumi/play/ep$id", index.toIntOrNull() ?: i + 1,
                        label, group, normalizeFirstPoster(inputUrl, ep.stringValue("cover"))))
                }
            }
            addEpisodes(result.optJSONArray("episodes") ?: result.optJSONObject("main_section")?.optJSONArray("episodes"), "")
            val sections = result.optJSONArray("section")
            for (i in 0 until (sections?.length() ?: 0)) {
                val section = sections?.optJSONObject(i) ?: continue
                addEpisodes(section.optJSONArray("episodes"), section.stringValue("title"))
            }
        }.distinctBy { it.id }
        val selected = episodes.firstOrNull { it.id == preferredEpId }
        val missing = preferredEpId != null && selected == null
        return UrlDanmuMetadata(
            title = title, episodeTitle = selected?.title.orEmpty().ifBlank { title },
            posterUrl = poster.ifBlank { selected?.posterUrl.orEmpty() },
            year = extractYear(result.optJSONObject("publish")?.stringValue("pub_time").orEmpty()),
            episodeLabel = selected?.label.orEmpty(), platformLabel = "哔哩哔哩",
            canonicalUrl = selected?.url ?: inputUrl,
            episodes = if (missing) emptyList() else episodes,
            selectedEpisodeId = selected?.id.orEmpty(), episodesComplete = episodes.isNotEmpty() && !missing,
            notice = if (missing) "未找到链接指定的分集，保留原链接获取弹幕" else ""
        ).takeIf { it.hasUsefulValue() }
    }

    fun parseBilibiliViewJson(inputUrl: String, json: String): UrlDanmuMetadata? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        if (root.optInt("code", 0) != 0) return null
        val data = root.optJSONObject("data") ?: return null
        val bvid = data.stringValue("bvid")
        if (!bvid.matches(Regex("BV[0-9A-Za-z]+"))) return null
        val expected = Regex("""/(BV[0-9A-Za-z]+)""").find(inputUrl)?.groupValues?.get(1)
        val expectedAid = Regex("""/av(\d+)""").find(inputUrl)?.groupValues?.get(1)
        if (expected == null && expectedAid == null) return null
        if (expected != null && expected != bvid) return null
        if (expectedAid != null && expectedAid != data.stringValue("aid")) return null
        val title = data.stringValue("title")
        val pageParam = runCatching { inputUrl.toHttpUrl().queryParameter("p") }.getOrNull()
        val page = if (pageParam == null) 1 else pageParam.toIntOrNull()
        val pages = data.optJSONArray("pages")
        val episodes = (0 until (pages?.length() ?: 0)).mapNotNull { i ->
            val item = pages?.optJSONObject(i) ?: return@mapNotNull null
            val cid = item.stringValue("cid")
            val number = item.optInt("page", i + 1)
            if (cid.isBlank() || number <= 0) return@mapNotNull null
            UrlDanmuEpisode(cid, item.stringValue("part").ifBlank { title },
                "https://www.bilibili.com/video/$bvid?p=$number", number,
                if ((pages?.length() ?: 0) > 1) "P$number" else "正片")
        }
        val selected = episodes.firstOrNull { it.number == page }
        return UrlDanmuMetadata(
            title = title, episodeTitle = selected?.title ?: title,
            posterUrl = normalizeFirstPoster(inputUrl, data.stringValue("pic")),
            year = timestampToYear(data.optLong("pubdate", 0L)),
            episodeLabel = selected?.label.orEmpty(), platformLabel = "哔哩哔哩",
            canonicalUrl = selected?.url ?: inputUrl,
            episodes = if (selected == null) emptyList() else episodes,
            selectedEpisodeId = selected?.id.orEmpty(), episodesComplete = selected != null,
            notice = if (selected == null) "未找到链接指定的分P，保留原链接获取弹幕" else ""
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
        val state = extractUrlPageObject(html, "videoData") ?: return null
        return Regex("""\b(?:sid|plid)\s*:\s*["']?(\d+)["']?""", RegexOption.IGNORE_CASE)
            .find(state)
            ?.groupValues
            ?.getOrNull(1)
    }

    fun parseSohuVideoListJson(inputUrl: String, json: String): UrlDanmuMetadata? {
        val root = runCatching { JSONObject(stripJsonp(json)) }.getOrNull() ?: return null
        val title = root.stringValue("albumName")
        val videos = root.optJSONArray("videos")
        val firstVideo = (0 until (videos?.length() ?: 0)).asSequence()
            .mapNotNull { videos?.optJSONObject(it) }
            .firstOrNull { video -> listOf("url_html5", "pageUrl", "url").any { key ->
                video.stringValue(key).isNotBlank() && video.stringValue(key).substringBefore('?').replace("http://", "https://") ==
                    inputUrl.substringBefore('?').replace("http://", "https://")
            } }
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
        val videoId = Regex("""/vplay/(\d+)""").find(inputUrl)?.groupValues?.get(1) ?: return null
        val state = extractUrlPageObject(html, "__INFO__") ?: return null
        if (!Regex("""\b(?:vid|id)["']?\s*:\s*["']?$videoId\b""").containsMatchIn(state)) return null
        val title = findJsString(state, "pTitle")
            .ifBlank { findMetaContent(html, "irAlbumName") }
            .ifBlank { cleanEpisodeSuffix(findTitle(html)) }
        val episodeTitle = findJsString(state, "title").ifBlank { findTitle(html) }.ifBlank { title }
        val releaseDate = findJsString(state, "releasedate")
        val poster = normalizeFirstPoster(
            inputUrl,
            findJsString(state, "pPicShutu"),
            findJsString(state, "pPic"),
            findJsString(state, "videoPic")
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
