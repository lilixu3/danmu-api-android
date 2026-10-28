package com.example.danmuapiapp.ui.screen.apitest

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 弹幕测试页「收藏」子控制器：收藏列表加载、增删/刷新/定时设置与状态展示。
 *
 * 对外由 [ApiTestViewModel] 转发，行为与拆分前一致。
 */
internal class ApiTestFavoriteController(
    private val scope: CoroutineScope,
    private val executor: ApiTestRequestExecutor,
    private val emitNotice: (String, Boolean) -> Unit,
    private val successfulSearchKeyword: () -> String,
    private val hasAnimeCandidates: () -> Boolean,
    private val scenePrefix: String = "弹幕测试"
) {
    var favoriteSupportState by mutableStateOf(FavoriteSupportState.Unknown)
        private set

    var favoriteItems by mutableStateOf<List<ApiTestFavoriteItem>>(emptyList())
        private set

    var scheduledFavoriteRefreshSupported by mutableStateOf(false)
        private set

    var favoriteLoadError by mutableStateOf<String?>(null)
        private set

    var favoriteOperation by mutableStateOf<FavoriteOperation?>(null)
        private set

    var favoriteOperationKeyword by mutableStateOf<String?>(null)
        private set

    fun favoriteForKeyword(keyword: String): ApiTestFavoriteItem? {
        return findFavoriteForKeyword(favoriteItems, keyword)
    }

    fun loadFavorites(baseUrl: String) {
        if (favoriteSupportState == FavoriteSupportState.Loading) return
        val base = executor.resolveApiBaseUrl(baseUrl)
        if (base == null) {
            favoriteSupportState = FavoriteSupportState.Failed
            favoriteLoadError = "弹幕源 Base URL 无效"
            return
        }
        scope.launch {
            loadFavoritesResolved(base, showLoading = true)
        }
    }

    fun toggleManualFavorite(baseUrl: String, currentQuery: String) {
        val query = currentQuery.trim()
        if (
            query.isBlank() ||
            query != successfulSearchKeyword() ||
            !hasAnimeCandidates() ||
            favoriteOperation != null
        ) {
            emitNotice("请先完成一次有效的关键词搜索", true)
            return
        }
        val existing = findFavoriteForKeyword(favoriteItems, query)
        val operation = if (existing == null) FavoriteOperation.Add else FavoriteOperation.Remove
        val keyword = existing?.keyword ?: query
        runFavoriteMutation(
            baseUrl = baseUrl,
            keyword = keyword,
            operation = operation,
            path = if (operation == FavoriteOperation.Add) "add" else "remove",
            body = buildFavoriteKeywordBody(keyword),
            fallback = if (operation == FavoriteOperation.Add) "收藏成功" else "已取消收藏"
        )
    }

    fun refreshFavorite(baseUrl: String, item: ApiTestFavoriteItem) {
        runFavoriteMutation(
            baseUrl = baseUrl,
            keyword = item.keyword,
            operation = FavoriteOperation.Refresh,
            path = "refresh",
            body = buildFavoriteKeywordBody(item.keyword),
            fallback = "收藏刷新成功"
        )
    }

    fun removeFavorite(baseUrl: String, item: ApiTestFavoriteItem) {
        runFavoriteMutation(
            baseUrl = baseUrl,
            keyword = item.keyword,
            operation = FavoriteOperation.Remove,
            path = "remove",
            body = buildFavoriteKeywordBody(item.keyword),
            fallback = "收藏已删除"
        )
    }

    fun updateFavoriteSchedule(
        baseUrl: String,
        item: ApiTestFavoriteItem,
        schedule: FavoriteScheduleDraft?
    ) {
        val body = runCatching { buildFavoriteScheduleBody(item.keyword, schedule) }
            .getOrElse { throwable ->
                emitNotice(throwable.message ?: "定时刷新设置无效", true)
                return
            }
        runFavoriteMutation(
            baseUrl = baseUrl,
            keyword = item.keyword,
            operation = FavoriteOperation.Schedule,
            path = "schedule",
            body = body,
            fallback = if (schedule == null) "已关闭定时刷新" else "定时刷新设置成功"
        )
    }

    suspend fun loadFavoritesResolved(base: String, showLoading: Boolean): Boolean {
        if (favoriteSupportState == FavoriteSupportState.Loading) return false
        if (showLoading) favoriteSupportState = FavoriteSupportState.Loading
        favoriteLoadError = null
        val url = "$base/api/v2/favorite/list"
        val startedAt = System.currentTimeMillis()
        val result = executor.executeGet(url)
        val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
        var loaded = false
        result.fold(
            onSuccess = { (code, body) ->
                executor.recordSuccess(
                    scene = "$scenePrefix/收藏列表",
                    method = "GET",
                    url = url,
                    statusCode = code,
                    durationMs = elapsed,
                    body = body
                )
                when {
                    code == 404 -> {
                        favoriteSupportState = FavoriteSupportState.Unsupported
                        favoriteItems = emptyList()
                        scheduledFavoriteRefreshSupported = false
                    }
                    code !in 200..299 -> {
                        favoriteSupportState = FavoriteSupportState.Failed
                        favoriteLoadError = favoriteErrorMessage(body, "收藏列表加载失败：HTTP $code")
                    }
                    else -> {
                        runCatching { parseFavoriteListResponse(body) }
                            .onSuccess { parsed ->
                                favoriteItems = parsed.favorites
                                scheduledFavoriteRefreshSupported = parsed.scheduledRefreshSupported
                                favoriteSupportState = FavoriteSupportState.Supported
                                loaded = true
                            }
                            .onFailure { throwable ->
                                favoriteSupportState = FavoriteSupportState.Failed
                                favoriteLoadError = throwable.message ?: "收藏列表响应无效"
                            }
                    }
                }
            },
            onFailure = { throwable ->
                val message = throwable.message ?: "收藏列表加载失败"
                favoriteSupportState = FavoriteSupportState.Failed
                favoriteLoadError = message
                executor.recordFailure(
                    scene = "$scenePrefix/收藏列表",
                    method = "GET",
                    url = url,
                    durationMs = elapsed,
                    message = message
                )
            }
        )
        return loaded
    }

    private fun runFavoriteMutation(
        baseUrl: String,
        keyword: String,
        operation: FavoriteOperation,
        path: String,
        body: String,
        fallback: String
    ) {
        if (favoriteOperation != null) return
        val base = executor.resolveApiBaseUrl(baseUrl)
        if (base == null) {
            emitNotice("弹幕源 Base URL 无效", true)
            return
        }
        val url = "$base/api/v2/favorite/$path"
        scope.launch {
            favoriteOperation = operation
            favoriteOperationKeyword = keyword
            val startedAt = System.currentTimeMillis()
            val result = executor.executeJsonPost(url, body)
            val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
            result.fold(
                onSuccess = { (code, responseBody) ->
                    executor.recordSuccess(
                        scene = "$scenePrefix/收藏/${operation.name.lowercase(Locale.ROOT)}",
                        method = "POST",
                        url = url,
                        statusCode = code,
                        durationMs = elapsed,
                        body = responseBody
                    )
                    if (code in 200..299) {
                        runCatching { parseFavoriteMutationMessage(responseBody, fallback) }
                            .onSuccess { message ->
                                emitNotice(message, false)
                                loadFavoritesResolved(base, showLoading = false)
                            }
                            .onFailure { throwable ->
                                emitNotice(throwable.message ?: fallback, true)
                            }
                    } else {
                        emitNotice(
                            favoriteErrorMessage(responseBody, "$fallback：HTTP $code"),
                            true
                        )
                    }
                },
                onFailure = { throwable ->
                    val message = throwable.message ?: "$fallback 失败"
                    emitNotice(message, true)
                    executor.recordFailure(
                        scene = "$scenePrefix/收藏/${operation.name.lowercase(Locale.ROOT)}",
                        method = "POST",
                        url = url,
                        durationMs = elapsed,
                        message = message
                    )
                }
            )
            favoriteOperation = null
            favoriteOperationKeyword = null
        }
    }
}
