# 项目架构说明

> 本文档描述 WS0–WS9 工程化重构后的实际结构，与
> [`docs/refactoring/MASTER_TRACKING.md`](refactoring/MASTER_TRACKING.md) 配套：
> 追踪文档记录进度与验证证据，本文档记录结构与边界约定。
> 结构变更时必须同步更新本文件；边界由 `scripts/refactoring/check_architecture.py` 自动校验。

## 1. Gradle 模块地图

`settings.gradle.kts` 包含 `:app`、`:core:model`、`:core:common`、`:ui:designsystem`、
`:feature:records` 与 `includeBuild("build-logic")`。

| 模块 | 职责 | 项目依赖 |
|---|---|---|
| `:app` | Android 应用：UI、业务编排、系统集成、原生与 Node 运行时打包 | 全部模块 |
| `:core:model` | 纯领域模型、纯逻辑与领域仓储接口（`domain.model`、`domain.repository`） | 无 |
| `:core:common` | 跨功能共享工具（格式化、比较、路径/字符串、设备兼容判定） | `:core:model` |
| `:ui:designsystem` | 主题、颜色、排版与共用 Compose 组件（`ui.theme`、`ui.component`、`ui.component.liquid`） | `:core:model`、`:core:common` |
| `:feature:records` | 请求记录功能（列表、筛选、统计洞察、详情与复制） | `:core:model`、`:core:common`、`:ui:designsystem` |

依赖方向单向收敛，禁止反向或跨层依赖：

```
:app ──► :feature:records ──► :ui:designsystem ──► :core:common ──► :core:model
  └───────────────────────────────────────────────────────────────────────┘
```

模块迁移刻意**保留原包名**（如 `com.example.danmuapiapp.ui.screen.records`），
以便 app 侧 import 零改动；模块「拥有」哪些包由 `check_architecture.py` 的
`MODULE_RULES` 白名单约定，而不是靠目录结构推断。新增模块必须同时补模块规则。

## 2. `:app` 内部包分层

### `domain/`

已整体迁入 `:core:model`，`:app` 内不再有 `domain` 源码目录，包名不变：

- `domain/model/`：数据模型、枚举与纯逻辑（版本比较、日志噪音过滤、下载命名等）。
- `domain/repository/`：仓储接口等稳定领域抽象。

领域层不得依赖 Android、UI 或具体 data 实现，也不得反向依赖 app 内部包。

### `data/`

- `repository/`：仓储实现及其拆分出的控制器与策略。
  - 核心侧：`CoreRepositoryImpl` 仅保留装配、来源解析、目录位置与转发；
    安装事务、依赖修复、远端数据源、更新检查、PR 组合、回退历史、
    Revision 版本缓存、目录监听、操作/候选状态机等各自独立成文件。
  - 运行时侧：`RuntimeRepositoryImpl` 编排生命周期、状态协调、日志、热重载与网络监听。
  - 其他：`DanmuDownloadRepositoryImpl`、`LocalDanmuRepositoryImpl`、
    `SettingsRepositoryImpl`、`AccessControlRepositoryImpl` 等按功能归属。
- `service/`：系统与运行时基础设施。`NodeService`/`NodeProjectManager` 管理 Node 进程，
  `RootRuntime*` 负责 Root 脚本构建、进程控制、项目同步与健康检查，
  `RuntimePaths`/`RuntimePathResolver` 负责工作目录与偏好，
  其余为更新检查、通知、保活、QuickSettings、配置同步等服务。
- `remote/github/`：`GithubRemoteService`，统一 GitHub Release 与元数据请求。
- `remote/announcement/`：公告中心远程访问。
- `network/`：OkHttp 客户端、代理、DoH DNS、连接池与配额拦截。
- `parser/`：`.env` / JS-lite 配置解析。
- `tunnel/`：frpc 内核安装、隧道监督、命令队列与诊断。
- `util/`：仅保留仍依赖 Android 或 app 内部实现的少量工具；其余共享工具在 `:core:common`。

### `ui/`

- `screen/<feature>/`：按功能一目录，入口文件只做编排；重逻辑放到
  `section/`（区块）、`dialog/`（弹窗）、`support/`（辅助与格式化）、`model/`（页面私有模型）。
  现有功能目录：`home`、`config`、`download`、`core`、`settings`、`localdanmu`、
  `records`、`console`、`diagnostics`、`deviceaccess`、`apitest`、`cache`、`push`、
  `favorites`（接口调试与弹幕推送共用的收藏页）、`tools`。
- `compat/`：兼容界面（Activity、Compose 页面、ViewModel、面板与对话框）。
- `startup/`：启动权限门禁，拆为 Gate 骨架、状态判定、卡片、步骤与 ViewModel。
- `common/`：跨页面控制器（更新安装、代理选择、依赖修复续跑、UI 格式化等）。
- `component/`、`theme/`：主体已迁入 `:ui:designsystem`；app 内仅保留少量仍依赖
  `data` 层的组件（如 `ConnectivityStatus`、`GithubQuotaNoticeHost`）。
- `navigation/`：`Screen` 路由定义。

### 其他

- `xposed/`：LSPosed 模块（Java），与 Compose UI 解耦。
- `di/`：Hilt 聚合模块。
- 顶层：`DanmuApiApplication`、`MainActivity`、`CompatEntryActivity`、`NodeBridge`。

## 3. 边界规则（自动校验）

`scripts/refactoring/check_architecture.py` 校验两类规则，`./gradlew :app:checkRefactorGuardrails` 会执行：

`preBuild`、`check`、`releaseCheck` 均强制依赖重构护栏与 Node 语法检查；直接打包也不能绕过这些静态门禁。

1. app 源码分层：`domain` 不得导入 `data`/`ui`/`android`/`androidx`；`data` 不得导入 `ui`。
2. 模块规则（`MODULE_RULES`）：逐模块校验允许的 `project(...)` 依赖与允许导入的包前缀，
   例如 `:core:model` 禁止任何 Android 导入与项目依赖，`:core:common` 允许 Android API
   但只依赖 `:core:model` 且不得导入 app 侧包。

新增模块、放宽导入或新增项目依赖时，必须同步更新该脚本与 `scripts/refactoring/test_guardrails.py` 的自测。

## 4. 运行时资产与 Node 服务

`app/src/main/assets/nodejs-project/` 是内嵌 Node 服务的根目录，手写资产清单
`app-runtime-assets.json` 是**唯一事实来源**（当前 27 项、含清单共 28 个文件）。

- 校验与复制逻辑集中在 `scripts/refactoring/runtime_assets.py`：只接受相对 POSIX 路径，
  拒绝绝对路径、`.`/`..` 越界与重复项，支持递归子目录，但排除 `node_modules` 与 `config`。
- Kotlin 侧统一经 `data/service/RuntimeAssetManifest.kt` 的 `AppRuntimeAssets` 读取同一清单，
  Root 同步（`RootRuntimeSync`/`RootRuntimeScripts`）、热重载指纹（`RuntimeHotReloadController`）
  与运行时状态校验（`NodeProjectManager`、`NetworkRuntimeStatus`）都据此推导文件集合。
- Node 测试经 `node-tests/runtime-fixture.cjs` 消费同一清单，测试夹具与打包内容一致。

Node 服务按职责拆分，入口只负责组合装配：

- 入口与运行时：`android-server.js`（组合装配）、`main.js`、`server-worker-runtime.js`、
  `worker-proxy.js`、`runtime-polyfills.js`、`startup-failure.js`。
- 配置与日志：`app-config-runtime.js`、`app-runtime-context.js`、`app-server-logging.js`。
- 访问控制与路由：`app-access-control.js`、`app-http-routes.js`、`app-management.js`。
- 弹幕与代理：`app-danmaku-cache.js`、`app-proxy-helper.js`、`app-proxy-routing.js`、
  `app-series-refresh.js`、`app-url-metadata.js`、`app-source-probe.js`、
  `favorite-scheduler-host.js`。
- 出站桥接：`app-outbound-runtime.js`、`app-outbound-bridge.js`、`app-outbound-native.js`、
  `app-outbound-dns.js`、`app-outbound-diagnostics.js`。

Node 回归测试位于 `node-tests/`（`node --test node-tests/*.test.cjs`），
`preBuild` 依赖 `testAppOutboundBridge`，出站桥接测试不通过则无法构建。

## 5. 构建体系

`build-logic` 是独立 included build，`app/build.gradle.kts` 只保留 Android 配置、依赖与插件接入。

- `danmu/DanmuBuildProperties.kt`：版本号、版本码、ABI、混淆/资源压缩、签名、
  Termux 主机判定与原生运行时目录等构建配置的**唯一解析来源**。
- `danmu.runtime-assets`：Node 依赖资产准备与 APK 运行时校验。
- `danmu.native-runtime`：原生运行时准备与输入校验。
- `danmu.quality-gates`：重构护栏任务（`checkRefactorGuardrails`、`checkNodeRuntimeScripts`）、
  发布防呆告警、`releaseCheck` 与出站桥接前置门禁。

常用任务：

```bash
./gradlew :app:checkRefactorGuardrails   # 架构边界、资产清单、硬编码审计、护栏自测
./gradlew :app:checkNodeRuntimeScripts   # Node 资产逐个 node --check
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
node --test node-tests/*.test.cjs
git diff --check
```

## 6. 拆分粒度约定

- 禁止过度封装：单次调用的逻辑不单独拆文件，简单逻辑不硬套抽象类/接口/DTO。
- 迁移优先 `git mv` 剪切，不复制后再删除，避免 diff 虚高。
- 不生成重复样板（重复 try/catch、日志）。
- 只有具备复用价值、职责明确的逻辑才抽成独立模块。

## 7. 新增代码放置指引

- 领域模型与接口 → `:core:model`；跨功能纯工具 → `:core:common`。
- 主题与共用组件 → `:ui:designsystem`；完整功能 → 新建 `:feature:<name>` 并补模块规则。
- app 内业务编排放 `data/repository/` 或 `ui/screen/<feature>/`，远程访问放
  `data/remote/` 或 `data/network/`，页面私有模型放对应功能目录。
- 新增 Node 手写资产必须先加入 `app-runtime-assets.json`，再跑护栏与 Node 测试。
- 不建立万能 `utils` 包；共享逻辑放入语义明确的领域或工具模块。

## 8. 当前边界与遗留

结构层面的已知缺口（进度与验证以 `MASTER_TRACKING.md` 的 WS9/WS10 记录为准）：

- 功能模块目前只有 `:feature:records` 试点，其余功能仍在 `:app` 内按目录分层。
- `:app` 内仍保留少量依赖 `data` 层的 `ui/component` 组件，尚未迁入设计系统。
- WS10 已拆分接口测试、核心历史、下载持久化及本地弹幕传输/导入；`CoreViewModel`、`CompatModeViewModel`、`NodeService` 等仍按独立职责评估后续拆分，不单以行数决定。
- 护栏和单测通过不等于设备验收完成；Root 启停、SAF 失权、权限往返、批量导入取消等场景仍需真机验证。
