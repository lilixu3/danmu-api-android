# DanmuXposedModule 拆分任务

目标是把 `app/src/main/java/com/example/danmuapiapp/xposed/DanmuXposedModule.java` 从 4700+ 行拆到 800 行以内，同时不改变 Xposed 注入、手动搜索、设置 overlay、自动推送和现有回归测试的行为。

## Scope
- In: 拆分 `xposed` 包内的大文件、补充/迁移相关测试、保持现有功能和运行时行为不变。
- Out: 不改宿主 App 的业务功能，不改弹幕核心协议，不做 UI 重设计，不引入新功能。

## 拆分原则
- 先拆纯逻辑，再拆 IO 和状态，再拆 UI。
- 每一步都保持 `./gradlew testDebugUnitTest --tests 'com.example.danmuapiapp.xposed.*'` 可通过。
- 旧行为优先保持，源码策略测试只在迁移到新文件后同步调整，不直接删掉约束。
- 单次只移动一组职责，避免同时改动过多调用点。

## 阶段 1: 抽纯模型和纯逻辑
- 抽出 Xposed 注入相关的数据模型：`ShellMedia`、`InjectionSettings`、`CandidateHandle`、`EpisodeCandidate`、`AnimeRef`、`BridgeRow`、`BridgeResult`、`PushGuard`。
- 抽出标题/集数/来源归一化逻辑：`extractEpisodeNumber`、`normalizeSearchTitle`、`normalizeDisplayTitle`、`normalizeSourceKey`、`displaySourceName`。
- 抽出纯格式化/辅助函数：`joinNonBlank`、`formatOffsetSeconds`、`buildPushSummary`、`safeParseInt`。
- 为这批纯逻辑补独立单测，确保拆分后输出一致。

## 阶段 2: 抽设置存储和桥接访问
- 抽出 `readInjectionSettings`、`saveInjectionSettings`、`readEpisodeShowTitles`、`saveEpisodeShowTitles`。
- 抽出 `getRemotePreferencesOrNull`、`commitInjectionSettings`、`commitEpisodeShowTitles`。
- 抽出核心 API 访问和 HTTP/JSON 解析：`searchAnimeDirect`、`loadEpisodesForAnime`、`parseAnimeSearch`、`parseBangumiCandidates`、`httpGet`、`readAll`。

## 阶段 3: 抽播放页手动搜索 UI
- 抽出 `showManualSearchDialog` 及其专用子方法。
- 抽出剧集列表、筛选条、推送历史、弹窗输入框、网格渲染逻辑。
- 保持现有按钮文案、布局约束和 AlertDialog 行为不变。

## 阶段 4: 抽设置 overlay 和宿主视图工具
- 抽出 `showInjectionSettingsOverlay` 及其宿主背景、返回拦截、overlay 布局、设置行构建逻辑。
- 抽出宿主页面锚点识别、背景裁剪、导航关闭守卫。
- 继续保留现有源码策略测试所覆盖的行为。

## 阶段 5: 抽自动推送控制器
- 抽出 `startAutoPushLoopOnce`、`markActivityResumed`、`markPlaybackActivity`、`markActivityPaused`、`markActivityDestroyed`。
- 抽出 `queryBridgeAutoPush`、`beginPushGuard`、`finishPushGuard`、`cleanupPushGuards`、`recordLastPush`、`notifyAutoPush`。
- 抽出 `readShellMedia`、`readShellMediaFromPort`、`selectAutoPollDelay`、`sleepAutoLoopQuietly`。

## 阶段 6: 调整源码策略测试
- 将直接读取 `DanmuXposedModule.java` 的断言，迁移到新的职责文件。
- 保留现有语义测试，继续校验播放弹窗、settings overlay、按钮样式和锚点策略。
- 如果某些断言不再适合放在入口类，改成对新控制器类的字符串校验或行为测试。

## 验收标准
- `DanmuXposedModule.java` 行数降到 800 行以内。
- `./gradlew testDebugUnitTest --tests 'com.example.danmuapiapp.xposed.*'` 通过。
- 播放页注入、设置页 overlay、自动推送和手动搜索功能正常。
- 现有源码策略测试没有因为纯搬运而误报。

## 落地状态
- 已完成拆分，`DanmuXposedModule.java` 降到 601 行。
- 新增职责文件包括：`DanmuXposedTextPolicy`、`DanmuXposedModels`、`DanmuXposedSettingsStore`、`DanmuXposedHttp`、`DanmuXposedBridgeClient`、`DanmuXposedPlaybackControls`、`DanmuXposedHostBackgrounds`、`DanmuXposedSettingsOverlay`、`DanmuXposedManualSearchDialog`、`DanmuXposedEpisodeRepository`、`DanmuXposedPushCoordinator`、`DanmuXposedShellMediaReader`、`DanmuXposedSettingsRowInjector`。
- 当前拆分后的主要大文件也控制在 800 行以内：`DanmuXposedPushCoordinator.java` 798 行、`DanmuXposedSettingsOverlay.java` 789 行、`DanmuXposedManualSearchDialog.java` 735 行。
- 已通过验证：`./gradlew testDebugUnitTest --tests 'com.example.danmuapiapp.xposed.*'`。

## 建议执行顺序
1. 先抽纯模型和纯逻辑。
2. 再抽设置存储和桥接。
3. 再抽两个 UI 大块。
4. 最后抽自动推送循环和宿主视图工具。
