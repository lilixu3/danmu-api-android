# App 链接资料解析

链接匹配的海报、标题和分集由 App 维护，独立内核仓库无需修改。

接口调试与弹幕下载的搜索框都支持直接粘贴视频链接：识别到 http(s) 链接时不再按片名搜索，改用下面的链接资料解析流程，下载时按原始链接请求弹幕。

## 数据流

1. Android 展开受支持的短链接，保留决定分集的参数。
2. 手动匹配请求现有 `/api/v2/search/anime?keyword=<url>&app_url_metadata=1`。
3. App 的 `app-url-metadata.js` 扩展层先让所选内核处理原请求。只有 HTTP 成功且 `success=true` 的搜索响应才会追加 `appUrlMetadata`。直接模式和 Worker 模式使用相同扩展。
4. 按 URL 的真实域名、视频 ID 和父剧集 ID 取资料，复用所选内核的来源方法和网络配置，不通过片名重新搜索。
5. Android 校验扩展版本和请求地址，转换为携带独立地址的分集候选。进入列表定位原链接，选择其他分集时获取弹幕、下载和导出都使用该条目的地址。
6. 远程服务、旧内核或来源接口缺少扩展能力时，回退到 Android 原生解析。资料不足时保留原链接。

## 返回约定

`appUrlMetadata` 包含 `version=1`、`inputUrl`、`canonicalUrl`、`platformLabel`、`title`、`posterUrl`、`year`、`episodeTitle`、`episodeLabel`、`selectedEpisodeId`、`episodes`、`episodesComplete` 和 `notice`。

每集携带 `id`、`title`、`url`、`number`、`label`、`group`、`posterUrl`。分集 ID 与播放地址绑定，不能用同一个原始分享链接代替所有分集地址。

B站普通投稿仅将 `pages` 作为分P，忽略 UP 主 `ugc_season` 合集。番剧保留主分集和特别篇分组；`ep` 或 `p` 指定条目缺失时不选第一集，保留输入链接。`ss` 剧集页提供列表让用户选择。

当旧内核的来源方法只返回列表，无法证明分页完整时，`episodesComplete=false`。UI 显示已获取的播放条目，并提示完整性未确认；空列表仅显示当前链接，不宣称全剧只有一集。

## 来源适配

- B站：视频详情 `pic/pages`，番剧详情 `cover/episodes/section`，支持短链、BV、av、ep、ss 和指定 P。
- 腾讯：URL 的 cid 对应 `getEpisodes`；页面资料只使用匹配 cid 的 COVER_INFO 或页面元数据。
- 爱奇艺：video ID 对应 base_info 和 `getEpisodes`。
- 优酷：video ID → show ID → 剧集资料和 `getEpisodes`。
- 芒果：cid/vid 对应 video/info 和 `getEpisodes`。
- 咪咕：cid 对应 content-info，必要时按响应的 epsID 取所属剧集。
- 搜狐：页面 videoData 的 playlist ID 对应 `getPlaylistData/getEpisodes`。
- 乐视：匹配当前视频 ID 的 __INFO__ 提供所属 pid，再获取分集。
- 西瓜/抖音长视频：gid 对应来源 `getEpisodes`。
- 埋堆堆：vodUuid 对应分集，保留每集 uuid。
- 爱壹帆：mediaKey 对应分集，保留 episodeKey、videoId 和 videoType。
- 红果：series ID 对应分集，保留每集 vid。
- Animeko：Bangumi episode → subject → 分集。
- 巴哈姆特：videoSn 对应详情和分集分组。

网页兜底只取页面元数据或按身份核对过的状态对象，禁止从整页所有 `pic/title/year/episode` 字段拼凑资料。每个平台的线上可用性仍受接口响应、所选内核版本和现有网络/登录配置影响。

## 验证

- `node-tests/app-url-metadata.test.cjs`：14 类来源的数据映射、指定分集、错误身份、推荐区污染、缺失/部分列表、旧内核、权限拒绝和失败降级。
- `node-tests/app-series-refresh.test.cjs`：实际启动 App 宿主，验证直接模式和 Worker 模式的解析扩展与鉴权。
- Kotlin `UrlDanmuMetadata*Test`、`AppUrlMetadataParserTest`、`ManualUrlDanmuSelectionFactoryTest`：短链到详情接口、原生安全兜底、数据契约和每集地址传递。
- 2026-10-20 在线复核 `b23.tv/NZWHH6K`：对应 `BV11xX5BjECL`，返回《康斯坦丁》视频封面、1 个分P和 CID `37024304639`。其他来源的映射测试使用固定响应，不能视为所有来源的线上可用性证明。
