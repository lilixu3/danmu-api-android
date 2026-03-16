package com.example.danmuapiapp.ui.screen.push

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danmuapiapp.domain.model.RequestRecord
import com.example.danmuapiapp.domain.repository.EnvConfigRepository
import com.example.danmuapiapp.domain.repository.RequestRecordRepository
import com.example.danmuapiapp.domain.repository.RuntimeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import javax.inject.Inject

data class PushAnimeCandidate(
    val animeId: Long,
    val title: String,
    val episodeCount: Int,
    val imageUrl: String = ""
)

data class PushEpisodeCandidate(
    val episodeId: Long,
    val episodeNumber: Int,
    val title: String
)

enum class PushClientSupportLevel {
    OfficialDoc,
    RepoDoc,
    CompatibilityVerified
}

data class PushClientSupport(
    val id: String,
    val name: String,
    val note: String,
    val endpointExample: String,
    val docUrl: String,
    val level: PushClientSupportLevel
)

data class PushLanDeviceCandidate(
    val ip: String,
    val isSelf: Boolean,
    val port9978Open: Boolean,
    val verifiedPushApi: Boolean,
    val latencyMs: Int?,
    val ifName: String?,
    val mac: String?
) {
    val targetTemplate: String
        get() = "http://$ip:9978/action?do=refresh&type=danmaku&path="

    val supportLabel: String
        get() = when {
            verifiedPushApi -> "已验证 OK/FongMi 接口"
            port9978Open -> "9978 端口可达"
            else -> "未检测到 9978 接口"
        }
}

@HiltViewModel
class PushDanmuViewModel @Inject constructor(
    runtimeRepository: RuntimeRepository,
    private val envConfigRepo: EnvConfigRepository,
    private val recordRepository: RequestRecordRepository,
    private val httpClient: OkHttpClient
) : ViewModel() {

    val runtimeState = runtimeRepository.runtimeState
    val envVars = envConfigRepo.envVars

    var isSearching by mutableStateOf(false)
        private set

    var isLoadingEpisodes by mutableStateOf(false)
        private set

    var loadingAnimeId by mutableStateOf<Long?>(null)
        private set

    var pushingEpisodeIds by mutableStateOf<Set<Long>>(emptySet())
        private set

    var hasSearchedAnime by mutableStateOf(false)
        private set

    var animeCandidates by mutableStateOf<List<PushAnimeCandidate>>(emptyList())
        private set

    var currentAnime by mutableStateOf<PushAnimeCandidate?>(null)
        private set

    var episodeCandidates by mutableStateOf<List<PushEpisodeCandidate>>(emptyList())
        private set

    var resultText by mutableStateOf("")
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var isScanningLan by mutableStateOf(false)
        private set

    var lanScanStatus by mutableStateOf("未扫描")
        private set

    var lanDevices by mutableStateOf<List<PushLanDeviceCandidate>>(emptyList())
        private set

    val clientSupports: List<PushClientSupport> = listOf(
        PushClientSupport(
            id = "ok",
            name = "OK 影视",
            note = "当前推送页面仅面向 OK 影视设计，已实测支持该接口与 offset/fontSize 参数。",
            endpointExample = "/action?do=refresh&type=danmaku&path=",
            docUrl = "https://github.com/FongMi/TV?tab=readme-ov-file#api",
            level = PushClientSupportLevel.CompatibilityVerified
        )
    )

    fun clearResult() {
        resultText = ""
        errorMessage = null
    }

    fun clearError() {
        errorMessage = null
    }

    fun clearLanScan() {
        lanDevices = emptyList()
        lanScanStatus = "未扫描"
    }

    fun scanLanDevices(runtimeLanUrl: String) {
        if (isScanningLan || isSearching || isLoadingEpisodes) return

        val selfIp = PushLanScanner.resolveSelfLanIpv4(runtimeLanUrl)
        if (selfIp.isNullOrBlank()) {
            errorMessage = "未获取到本机局域网 IP，请连接同一 Wi-Fi 后重试"
            return
        }

        viewModelScope.launch {
            isScanningLan = true
            errorMessage = null
            lanScanStatus = "扫描中：正在探测 $selfIp 所在网段..."
            val startedAt = System.currentTimeMillis()

            val result = withContext(Dispatchers.IO) {
                runCatching { PushLanScanner.scanLan(selfIp = selfIp, httpClient = httpClient) }
            }

            result.fold(
                onSuccess = { scanned ->
                    val filtered = scanned
                        .asSequence()
                        .map { device ->
                            PushLanDeviceCandidate(
                                ip = device.ip,
                                isSelf = device.ip == selfIp || device.ip == "127.0.0.1",
                                port9978Open = device.port9978Open,
                                verifiedPushApi = device.verifiedPushApi,
                                latencyMs = device.latencyMs,
                                ifName = device.ifName,
                                mac = device.mac
                            )
                        }
                        .filter { it.port9978Open || it.verifiedPushApi || it.isSelf }
                        .sortedWith(
                            compareByDescending<PushLanDeviceCandidate> { it.verifiedPushApi }
                                .thenByDescending { it.port9978Open }
                                .thenBy { it.latencyMs ?: Int.MAX_VALUE }
                                .thenBy { it.ip }
                        )
                        .toList()
                    lanDevices = filtered
                    val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
                    lanScanStatus = buildLanScanStatus(
                        selfIp = selfIp,
                        devices = filtered,
                        elapsedMs = elapsed
                    )
                },
                onFailure = { throwable ->
                    lanDevices = emptyList()
                    lanScanStatus = "扫描失败"
                    errorMessage = throwable.message ?: "局域网扫描失败"
                }
            )

            isScanningLan = false
        }
    }

    fun backToAnimeList() {
        currentAnime = null
        episodeCandidates = emptyList()
        loadingAnimeId = null
    }

    fun searchAnime(baseUrl: String, keyword: String) {
        if (isSearching || isLoadingEpisodes) return

        val base = normalizeBaseUrl(baseUrl)
        if (base == null) {
            errorMessage = "弹幕源 Base URL 无效"
            return
        }

        val q = keyword.trim()
        if (q.isBlank()) {
            errorMessage = "请输入搜索关键词"
            return
        }

        val url = "$base/api/v2/search/anime?keyword=${urlEncode(q)}"

        viewModelScope.launch {
            isSearching = true
            errorMessage = null
            val startedAt = System.currentTimeMillis()

            val result = withContext(Dispatchers.IO) {
                requestGet(url)
            }

            val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
            hasSearchedAnime = true

            result.fold(
                onSuccess = { (code, body) ->
                    if (code in 200..299) {
                        val parsed = parseAnimeCandidates(body)
                        animeCandidates = parsed
                        currentAnime = null
                        episodeCandidates = emptyList()
                        resultText = if (parsed.isEmpty()) {
                            "未找到匹配动漫，请尝试其他关键词。"
                        } else {
                            "已找到 ${parsed.size} 个动漫，请点击进入剧集详情。"
                        }
                    } else {
                        animeCandidates = emptyList()
                        currentAnime = null
                        episodeCandidates = emptyList()
                        resultText = ""
                        errorMessage = "搜索失败：HTTP $code"
                    }

                    recordRepository.addRecord(
                        RequestRecord(
                            scene = "弹幕推送/搜索动漫",
                            method = "GET",
                            url = url,
                            statusCode = code,
                            durationMs = elapsed,
                            success = code in 200..299,
                            responseSnippet = body.take(2000)
                        )
                    )
                },
                onFailure = { throwable ->
                    val msg = throwable.message ?: "搜索失败"
                    errorMessage = msg
                    animeCandidates = emptyList()
                    currentAnime = null
                    episodeCandidates = emptyList()
                    recordRepository.addRecord(
                        RequestRecord(
                            scene = "弹幕推送/搜索动漫",
                            method = "GET",
                            url = url,
                            statusCode = null,
                            durationMs = elapsed,
                            success = false,
                            errorMessage = msg
                        )
                    )
                }
            )

            isSearching = false
        }
    }

    fun openAnimeDetail(baseUrl: String, anime: PushAnimeCandidate) {
        if (isSearching || isLoadingEpisodes) return

        val base = normalizeBaseUrl(baseUrl)
        if (base == null) {
            errorMessage = "弹幕源 Base URL 无效"
            return
        }

        val url = "$base/api/v2/bangumi/${anime.animeId}"

        viewModelScope.launch {
            isLoadingEpisodes = true
            loadingAnimeId = anime.animeId
            errorMessage = null
            val startedAt = System.currentTimeMillis()

            val result = withContext(Dispatchers.IO) {
                requestGet(url)
            }

            val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)

            result.fold(
                onSuccess = { (code, body) ->
                    if (code in 200..299) {
                        val parsed = parseEpisodeCandidates(body)
                        currentAnime = anime
                        episodeCandidates = parsed
                        resultText = if (parsed.isEmpty()) {
                            "该动漫暂无可推送剧集。"
                        } else {
                            "已进入《${anime.title}》剧集页，共 ${parsed.size} 集。"
                        }
                    } else {
                        currentAnime = null
                        episodeCandidates = emptyList()
                        resultText = ""
                        errorMessage = "加载剧集失败：HTTP $code"
                    }

                    recordRepository.addRecord(
                        RequestRecord(
                            scene = "弹幕推送/加载剧集",
                            method = "GET",
                            url = url,
                            statusCode = code,
                            durationMs = elapsed,
                            success = code in 200..299,
                            responseSnippet = body.take(2000)
                        )
                    )
                },
                onFailure = { throwable ->
                    val msg = throwable.message ?: "加载剧集失败"
                    errorMessage = msg
                    currentAnime = null
                    episodeCandidates = emptyList()
                    recordRepository.addRecord(
                        RequestRecord(
                            scene = "弹幕推送/加载剧集",
                            method = "GET",
                            url = url,
                            statusCode = null,
                            durationMs = elapsed,
                            success = false,
                            errorMessage = msg
                        )
                    )
                }
            )

            isLoadingEpisodes = false
            loadingAnimeId = null
        }
    }

    fun pushEpisode(
        targetInput: String,
        baseUrl: String,
        episode: PushEpisodeCandidate,
        offsetRaw: String,
        fontRaw: String,
        envDanmuFontSize: Int?
    ) {
        if (isSearching || isLoadingEpisodes) return
        if (pushingEpisodeIds.contains(episode.episodeId)) return

        val base = normalizeBaseUrl(baseUrl)
        if (base == null) {
            errorMessage = "弹幕源 Base URL 无效"
            return
        }

        if (episode.episodeId <= 0L) {
            errorMessage = "无效剧集ID"
            return
        }

        val prepared = when (
            val prepareResult = PushEpisodeRequestComposer.prepareInput(
                targetRaw = targetInput,
                offsetRaw = offsetRaw,
                fontRaw = fontRaw,
                envDanmuFontSize = envDanmuFontSize
            )
        ) {
            is PushEpisodeRequestComposer.PrepareResult.Success -> prepareResult.input
            is PushEpisodeRequestComposer.PrepareResult.Error -> {
                errorMessage = prepareResult.error.fieldMessage
                return
            }
        }

        val request = runCatching {
            PushEpisodeRequestComposer.buildPushRequest(
                sourceBase = base,
                episodeId = episode.episodeId,
                input = prepared
            )
        }.getOrElse {
            errorMessage = it.message ?: "推送地址无效"
            return
        }
        val finalUrl = request.pushUrl

        viewModelScope.launch {
            pushingEpisodeIds = pushingEpisodeIds + episode.episodeId
            errorMessage = null
            val startedAt = System.currentTimeMillis()

            val result = withContext(Dispatchers.IO) {
                requestGet(finalUrl)
            }

            val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)

            result.fold(
                onSuccess = { (code, body) ->
                    resultText = buildString {
                        append("第${episode.episodeNumber}集推送返回：HTTP $code")
                        if (request.extraHint.isNotBlank()) {
                            append("（${request.extraHint}）")
                        }
                        append("\n\n")
                        append(body)
                    }
                    recordRepository.addRecord(
                        RequestRecord(
                            scene = "弹幕推送/推送剧集#${episode.episodeNumber}",
                            method = "GET",
                            url = finalUrl,
                            statusCode = code,
                            durationMs = elapsed,
                            success = code in 200..299,
                            responseSnippet = body.take(2000)
                        )
                    )
                },
                onFailure = { throwable ->
                    val msg = throwable.message ?: "推送失败"
                    errorMessage = "第${episode.episodeNumber}集推送失败：$msg"
                    recordRepository.addRecord(
                        RequestRecord(
                            scene = "弹幕推送/推送剧集#${episode.episodeNumber}",
                            method = "GET",
                            url = finalUrl,
                            statusCode = null,
                            durationMs = elapsed,
                            success = false,
                            errorMessage = msg
                        )
                    )
                }
            )

            pushingEpisodeIds = pushingEpisodeIds - episode.episodeId
        }
    }

    private fun requestGet(url: String): Result<Pair<Int, String>> {
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                response.code to response.body.string()
            }
        }
    }

    private fun parseAnimeCandidates(raw: String): List<PushAnimeCandidate> {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return emptyList()
        val arr = when {
            trimmed.startsWith("[") -> runCatching { JSONArray(trimmed) }.getOrElse { return emptyList() }
            else -> {
                val root = runCatching { JSONObject(trimmed) }.getOrElse { return emptyList() }
                root.optJSONArray("animes") ?: root.optJSONArray("data") ?: JSONArray()
            }
        }

        val out = ArrayList<PushAnimeCandidate>(arr.length())
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val animeId = readLong(item, "animeId", "id")
            val title = readString(item, "animeTitle", "title", "name")
            if (animeId <= 0L || title.isBlank()) continue
            val episodeCount = readInt(item, "episodeCount", "totalEpisodes", "count")
            val imageUrl = readString(item, "imageUrl", "cover", "thumb", "poster", "pic").ifBlank {
                item.optJSONObject("image")?.let { image ->
                    readString(image, "thumb", "poster", "url")
                }.orEmpty()
            }.ifBlank {
                item.optJSONObject("images")?.let { images ->
                    readString(images, "common", "large", "medium", "small")
                }.orEmpty()
            }
            out += PushAnimeCandidate(
                animeId = animeId,
                title = title,
                episodeCount = episodeCount.coerceAtLeast(0),
                imageUrl = imageUrl
            )
        }
        return out.distinctBy { it.animeId }
    }

    private fun parseEpisodeCandidates(raw: String): List<PushEpisodeCandidate> {
        val root = runCatching { JSONObject(raw) }.getOrElse { return emptyList() }
        val bangumi = root.optJSONObject("bangumi")
            ?: root.optJSONObject("data")
            ?: JSONObject()

        val episodes = bangumi.optJSONArray("episodes")
            ?: root.optJSONArray("episodes")
            ?: JSONArray()

        val out = ArrayList<PushEpisodeCandidate>(episodes.length())
        for (i in 0 until episodes.length()) {
            val item = episodes.optJSONObject(i) ?: continue
            val episodeId = readLong(item, "episodeId", "id", "cid")
            if (episodeId <= 0L) continue
            val number = readInt(item, "episodeNumber", "number", "sort", "index")
                .takeIf { it > 0 } ?: (i + 1)
            val title = readString(item, "episodeTitle", "title", "name")
            out += PushEpisodeCandidate(
                episodeId = episodeId,
                episodeNumber = number,
                title = title.ifBlank { "第${number}集" }
            )
        }
        return out
            .distinctBy { it.episodeId }
            .sortedWith(compareBy<PushEpisodeCandidate> { it.episodeNumber }.thenBy { it.episodeId })
    }

    private fun normalizeBaseUrl(baseUrl: String): String? {
        val raw = baseUrl.trim()
        if (raw.isBlank()) return null
        return ensureHttpPrefix(raw).trimEnd('/')
    }

    private fun ensureHttpPrefix(url: String): String {
        val trimmed = url.trim()
        return if (
            trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "http://$trimmed"
        }
    }

    private fun readString(obj: JSONObject, vararg keys: String): String {
        keys.forEach { key ->
            val value = obj.optString(key, "").trim()
            if (value.isNotBlank() && !value.equals("null", ignoreCase = true)) {
                return value
            }
        }
        return ""
    }

    private fun readInt(obj: JSONObject, vararg keys: String): Int {
        keys.forEach { key ->
            if (obj.has(key)) {
                val n = obj.optInt(key, Int.MIN_VALUE)
                if (n != Int.MIN_VALUE) return n
                val text = obj.optString(key, "").trim()
                text.toIntOrNull()?.let { return it }
            }
        }
        return 0
    }

    private fun readLong(obj: JSONObject, vararg keys: String): Long {
        keys.forEach { key ->
            if (obj.has(key)) {
                val n = obj.optLong(key, Long.MIN_VALUE)
                if (n != Long.MIN_VALUE) return n
                val text = obj.optString(key, "").trim()
                text.toLongOrNull()?.let { return it }
            }
        }
        return -1L
    }

    private fun urlEncode(raw: String): String {
        return URLEncoder.encode(raw, Charsets.UTF_8.name())
    }

    private fun buildLanScanStatus(
        selfIp: String,
        devices: List<PushLanDeviceCandidate>,
        elapsedMs: Long
    ): String {
        val verified = devices.count { it.verifiedPushApi }
        val open9978 = devices.count { it.port9978Open }
        val elapsedText = if (elapsedMs < 1000L) "${elapsedMs}ms" else {
            String.format(Locale.getDefault(), "%.1fs", elapsedMs / 1000f)
        }
        return if (devices.isEmpty()) {
            "本机：$selfIp · 未发现可用推送端（耗时 $elapsedText）"
        } else {
            "本机：$selfIp · 可达端口 $open9978 台 · 已验证接口 $verified 台 · 耗时 $elapsedText"
        }
    }
}
