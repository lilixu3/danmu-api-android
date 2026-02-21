package com.example.danmuapiapp.ui.screen.apitest

enum class ApiParamInputType {
    Text,
    Select
}

data class ApiParamConfig(
    val name: String,
    val label: String,
    val required: Boolean = false,
    val placeholder: String = "",
    val inputType: ApiParamInputType = ApiParamInputType.Text,
    val options: List<String> = emptyList(),
    val helper: String = ""
)

data class ApiEndpointConfig(
    val key: String,
    val title: String,
    val method: String,
    val pathTemplate: String,
    val params: List<ApiParamConfig> = emptyList(),
    val hasRawBody: Boolean = false,
    val bodyHint: String = "",
    val forceQueryParams: Set<String> = emptySet()
)

object ApiTestCatalog {

    val endpoints: List<ApiEndpointConfig> = listOf(
        ApiEndpointConfig(
            key = "searchAnime",
            title = "搜索动漫",
            method = "GET",
            pathTemplate = "/api/v2/search/anime",
            params = listOf(
                ApiParamConfig(
                    name = "keyword",
                    label = "关键词",
                    required = true,
                    placeholder = "示例：凡人修仙传"
                )
            )
        ),
        ApiEndpointConfig(
            key = "searchEpisodes",
            title = "搜索剧集",
            method = "GET",
            pathTemplate = "/api/v2/search/episodes",
            params = listOf(
                ApiParamConfig(
                    name = "anime",
                    label = "动漫名称",
                    required = true,
                    placeholder = "示例：凡人修仙传"
                )
            )
        ),
        ApiEndpointConfig(
            key = "matchAnime",
            title = "匹配动漫",
            method = "POST",
            pathTemplate = "/api/v2/match",
            params = listOf(
                ApiParamConfig(
                    name = "fileName",
                    label = "文件名",
                    required = true,
                    placeholder = "示例：凡人修仙传 S01E01"
                )
            )
        ),
        ApiEndpointConfig(
            key = "getBangumi",
            title = "获取番剧详情",
            method = "GET",
            pathTemplate = "/api/v2/bangumi/:animeId",
            params = listOf(
                ApiParamConfig(
                    name = "animeId",
                    label = "动漫ID",
                    required = true,
                    placeholder = "示例：236379"
                )
            )
        ),
        ApiEndpointConfig(
            key = "getComment",
            title = "获取弹幕",
            method = "GET",
            pathTemplate = "/api/v2/comment/:commentId",
            params = listOf(
                ApiParamConfig(
                    name = "commentId",
                    label = "弹幕ID",
                    required = true,
                    placeholder = "示例：10009"
                ),
                ApiParamConfig(
                    name = "format",
                    label = "格式",
                    inputType = ApiParamInputType.Select,
                    options = listOf("json", "xml"),
                    helper = "可选：json 或 xml"
                ),
                ApiParamConfig(
                    name = "segmentflag",
                    label = "分片标志",
                    inputType = ApiParamInputType.Select,
                    options = listOf("true", "false"),
                    helper = "可选：true 或 false"
                )
            )
        ),
        ApiEndpointConfig(
            key = "getSegmentComment",
            title = "获取分片弹幕",
            method = "POST",
            pathTemplate = "/api/v2/segmentcomment",
            params = listOf(
                ApiParamConfig(
                    name = "format",
                    label = "格式",
                    inputType = ApiParamInputType.Select,
                    options = listOf("json", "xml"),
                    helper = "可选：json 或 xml"
                )
            ),
            hasRawBody = true,
            bodyHint = "示例：{\n  \"type\":\"qq\",\n  \"segment_start\":0,\n  \"segment_end\":30000,\n  \"url\":\"https://...\"\n}",
            forceQueryParams = setOf("format")
        )
    )
}
