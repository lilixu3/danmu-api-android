# Danmu API Android 工程化重构追踪

> 本文件是重构的唯一进度事实来源。状态、统计和验证结果必须在对应改动完成后更新，
> 不能用计划或推测代替实际结果。

## 基线

- 分支：`test`
- 重构前 checkpoint：`bade371`（`checkpoint: 保存 test 分支重构前本地改动`）
- checkpoint 已推送到：`origin/test`
- 起始日期：2026-10-20
- 当前阶段：`WS9 文档与验证收口`（WS8 已完成；WS9 仅剩完整构建待用户确认）
- Kotlin 主代码：约 104,071 行
- Kotlin 测试：约 16,228 行、197 个测试文件
- 手写 Node 入口代码：约 5,975 行
- Node 测试：约 2,448 行、18 个已跟踪测试文件
- Gradle Kotlin DSL：1,778 行
- 最大 Kotlin 文件：`CoreRepositoryImpl.kt`，3,777 行
- 最大 Node 文件：`android-server.js`，2,872 行
- `app/build.gradle.kts`：1,744 行

基线静态检查：

- `git diff --check`：通过
- 顶层 Node JS `node --check`：通过
- 完整 Gradle 构建：按用户要求，在 checkpoint 阶段未执行

## 目标

1. 建立不可绕过的架构边界，业务层不得依赖 UI。
2. 消除运行时资产、Root 同步和测试复制中的硬编码文件清单。
3. 拆分超大 Repository、ViewModel、Compose 页面、Root 控制器和 Node 服务。
4. 建立文件移动与资产复制的可验证安全网，避免重构丢文件。
5. 把构建、原生运行时、Node 资产、签名和发布校验从 `app/build.gradle.kts` 分离。
6. 在包边界稳定后提取 Gradle 模块，而不是一次性大范围改包名。
7. 每个阶段保持行为兼容，并记录可复现的验证证据。

## 工作流状态

状态值：`未开始`、`进行中`、`已完成`、`阻塞`

| ID | 工作流 | 状态 | 验收条件 |
|---|---|---|---|
| WS0 | 安全网、追踪文档、架构边界检查 | 已完成 | 有自动边界检查、文件完整性检查、硬编码清单审计；每阶段可回滚 |
| WS1 | 公共纯粹工具与数据层去 UI 依赖 | 已完成 | `data/domain` 不导入 `ui`；Shell/版本/字节格式工具单一来源 |
| WS2 | Node 运行时资产清单与递归复制 | 已完成 | `app-runtime-assets.json` 是唯一资产清单；Gradle、Root、测试均消费该清单 |
| WS3 | `android-server.js` 模块化 | 已完成 | 入口只负责组合；Worker、配置、日志、安全、路由、弹幕缓存、代理分文件 |
| WS4 | `CoreRepositoryImpl` 拆分 | 已完成 | Repository 仅编排；远端、安装、依赖修复、恢复、监听各自独立 |
| WS5 | `RuntimeRepositoryImpl` 与 Root 控制拆分 | 已完成 | 生命周期、协调、日志、热重载、网络、Root 脚本/进程/同步分离 |
| WS6 | 大型 ViewModel 与 Compose 文件拆分 | 已完成 | ViewModel 只组合控制器；页面入口只编排；无新增超大文件 |
| WS7 | Gradle `build-logic` 拆分 | 已完成 | `app/build.gradle.kts` 只保留 Android 配置、依赖和插件接入 |
| WS8 | Gradle 模块化 | 已完成 | 纯模型、设计系统和已验证的功能边界成为独立模块 |
| WS9 | 文档、测试与持续校验收口 | 进行中 | 架构文档与代码一致；边界和资产测试进入常规检查 |

## 防丢文件与防硬编码规则

### 文件移动

1. 优先使用 `git mv`，保留 Git 历史。
2. 移动提交不得同时修改业务行为。
3. 每个移动提交前后比较待打包运行时资产集合，必须完全一致。
4. 任何新增 Node 子目录都必须同时验证：
   - Android assets 中实际存在；
   - Root 同步后实际存在；
   - 测试临时目录中存在；
   - `require()` 引用可解析。
5. 构建日志、APK 和原生二进制不进入重构提交。

### 运行时资产

1. `app/src/main/assets/nodejs-project/app-runtime-assets.json` 是手写 Node 资产的唯一清单。
2. Kotlin、Gradle、Shell、测试不得维护第二份文件名全集。
3. manifest 路径统一使用相对 POSIX 路径。
4. 复制逻辑必须拒绝绝对路径、`..` 越界和重复路径。
5. manifest 必须能递归包含子目录，但不得把 `node_modules` 当作手写源码复制。

### 架构边界

1. `domain` 不得依赖 Android、UI 或具体 data 实现。
2. `data` 不得依赖 `ui`。
3. UI 不得实例化具体 Repository 实现，兼容运行图等少量例外必须记录并逐项消除。
4. 跨功能共享的纯粹逻辑放在明确命名的 `domain` 或共享工具中，不建立万能 `utils` 包。

### 提交规则

- 一个提交只承担一个可解释的重构目标。
- 纯移动、纯提取、行为修改分开提交。
- 每次提交前更新本文件的“当前进度”和“进度日志”。
- 若验证失败，不把该项标记为已完成；记录失败命令和原因。

## 当前进度

### WS0 安全网与边界

- [x] 保存并推送重构前 checkpoint
- [x] 建立追踪文档
- [x] 建立硬编码清单审计脚本
- [x] 建立运行时资产完整性脚本
- [x] 建立包依赖边界检查
- [x] 将检查接入 Gradle

### WS1 公共工具与依赖方向

- [x] 移除 `data -> ui` 依赖
- [x] 统一 Shell 转义
- [x] 统一版本比较
- [x] 统一字节格式化
- [x] 为提取后的纯逻辑补测试

### WS2 运行时资产清单

- [x] 盘点所有硬编码运行时文件路径
- [x] manifest 支持递归目录
- [x] Kotlin Root 同步改为 manifest 驱动
- [x] Gradle 与 Node 测试改为 manifest 驱动
- [x] 增加缺文件、越界、重复路径测试

### WS3 Node 模块化

- [x] 拆分 Worker 生命周期
- [x] 拆分配置与监听
- [x] 拆分日志
- [x] 拆分访问控制
- [x] 拆分 HTTP/管理路由
- [x] 拆分弹幕准备缓存
- [x] 拆分代理辅助服务
- [x] 入口文件降至合理规模

### WS4 Core Repository

- [x] 候选恢复持久化与路径校验
- [x] 目录监听与忽略策略
- [x] 安装事务、暂存与回滚
- [x] 归档布局与 Root 探测
- [x] Release/Commit/Atom 解析
- [x] 远端 URL 与回退策略
- [x] 依赖修复入口与替换
- [x] Remote Branch/Release/Revision/Compare 数据源迁移
- [x] 安装文件事务、来源元数据与依赖替换控制器化
- [x] 依赖修复入口与 pending 状态机继续解耦
- [x] 来源持久化与安装完成状态控制器化
- [x] 操作状态机控制器化
- [x] Revision 版本缓存与失败 TTL 策略化
- [x] 远端分支来源解析与缓存控制器化
- [x] 更新检查、比较缓存与最新版本标签控制器化
- [x] PR 实验室数据源与 PR 组合准备控制器化
- [x] 下载安装流水线与暂存核心发布控制器化
- [x] 回退、Release/提交历史与 Revision 版本解析控制器化
- [x] 核心状态刷新管线、首屏展示缓存与目录监听控制器化
- [x] 核心操作门与 pending 依赖修复状态机控制器化
- [x] 候选核心确认与恢复流程、候选状态控制器化
- [x] 清理迁移后失效的私有死代码
- [x] 更新计划生成（PR 覆盖度确认）控制器化
- [x] Repository 门面降到合理规模：只保留装配、来源解析、目录位置与转发

### WS5 Runtime Repository 与 Root

WS5 起点（`08c2df8` 之后）：

- `RuntimeRepositoryImpl.kt`：3,047 行
- `RootRuntimeController.kt`：1,428 行
- `RuntimePaths.kt`：770 行
- `NodeService.kt`：1,064 行、`NodeProjectManager.kt`：956 行

拆分顺序：先按文件归属并行拆分 `RuntimeRepositoryImpl`（日志/热重载/网络）、
`RootRuntimeController`（脚本构建/进程探测/项目同步）与 `RuntimePaths`（偏好存储/
工作目录迁移），再由统一验证收口。

- [x] 状态存储（`RuntimeStateStore`）
- [x] 生命周期控制（`RuntimeLifecycleController`）
- [x] 状态协调（`RuntimeStateReconciler`）
- [x] 日志读取与解析（`RuntimeLogController`）
- [x] 热重载（`RuntimeHotReloadController`）
- [x] 网络监听（`RuntimeNetworkMonitorController`）
- [x] Root 脚本构建（`RootRuntimeScripts`）
- [x] Root 进程控制（`RootRuntimeProcessControl`）
- [x] Root 项目同步（`RootRuntimeSync`）
- [x] Root 健康检查（`RootRuntimeHealth`）
- [x] 工作目录偏好存储与迁移（`RuntimeWorkDirPreferences`/`RuntimeWorkDirMigration`）

### WS6 UI

WS6 起点（`0bcdc40` 之后）：

- `DanmuDownloadViewModel.kt`：1,982 行
- `HomeViewModel.kt`：1,578 行、`HomeScreen.kt`：1,937 行、`HomeScreenSections.kt`：1,537 行
- `SettingsViewModel.kt`：1,608 行
- `ConfigDialogEditors.kt`：1,707 行、`ConfigVisualEditors.kt`：1,697 行
- `StartupPermissionGate.kt`：1,626 行
- `PullRequestLabScreen.kt`：1,549 行、`PullRequestLabViewModel.kt`：740 行

拆分顺序：第一轮并行拆三个大型 ViewModel（Download / Home / Settings），
第二轮拆 Compose 侧（Home Screen、Config Editors、Startup Permission Gate、Pull Request Lab）。

- [x] Download ViewModel
- [x] Home ViewModel
- [x] Settings ViewModel
- [x] Home Screen
- [x] Config Editors
- [x] Startup Permission Gate
- [x] Pull Request Lab

### WS7 Gradle

- [x] 签名与版本配置（解析集中到 `build-logic/src/main/kotlin/danmu/DanmuBuildProperties.kt`，`android {}` 只读取结果）
- [x] Node 依赖资产任务（`danmu.runtime-assets` 预编译脚本插件）
- [x] 原生运行时任务（`danmu.native-runtime` 预编译脚本插件）
- [x] frp 校验任务（同上）
- [x] 打包与发布校验任务（`danmu.runtime-assets` 的 APK 运行时校验 + `danmu.quality-gates` 的 `releaseCheck`）

### WS8 Gradle 模块

- [x] 建立 `:core:model` / `:core:common` / `:ui:designsystem` 模块骨架并接入 `settings.gradle.kts`
- [x] 固定模块依赖方向（`:core:model` 无项目依赖、`:core:common` 只依赖 `:core:model`、
      `:ui:designsystem` 只依赖 `:core:*`），并把方向写进 `check_architecture.py`
- [x] `:core:model` 迁入纯模型与纯逻辑（36 个模型 + 18 个测试，包名不变）
- [x] `:core:common` 迁入跨功能共享工具（`data/util` 中 11 个无 app 依赖文件）
- [x] `:ui:designsystem` 迁入主题、颜色、排版与共用组件（27 个文件，含 liquid 子包）
- [x] 功能模块边界评审（模块依赖白名单 + 源码导入规则写入护栏；`AppDialog` 阻塞点已定位）
- [x] 首批功能模块提取（试点 `:feature:records`：3 个文件 + 测试迁入，Hilt 聚合跨模块生效）
- [x] 设计系统收口：`AppDialog`、`Theme` 与 5 个对话框迁入（依赖 `DeviceCompatMode` 上移 `:core:common`）
- [ ] 后续功能模块（`:feature:download`、`:feature:localdanmu` 等）按需提取

第二轮补充（设计系统收口 + 功能模块试点）：

- `DeviceCompatMode` 按用户决策以保留包名迁入 `:core:common`，`:core:common` 规则放宽为允许平台导入（仍禁 app 包、仍只依赖 `:core:model`），护栏自测 9 → 10 项。
- `domain.repository` 两个纯接口（353 行、零 Android 依赖）以保留包名迁入 `:core:model`，为功能模块提供领域抽象；模块补 `api(coroutines-core)`。
- `:ui:designsystem` 第二批迁入 `AppDialog`、`Theme` 与 5 个对话框共 7 个文件（1,723 行）；`Theme → AppDialogHost → AppDialog` 链路打通。仍留原地 4 个：`ConnectivityStatus`（依赖 `data.service`）、`GithubQuotaNoticeHost`（`data.service`）、`CorePullRequestUpdateChoiceDialog`（`ui.common`）、`GlassBottomNavigationBar`（`ui.navigation`）。
- 新增 `:feature:records` 试点模块：3 个文件 + 1 个测试（778 行）迁入，包名不变、app 侧 import 零改动、无可见性放宽；Hilt 聚合任务已从模块 AAR 取到 ViewModel 模块。护栏新增该模块规则。
- 已知观察：records 模块的包仍位于 `com.example.danmuapiapp.ui.screen.records`（刻意保持包名以换取 app 零改动），模块「拥有」的包边界目前靠护栏约定而非结构；`aapt2` 在资源合并时打印过 `No package ID 7f found` 提示，构建成功，留待 WS9 复核。

模块化结果与遗留：

- `:core:model`：纯 Kotlin/JVM，0 个 android/androidx import；app 侧 903 行 import 零改动，仅 3 个跨模块符号放宽为 `public`。
- `:core:common`：11 个纯工具迁入（`DotEnvCodec`、`PortProbe`、`ShellUtils` 等）；6 个依赖 Android 或 app 包的工具（`AppAppearancePrefs`、`SecureStringStore`、`TokenDefaults`、`DeviceCompatMode`、`SharedPrefsSafe`、`RuntimeApiAccessResolver`）留在原地。
- `:ui:designsystem`：`ui/theme` 与 `ui/component`（含 `liquid/`）共 27 个文件迁入；依赖 app 内部实现的 11 个文件（`AppDialog`、`Theme`、5 个对话框等）留在原地。
- 已知阻塞：`AppDialog` 依赖 `data/util/DeviceCompatMode`（`data/service/NodeService` 也在用），而 `Theme.kt` 依赖 `AppDialogHost`，因此 `AppDialog`/`Theme`/5 个对话框暂时无法进设计系统模块。要收口需先把 `DeviceCompatMode` 以保留包名迁入 `:core:common` 并放宽该模块的 Android 导入限制，属独立决策项。
- 模块边界护栏修正：`check_architecture.py` 的模块源码根此前写成 `src/main/java`，实际源码在 `src/main/kotlin`，规则空转；已修正为 kotlin 根并按真实包名重写白名单，护栏自测 9 项恢复通过。
- 验证：`:core:model` 18 suite / 82 项、`:ui:designsystem` 5 suite / 15 项、`:app` 211 suite / 1,071 项，合计 **234 suite / 1,168 项 / 0 失败**（与迁移前一致）；`:app:assembleDebug` 与三个模块 `assembleDebug` 全部通过；Node 216 项、护栏 9 项、架构边界与 `git diff --check` 通过。

### WS9 收口

独立审计（`00e431a`）登记的收口项：

- 粒度复核：仍有 8 个「<100 行 + 单一宿主」文件待逐项判定是否并回宿主
  （`SettingsTvSyncController` 53、`SettingsQuickSettingsTileController` 58、`SettingsGithubAccountController` 90、
  `SettingsRootAutoStartController` 90、`HomeAnnouncementOverlays` 74、`CoreRootProbe` 67、
  `RuntimeWorkDirPreferences` 60、`CoreDirWatcher` 96）。
- 重复实现：`CoreDirWatcher` 与 `RuntimeHotReloadController` 各有一份 `FileObserver` 包装（mask 不同）；
  Home/Settings 的应用更新控制器各有 3 个完全相同的转发方法。均为重构前既有，登记待合并。
- 既有死代码：`ui/screen/config/editor/` 下 8 个 composable 无任何调用
  （`KeywordListEditor`、`IpBlacklistEditor`、`CompactMergeSourcePairsEditor`、`CompactMatchPlatformRulesEditor`、
  `SourceConcurrencyBySourceEditor`、`CompactTitleMappingTableEditor`、`MergeSourcePairsEditor`、`OrderedTokenEditor`）；
  `RootRuntimeController.waitForPort` 同样未被调用。
- 剩余超大文件登记（WS8/WS9 评估）：`ApiTestViewModel` 1,511、`CoreViewModel` 1,419、`CompatModeViewModel` 1,278、
  `DanmuDownloadRepositoryImpl` 1,141、`GithubPullRequestService` 1,126、`NodeService` 1,064、`LocalDanmuViewModel` 1,059。
- [x] 处理上述粒度、重复实现与死代码项（清理 lane，见下「2026-10-22 WS9 清理收口」）
- [x] 重写 `docs/architecture.md`（按 WS8 后的模块结构重写，含依赖图、护栏强制方式、构建与运行时结构）
- [x] 更新 README 开发说明（模块结构、`verifyAll` 聚合验证、两条 Termux 环境坑、四条迁移与新增约定）
- [x] 常规验证任务一次执行（新增 `:app:verifyAll`，实跑通过）
- [ ] 完整构建由用户确认后执行（`assembleRelease`/`releaseCheck` 涉及签名与版本参数）
- [ ] 剩余超大文件（`ApiTestViewModel` 1,511、`CoreViewModel` 1,419、`CompatModeViewModel` 1,278、
      `DanmuDownloadRepositoryImpl` 1,141、`GithubPullRequestService` 1,126、`NodeService` 1,064、
      `LocalDanmuViewModel` 1,059）按需继续拆分

### 2026-10-22 WS9 清理收口

清理 lane 按「可达性分析 + 逐符号 grep + 编译器」三重证据执行，改动全部落在 `app/src/main/java`。

- 粒度复核：登记里 8 个「<100 行 + 单一宿主」文件全部并回宿主，均为同包纯移动、零行为改动。
  `SettingsTvSyncController` 53 / `SettingsQuickSettingsTileController` 58 / `SettingsGithubAccountController` 90 /
  `SettingsRootAutoStartController` 90 → `SettingsViewModel.kt`；`HomeAnnouncementOverlays` 74 → `HomeScreen.kt`；
  `CoreRootProbe` 67 → `CoreRepositoryImpl.kt`；`RuntimeWorkDirPreferences` 60 → `RuntimePaths.kt`。
  `CoreDirWatcher` 96 按下一条改造成共享监听器，不再单独存在。
- 重复实现合并：新增 `data/repository/DirTreeWatcher.kt`（递归 `FileObserver`，mask 与忽略策略由调用方注入），
  替换 `CoreDirWatcher` 与 `RuntimeHotReloadController.WorkDirHotReloadWatcher` 两份逐行相同的实现。
  两者差异原样保留：热重载多监听 `ATTRIB`，忽略策略额外忽略 `config`。相对路径归一与核心忽略策略仍是
  `CoreDirWatchPolicy` 单一来源（`CoreDirWatchPolicyTest` 未改动）。
  Home/Settings 应用更新控制器里 3 个完全相同的纯转发方法（`installDownloadedAppUpdate`、
  `dismissInstallAppUpdateDialog`、`openDownloadsApp`，另有 Home 的 `dismissForegroundAppUpdateMethodDialog`
  与 Settings 的 `dismissAppUpdateMethodDialog`）删除，改由 ViewModel 直接调用各自已有的 `appUpdateInstaller`；
  两个 ViewModel 的对外方法与页面调用点零改动。
- 死代码清理（均确认在 `bade371` 同样无调用，不是本次重构引入）：
  - `ui/screen/config/editor/` 整包收敛：删除 7 个整文件（`AutoMatchMappingEditor`、`ConfigMappingTableEditors`、
    `ConfigMergeSourcePairsEditor`、`ConfigOrderedTokenEditor`、`ConfigSimpleListEditors`、`ConfigTitleMappingEditor`、
    `ConfigTitleOffsetEditor`），以及 `ConfigSpecialEditors` 内 3 个非 Compact 编辑器与 `EntityEditorCard`、
    `ConfigMergeRuleEditors.entityText`/`buildCustomMergeRule`、`AutoMatchMappingModels.validateAutoMatchDraft`、
    `ConfigEditorPrimitives.looseTokens`、`ConfigDanmuColorEditors.replaceWithDefaultColorPool` 与 `DefaultDanmuColorPool`。
    根因：`EnvVarEditDialog` 只对 `COLOR_POOL` 等少数 key 做专用界面，其余一律按 `EnvType` 走通用控件，
    按 key 特判的旧编辑器（含其 `Compact*` 变体）全部悬空；登记只覆盖其中 8 个，本次按可达性补全。
  - `ConfigScreenUtils.kt` 569 → 211 行：删除仅为上述编辑器服务的解析/序列化对（标题映射、标题偏移、
    自定义合并规则、时间轴偏移、匹配平台规则）、对应 row 数据类与私有辅助，以及 `canonicalizePlatformSelection`、
    `togglePlatformSelection`、`formatPlatformSummary`、`List.move`；顺带清掉该文件 39 行失效 import。
  - 其他：`LocalDanmuFormatters.kt`（唯一函数无调用，整文件删除）、`CompatModeSettingsScreen.CompatModeSettingsDialog`、
    `DeviceAccessScreen.primaryActionButtonColors`、`RootRuntimeHealth.waitForPort`（私有且无调用）、
    `RuntimeDependencyPackManager.Companion.readLimited`。
  - 登记未处理：`VideoShellAutoPushState`（`data/service/VideoShellAutoPushLogic.kt`）在 `app/src` 内无引用，
    但形态上是公开数据类，交由功能负责人判定是否保留。
- 验证：`:app:compileDebugKotlin` 通过；`:app:testDebugUnitTest` 210 suite / 1,068 项 / 0 失败（与 WS8 收口一致）；
  `:app:checkRefactorGuardrails` 通过（架构边界、资产清单 27 项 / 28 文件、护栏自测 10 项）；
  `node --test node-tests/*.test.cjs` 216 项通过；`git diff --check` 干净。
- 规模：`app/` 侧 35 文件、+512 / −3,602 行（含并回宿主带来的「删文件 + 追加」记账），净减约 3,000 行。

### 2026-10-22 WS10 核心/兼容 lane（`CoreViewModel` + `CompatModeViewModel`）

按「只拆有独立变更理由的子系统、不具备复用价值的不单独立文件」落地两件事：

- `CoreViewModel.kt` 1,419 → 1,146 行：把核心回退对话框子系统（15 个状态属性、分页与页缓存、
  按提交 sha 补齐版本号、变更详情、待回退提交暂存）剪切为 `CoreRevisionHistoryController.kt`（362 行）。
  纯搬移，仅为去重名改私有名（`loadRevisionPage` → `loadPage`、`cancelRevisionVersionRequests` → `cancelVersionRequests`、
  `applyRevisionVersion` → `applyVersion`、`revisionPageCacheKey`/`cacheRevisionPage` → `pageCacheKey`/`cachePage`、
  `isCurrentRevision*Request` → `isCurrent*Request`）；宿主耦合改为注入：`viewModelScope` → scope 参数，
  `settingsRepo.customCoreSource` → `customSourceFingerprint` 回调，加载失败重选线路 → `onHistoryLoadFailed`，
  远端用量后刷新额度 → `onApiUsage`。
  `CoreViewModel` 保留同名公开属性与函数（属性改为 getter 转发），`CoreRevisionHistoryPanel`/`CoreScreen` 调用点零改动；
  `openRollbackDialog`/`confirmRollback`/`doRollbackTo` 仍留在 ViewModel（依赖代理门与核心变更编排）。
- 去重：删除 `CoreViewModel` 内联的代理选择实现（3 个状态属性 + 私有测速任务 + 5 个入口），
  改用 Home/Settings/Compat/Startup 已在用的 `ui/common/ProxyPickerController`；构造函数新增 `GithubProxySpeedTester`。
  对外属性名与函数签名不变（`showProxyPickerDialog`/`proxySelectedId`/`proxyTestingIds`/`proxyLatencyMap`/
  `selectProxy`/`retestProxySpeed`/`confirmProxySelection`/`dismissProxyPickerDialog`），`CoreScreen` 调用点零改动；
  唯一行为差异是测速改走 `GithubProxySpeedTester.testAll`（与原来逐项并发 `testLatency` 同一实现）。
- `CompatModeViewModel.kt` 1,278 → 1,257 行：`observeState` 中 15 段逐字段
  `launch { flow.collectLatest { _uiState.update { it.copy(x = value) } } }` 收敛为同文件私有
  `observeInto(flow) { state, value -> ... }`（不拆文件、不新增抽象）；`runtimeState` 段因带
  `syncServer.updateHost` 与 keepAlive 重算保持显式，keepAlive 五条流仍走 `refreshKeepAliveUi()`。
- 未动：`CoreViewModel` 其余 8 个 80–120 行簇（工作区刷新、更新检查、分支、依赖修复、GitHub 账号等）与
  `CompatModeViewModel` 其余结构；按评审结论单拆任一个都是为拆而拆。
- 验证（独立 worktree：HEAD `76f3cc5` + 仅本 lane 三个文件，避开并行 lane 的在途编辑）：
  `:app:compileDebugKotlin` 通过；`:app:testDebugUnitTest` 210 suite / 1,068 项 / 0 失败（与 WS9 基线一致）；
  `:app:checkRefactorGuardrails` 通过（含护栏自测 10 项）；主工作树 `check_architecture.py`、
  `audit_runtime_hardcoding.py`、`git diff --check` 通过。原生任务按 WS7 结论以
  `-x :app:prepareNativeRuntime -x :app:verifyNativeRuntimeInputs` 排除，与本次改动无关。

### 2026-10-22 WS10 弹幕测试 lane（`ApiTestViewModel`）

按「只拆有独立变更理由的子系统、不具备复用价值的不单独立文件」落地：

- `ApiTestViewModel.kt` 1,511 → 306 行门面：只保留依赖装配、跨流程状态
  （`errorMessage`、`uiNotice`、`lastApiActionStartedAtMs`、搜索布局偏好）与对外 API 转发。
- 拆出的 6 个同包文件都对应界面上一块独立功能区，不是按行数切：
  - `ApiTestRequestExecutor.kt`（231）：出站 GET/POST/字节请求、请求记录落库、Base URL 归一化、
    二进制响应片段；调试、自动匹配、手动搜索、收藏、导出五条流程共用同一份实现。
  - `ApiTestDebugController.kt`（245）：接口调试页参数拼装、cURL 生成与调试响应/调用记录。
  - `ApiTestAutoMatchController.kt`（241）：文件名自动匹配与 URL 直达解析两条入口。
  - `ApiTestManualSearchController.kt`（392）：关键词/URL 搜索、剧集加载、手动取弹幕（含来源 URL 兜底）与步骤回退。
  - `ApiTestFavoriteController.kt`（246）：收藏列表加载、增删/刷新/定时设置与收藏态展示。
  - `ApiTestExportController.kt`（125）：导出请求、载荷校验与待保存文件状态。
- 共用但不足以独立成模块的 `buildDanmuInsightOrFallback`（55 行，自动匹配与手动搜索共用）移入既有
  `ApiTestDanmuParser.kt`，不新开文件。
- 宿主耦合按既有做法注入：`viewModelScope` → scope 参数，`errorMessage` 读写 → `setError`/`currentError`，
  `emitNotice`/`nextUiEventId` → 回调，跨控制器调用走回调（手动搜索成功后重载收藏）。
- 行为保持证据：对外 API 60 → 60 零差异（属性由 `var x private set` 改为 `val x get()` 转发，函数签名不变）；
  字面量多重集 166 → 166，仅 7 处调用点限定名变化（`urlEncode` → `executor.urlEncode`）与 1 处
  `"二进制响应：$size 字节"` → `"二进制响应：${bytes.size} 字节"`（`ByteArray.toTextSnippet` 改为
  `textSnippet(bytes, contentType)`）。
- 未动：`UrlDanmuMetadataResolver`（822）等本包其它文件不在超大文件登记范围内。
- 验证：`:app:compileDebugKotlin` 通过；`:app:testDebugUnitTest` 210 suite / 1,068 项 / 0 失败；
  `:app:checkRefactorGuardrails`、`:app:checkNodeRuntimeScripts` 通过；`git diff --check` 干净。
- 环境记录：三个 lane 并行时主工作树的 Gradle daemon 被系统回收过一次
  （`Gradle build daemon disappeared unexpectedly`，KSP 缓存也出现过半成品状态）；按 `flock .gradle-team.lock`
  串行后通过，失败残留的 `app/build/kspCaches/debug` 移开后重建即可。

## 验证记录

| 日期 | 范围 | 命令或方法 | 结果 | 备注 |
|---|---|---|---|---|
| 2026-10-20 | checkpoint | `git diff --check` | 通过 | checkpoint 前 |
| 2026-10-20 | checkpoint | 顶层 JS 逐个 `node --check` | 通过 | checkpoint 前 |
| 2026-10-20 | checkpoint | 敏感信息/大文件扫描 | 通过 | 未发现异常 |
| 2026-10-20 | 推送 | `gh_git_push.sh 1 origin test` | 通过 | `3e9dc7e..bade371` |
| 2026-10-20 | WS0 护栏 | `checkRefactorGuardrails` 对应四个脚本 | 通过 | 6 个护栏自测通过 |
| 2026-10-20 | Kotlin 编译 | `./gradlew :app:compileDebugKotlin` | 通过 | 仅存量 deprecation 警告 |
| 2026-10-20 | 目标单测 | VersionComparison、ShellUtils、Tunnel、Root 同步等 6 个测试类 | 通过 | 37 项 |
| 2026-10-20 | 完整 Kotlin 单测 | `./gradlew :app:testDebugUnitTest` | 通过 | 209 个 suite、1031 项 |
| 2026-10-20 | 字节格式化 | `ByteSizeFormatterTest` + 完整 Kotlin 单测 | 通过 | 210 个 suite、1034 项 |
| 2026-10-21 | WS3 日志模块 | `node --test node-tests/*.test.cjs` | 通过 | 176 项，含日志 6 项 |
| 2026-10-21 | WS3 日志模块 | `./gradlew :app:checkRefactorGuardrails :app:checkNodeRuntimeScripts` | 通过 | 资产 22 文件、护栏自测 6 项、JS 语法 18 项 |
| 2026-10-21 | WS3 日志模块 | `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:testAppOutboundBridge` | 通过 | 编译通过；单测 210 suite、1034 项；Node 176 项 |
| 2026-10-21 | WS3 访问控制 | `node --test node-tests/*.test.cjs` | 通过 | 184 项，含访问控制 8 项 |
| 2026-10-21 | WS3 访问控制 | `./gradlew :app:checkRefactorGuardrails :app:checkNodeRuntimeScripts` | 通过 | 资产 23 文件、护栏自测 6 项、JS 语法 19 项 |
| 2026-10-21 | WS3 访问控制 | `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` | 通过 | 编译通过；单测 210 suite、1034 项 |
| 2026-10-21 | WS3 Worker 生命周期 | `node --test node-tests/worker-runtime.test.cjs` | 通过 | 8 项 |
| 2026-10-21 | WS3 Worker 生命周期 | `node --test node-tests/*.test.cjs` | 通过 | 192 项，含 Worker 8 项 |
| 2026-10-21 | WS3 Worker 生命周期 | `./gradlew :app:checkRefactorGuardrails :app:checkNodeRuntimeScripts :app:testAppOutboundBridge` | 通过 | 资产 23 项清单、24 文件；JS 语法 20 项；Node 192 项 |
| 2026-10-21 | WS3 Worker 生命周期 | `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` | 通过 | BUILD SUCCESSFUL；Kotlin 编译与单测增量命中 |
| 2026-10-21 | WS3 Worker 生命周期 | `git diff --check` | 通过 | 无空白错误 |
| 2026-10-21 | WS3 配置与监听 | `node --test node-tests/config-runtime.test.cjs` | 通过 | 5 项 |
| 2026-10-21 | WS3 配置与监听 | `node --test node-tests/*.test.cjs` | 通过 | 197 项，含配置 5 项 |
| 2026-10-21 | WS3 配置与监听 | `node node-tests/parse-dotenv-regression.js` | 通过 | 4 个 dotenv 用例 |
| 2026-10-21 | WS3 配置与监听 | `./gradlew :app:checkRefactorGuardrails :app:checkNodeRuntimeScripts :app:testAppOutboundBridge` | 通过 | 资产 24 项清单、25 文件；JS 语法 21 项；Node 197 项 |
| 2026-10-21 | WS3 配置与监听 | `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` | 通过 | BUILD SUCCESSFUL；Kotlin 编译与单测增量命中 |
| 2026-10-21 | WS3 弹幕准备缓存 | `node --test node-tests/danmaku-cache.test.cjs` | 通过 | 5 项 |
| 2026-10-21 | WS3 弹幕准备缓存 | `node --test node-tests/*.test.cjs` | 通过 | 202 项，含弹幕缓存 5 项 |
| 2026-10-21 | WS3 弹幕准备缓存 | `node node-tests/danmaku-prepare-cache-regression.js` | 通过 | 计数、XML 识别与覆盖参数回归 |
| 2026-10-21 | WS3 弹幕准备缓存 | `./gradlew :app:checkRefactorGuardrails :app:checkNodeRuntimeScripts :app:testAppOutboundBridge` | 通过 | 资产 25 项清单、26 文件；JS 语法 22 项；Node 202 项 |
| 2026-10-21 | WS3 弹幕准备缓存 | `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` | 通过 | BUILD SUCCESSFUL；Kotlin 编译与单测增量命中 |
| 2026-10-21 | WS3 代理辅助服务 | `node --test node-tests/proxy-helper.test.cjs` | 通过 | 4 项 |
| 2026-10-21 | WS3 代理辅助服务 | `node --test node-tests/*.test.cjs` | 通过 | 206 项，含代理辅助 4 项与 host 回归 |
| 2026-10-21 | WS3 代理辅助服务 | `./gradlew :app:checkRefactorGuardrails :app:checkNodeRuntimeScripts :app:testAppOutboundBridge` | 通过 | 资产 26 项清单、27 文件；JS 语法 23 项；Node 206 项 |
| 2026-10-21 | WS3 代理辅助服务 | `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` | 通过 | BUILD SUCCESSFUL；Kotlin 编译与单测增量命中 |
| 2026-10-21 | WS3 HTTP/管理路由 | `node --test node-tests/http-routes.test.cjs` | 通过 | 10 项 |
| 2026-10-21 | WS3 HTTP/管理路由 | `node --test node-tests/*.test.cjs` | 通过 | 216 项，含 HTTP 路由 10 项 |
| 2026-10-21 | WS3 HTTP/管理路由 | `./gradlew :app:checkRefactorGuardrails :app:checkNodeRuntimeScripts :app:testAppOutboundBridge` | 通过 | 资产 27 项清单、28 文件；JS 语法 24 项；Node 216 项 |
| 2026-10-21 | WS3 HTTP/管理路由 | `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` | 通过 | BUILD SUCCESSFUL；Kotlin 编译与单测增量命中 |
| 2026-10-21 | WS3 HTTP/管理路由 | `git diff --check` | 通过 | 无空白错误 |
| 2026-10-21 | WS4 Core Repository | `node --test node-tests/*.test.cjs` | 通过 | 216 项，含 WS3 模块与 host 回归 |
| 2026-10-21 | WS4 Core Repository | `./gradlew :app:checkRefactorGuardrails :app:checkNodeRuntimeScripts :app:testAppOutboundBridge` | 通过 | 资产 27 项清单、28 文件；JS 语法 24 项；护栏 6 项；Node 216 项 |
| 2026-10-21 | WS4 Core Repository | `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` | 通过 | 编译通过；217 suite、1,070 项、0 失败 |
| 2026-10-21 | WS4 Core Repository | `git diff --check` | 通过 | 无空白错误 |
| 2026-10-21 | WS4 收尾复核 | `./gradlew :app:checkRefactorGuardrails` | 通过 | 架构边界、资产清单 27 项/28 文件、护栏 6 项 |
| 2026-10-21 | WS4 收尾复核 | `node --test node-tests/*.test.cjs` | 通过 | 216 项，0 失败 |
| 2026-10-21 | WS4 收尾复核 | `git diff --check` | 通过 | 无空白错误 |
| 2026-10-21 | WS4 远端数据源 | `./gradlew :app:testDebugUnitTest --tests CoreRemoteDataSourceTest --tests CoreRemoteBranchParsingTest` | 通过 | 16 + 7 项；分支、比较、历史、详情、globals、Release |
| 2026-10-21 | WS4 远端数据源 | `./gradlew :app:testDebugUnitTest` | 通过 | BUILD SUCCESSFUL，全量 Kotlin 单测 |
| 2026-10-21 | WS4 远端数据源 | `node --test node-tests/*.test.cjs` | 通过 | 216 项，0 失败 |
| 2026-10-21 | WS4 远端数据源 | `python3 scripts/refactoring/check_architecture.py` | 通过 | 架构边界无违规 |
| 2026-10-21 | WS4 远端数据源 | `python3 scripts/refactoring/test_guardrails.py` | 通过 | 6 项 |
| 2026-10-21 | WS4 远端数据源 | `git diff --check` | 通过 | 无空白错误 |
| 2026-10-21 | WS4 安装控制器 | `./gradlew :app:testDebugUnitTest --tests CoreInstallControllerTest --tests CoreInstallMetadataPolicyTest --tests CoreDependencyRepairPolicyTest --tests CoreDownloadPolicyTest` | 通过 | 5 + 6 + 3 + 4 项 |
| 2026-10-21 | WS4 安装控制器 | `./gradlew :app:testDebugUnitTest` | 通过 | 223 suite、1,111 项、0 失败 |
| 2026-10-21 | WS4 安装控制器 | `python3 scripts/refactoring/check_architecture.py` | 通过 | 架构边界无违规 |
| 2026-10-21 | WS4 安装控制器 | `python3 scripts/refactoring/test_guardrails.py` | 通过 | 6 项 |
| 2026-10-21 | WS4 安装控制器 | `git diff --check` | 通过 | 无空白错误 |
| 2026-10-21 | WS4 状态机与缓存 | `./gradlew :app:testDebugUnitTest --tests CoreInstallControllerTest --tests CoreInstallMetadataPolicyTest --tests CoreDependencyRepairPolicyTest --tests CoreDownloadPolicyTest --tests CoreSourcePersistencePolicyTest --tests CoreInstalledStateControllerTest --tests CoreOperationControllerTest --tests CoreRevisionVersionCacheTest --rerun` | 通过 | 5 + 6 + 6 + 4 + 4 + 4 + 5 + 6 项，共 40 项 |
| 2026-10-21 | WS4 状态机与缓存 | `./gradlew :app:testDebugUnitTest --tests '*' --tests '!com.example.danmuapiapp.data.tunnel.FrpcConfigIntegrationTest'` | 通过（排除环境集成） | 227 suite、1,131 项、0 失败；首次全量在 `FrpcConfigIntegrationTest` 真实 frpc 停止脚本读取处卡住，已终止并排除该用例重跑 |
| 2026-10-21 | WS4 分支来源控制器 | `./gradlew :app:testDebugUnitTest --tests CoreRemoteBranchSourceControllerTest --tests CoreRemoteDataSourceTest --tests CoreRemoteBranchParsingTest --rerun` | 通过 | 6 + 16 + 7 项 |
| 2026-10-21 | WS4 分支来源控制器 | `./gradlew :app:testDebugUnitTest --tests '*' --tests '!com.example.danmuapiapp.data.tunnel.FrpcConfigIntegrationTest'` | 通过（排除环境集成） | 228 suite、1,139 项、0 失败 |
| 2026-10-21 | WS4 状态机与缓存 | `python3 scripts/refactoring/check_architecture.py` | 通过 | 架构边界无违规 |
| 2026-10-21 | WS4 状态机与缓存 | `python3 scripts/refactoring/test_guardrails.py` | 通过 | 6 项 |
| 2026-10-21 | WS4 状态机与缓存 | `node --test node-tests/*.test.cjs` | 通过 | 216 项，0 失败 |
| 2026-10-21 | WS4 状态机与缓存 | `git diff --check` | 通过 | 无空白错误 |
| 2026-10-21 | WS4 更新检查控制器 | `./gradlew :app:testDebugUnitTest --tests CoreUpdateCheckControllerTest` | 通过 | 10 项：版本领先/落后、比较缺失报错、比较缓存、PR 头变化、来源不一致、配额阻断、比较发布、标签失效 |
| 2026-10-21 | WS4 更新检查与 PR 控制器 | `./gradlew :app:checkRefactorGuardrails :app:compileDebugKotlin :app:testDebugUnitTest --tests '*' --tests '!...FrpcConfigIntegrationTest'` | 通过（排除环境集成） | 229 suite、1,149 项、0 失败 |
| 2026-10-21 | WS4 更新检查与 PR 控制器 | `node --test node-tests/*.test.cjs` | 通过 | 216 项，0 失败 |
| 2026-10-21 | WS4 更新检查与 PR 控制器 | `python3 scripts/refactoring/check_architecture.py` | 通过 | 架构边界无违规 |
| 2026-10-21 | WS4 更新检查与 PR 控制器 | `python3 scripts/refactoring/test_guardrails.py` | 通过 | 6 项 |
| 2026-10-21 | WS4 更新检查与 PR 控制器 | `git diff --check` | 通过 | 无空白错误 |
| 2026-10-21 | WS4 下载安装流水线 | `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --tests '*' --tests '!...FrpcConfigIntegrationTest'` | 通过（排除环境集成） | 229 suite、1,149 项、0 失败 |
| 2026-10-21 | WS4 回退与历史控制器 | `./gradlew :app:checkRefactorGuardrails :app:compileDebugKotlin :app:testDebugUnitTest --tests '*' --tests '!...FrpcConfigIntegrationTest'` | 通过（排除环境集成） | 229 suite、1,149 项、0 失败；护栏通过 |
| 2026-10-21 | WS4 回退与历史控制器 | `node --test node-tests/*.test.cjs` | 通过 | 216 项，0 失败 |
| 2026-10-21 | WS4 回退与历史控制器 | `python3 scripts/refactoring/check_architecture.py` | 通过 | 架构边界无违规 |
| 2026-10-21 | WS4 回退与历史控制器 | `python3 scripts/refactoring/test_guardrails.py` | 通过 | 6 项 |
| 2026-10-21 | WS4 回退与历史控制器 | `git diff --check` | 通过 | 无空白错误 |
| 2026-10-21 | WS4 核心状态刷新控制器 | `./gradlew :app:checkRefactorGuardrails :app:compileDebugKotlin :app:testDebugUnitTest --tests '*' --tests '!...FrpcConfigIntegrationTest'` | 通过（排除环境集成） | 229 suite、1,149 项、0 失败；护栏通过 |
| 2026-10-21 | WS4 核心状态刷新控制器 | `node --test node-tests/*.test.cjs` | 通过 | 216 项，0 失败 |
| 2026-10-21 | WS4 核心状态刷新控制器 | `python3 scripts/refactoring/check_architecture.py` | 通过 | 架构边界无违规 |
| 2026-10-21 | WS4 核心状态刷新控制器 | `git diff --check` | 通过 | 无空白错误 |
| 2026-10-21 | WS4 pending 状态机控制器 | `./gradlew :app:checkRefactorGuardrails :app:compileDebugKotlin :app:testDebugUnitTest --tests '*' --tests '!...FrpcConfigIntegrationTest'` | 通过（排除环境集成） | 229 suite、1,149 项、0 失败；护栏通过 |
| 2026-10-21 | WS4 pending 状态机控制器 | `node --test node-tests/*.test.cjs` | 通过 | 216 项，0 失败 |
| 2026-10-21 | WS4 pending 状态机控制器 | `python3 scripts/refactoring/check_architecture.py` | 通过 | 架构边界无违规 |
| 2026-10-21 | WS4 pending 状态机控制器 | `git diff --check` | 通过 | 无空白错误 |
| 2026-10-21 | WS4 候选核心控制器 | `./gradlew :app:checkRefactorGuardrails :app:compileDebugKotlin :app:testDebugUnitTest --tests '*' --tests '!...FrpcConfigIntegrationTest'` | 通过（排除环境集成） | 229 suite、1,149 项、0 失败；护栏通过 |
| 2026-10-21 | WS4 候选核心控制器 | `node --test node-tests/*.test.cjs` | 通过 | 216 项，0 失败 |
| 2026-10-21 | WS4 候选核心控制器 | `python3 scripts/refactoring/check_architecture.py` | 通过 | 架构边界无违规 |
| 2026-10-21 | WS4 候选核心控制器 | `git diff --check` | 通过 | 无空白错误 |
| 2026-10-21 | WS4 更新计划控制器 | `./gradlew :app:testDebugUnitTest --tests CoreUpdateCheckControllerTest` | 通过 | 13 项，含更新计划三例 |
| 2026-10-21 | WS4 更新计划控制器 | `./gradlew :app:checkRefactorGuardrails :app:compileDebugKotlin :app:testDebugUnitTest --tests '*' --tests '!...FrpcConfigIntegrationTest'` | 通过（排除环境集成） | 229 suite、1,152 项、0 失败；护栏通过 |
| 2026-10-21 | WS4 更新计划控制器 | `node --test node-tests/*.test.cjs` | 通过 | 216 项，0 失败 |
| 2026-10-21 | WS4 更新计划控制器 | `python3 scripts/refactoring/check_architecture.py` | 通过 | 架构边界无违规 |
| 2026-10-21 | WS4 更新计划控制器 | `git diff --check` | 通过 | 无空白错误 |
| 2026-10-21 | WS0-WS4 落盘 | `git add`/`git commit` 分 5 个语义提交 | 通过 | `555085b` 护栏与文档、`4e1952d` domain 抽取、`2219eb2` Core 拆分、`17e5b99` Node 拆分与资产清单、`7ebaeb5` 安装续跑与弹窗 toast |
| 2026-10-21 | 推送 | `gh_git_push.sh 1 origin test` | 通过 | `bade371..7ebaeb5`，工作树干净 |
| 2026-10-21 | 提交后复核 | `./gradlew :app:checkRefactorGuardrails` | 通过 | 架构边界、资产清单、护栏自测全通过 |
| 2026-10-21 | WS5 Runtime Repository | `./gradlew :app:compileDebugKotlin` | 通过 | 仅存量告警；拆分后新增 9 个文件全部编译通过 |
| 2026-10-21 | WS5 Runtime Repository | `./gradlew :app:checkRefactorGuardrails :app:checkNodeRuntimeScripts` | 通过 | 架构边界无违规；资产清单 27 项/28 文件；护栏自测 6 项；JS 语法 24 项 |
| 2026-10-21 | WS5 Runtime Repository | `./gradlew :app:testAppOutboundBridge` | 通过 | Node 216 项、0 失败 |
| 2026-10-21 | WS5 Runtime Repository | `./gradlew :app:testDebugUnitTest` | 通过 | 231 suite、1,158 项、0 失败 |
| 2026-10-21 | WS5 Runtime Repository | `node --test node-tests/*.test.cjs` | 通过 | 216 项、0 失败 |
| 2026-10-21 | WS5 Runtime Repository | `python3 scripts/refactoring/check_architecture.py` | 通过 | 架构边界无违规 |
| 2026-10-21 | WS5 Runtime Repository | `git diff --check` | 通过 | 无空白错误 |
| 2026-10-21 | WS5 Root 控制 | `./gradlew :app:compileDebugKotlin` | 通过 | 0 error |
| 2026-10-21 | WS5 Root 控制 | `./gradlew :app:testDebugUnitTest --tests RootRuntimeControllerNodeModulesTest --tests RootRuntimeControllerMarkerTest --tests RootRuntimeStartDecisionTest` | 通过 | 12 + 1 + 2 项 |
| 2026-10-21 | WS5 RuntimePaths | `./gradlew :app:testDebugUnitTest --tests RuntimePathsMigrationTest --tests RuntimePathResolverTest --tests RuntimeVariantKeyResolverTest` | 通过 | 8 + 4 + 2 项 |
| 2026-10-21 | WS5 统一收口复核 | `./gradlew :app:checkRefactorGuardrails :app:compileDebugKotlin :app:testDebugUnitTest` | 通过 | 231 suite、1,158 项、0 失败；护栏通过 |
| 2026-10-21 | WS5 统一收口复核 | `node --test node-tests/*.test.cjs` | 通过 | 216 项，0 失败 |
| 2026-10-21 | WS5 统一收口复核 | `python3 scripts/refactoring/check_architecture.py`、`test_guardrails.py` | 通过 | 架构边界无违规、护栏自测 6 项 |
| 2026-10-21 | WS5 统一收口复核 | 脚本字面量与 API 面独立比对 | 通过 | Root 三引号脚本块归一化后逐块一致；Runtime 11 个脚本块零差异；`RuntimePaths`/`RootRuntimeController` 仅私有函数改名 |
| 2026-10-21 | WS5 统一收口复核 | `git diff --check` | 通过 | 无空白错误 |
| 2026-10-21 | WS6 Download VM | `./gradlew :app:compileDebugKotlin` | 通过 | 0 error |
| 2026-10-21 | WS6 Download VM | `./gradlew :app:testDebugUnitTest :app:checkRefactorGuardrails :app:checkNodeRuntimeScripts` | 通过 | 234 suite、1,168 项、0 失败；护栏自测 6 项、JS 语法 24 项 |
| 2026-10-21 | WS6 Download VM | `python3 scripts/refactoring/check_architecture.py`、`test_guardrails.py` | 通过 | 架构边界无违规、护栏自测 6 项 |
| 2026-10-21 | WS6 Download VM | 搬移代码归一化逐行比对 | 通过 | `processPendingQueue` 561 行与其余搬移方法逐块一致，仅状态归属与回调注入差异 |
| 2026-10-21 | WS6 Download VM | `git diff --check` | 通过 | 无空白错误 |
| 2026-10-21 | WS6 Home VM | `flock .gradle-team.lock ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` | 通过 | 1,168 项、0 失败 |
| 2026-10-21 | WS6 Home VM | 函数名/属性名/字符串字面量双向比对 | 通过 | 89 条字面量零差异；仅 3 个私有函数改名；期间发现并修回一处误改文案 |
| 2026-10-21 | WS6 Settings VM | 独立 worktree `:app:compileDebugKotlin --offline` | 通过 | 隔离其他并行改动后编译成功 |
| 2026-10-21 | WS6 Settings VM | 对外 API 逐名比对 + 字面量多重集比对 | 通过 | API 名单零差异；213 条字面量零差异；13 个界面调用点零改动 |
| 2026-10-21 | WS6 统一收口复核 | `./gradlew :app:checkRefactorGuardrails :app:compileDebugKotlin :app:testDebugUnitTest` | 通过 | 234 suite、1,168 项、0 失败；护栏通过 |
| 2026-10-21 | WS6 统一收口复核 | `node --test node-tests/*.test.cjs` | 通过 | 216 项，0 失败 |
| 2026-10-21 | WS6 统一收口复核 | 三个 ViewModel 字面量多重集独立比对 | 通过 | download 204=204、home 89=89、settings 213=213；差异仅为状态归属前缀 |
| 2026-10-21 | WS6 统一收口复核 | `git diff --check`、`check_architecture.py`、`test_guardrails.py` | 通过 | 无空白错误、边界无违规、护栏自测 6 项 |
| 2026-10-21 | WS6 Home Screen | 隔离 worktree 编译 + 单测 + 护栏 + Node 测试 | 通过 | 1,168 项 0 失败；字面量 188=188 |
| 2026-10-21 | WS6 Config Editors | 隔离 worktree 编译 + 单测（两个 lane 各自） | 通过 | ConfigDialogEditors 150=150、ConfigVisualEditors 375=375 字面量零差异 |
| 2026-10-21 | WS6 第二轮收口 | `./gradlew :app:checkRefactorGuardrails :app:compileDebugKotlin :app:testDebugUnitTest` | 通过 | 234 suite、1,168 项、0 失败 |
| 2026-10-21 | WS6 第二轮收口 | 三个 Compose lane 字面量多重集独立比对 | 通过 | Home Screen 188=188、ConfigDialogEditors 150=150、ConfigVisualEditors 375=375 |
| 2026-10-21 | WS6 拆分粒度收敛 | 合并单次调用的 16 行辅助文件回宿主文件 | 通过 | `HomeCacheFileEventPolicy`→`HomeCacheRefreshController`、`HomeDisplayFormatters`→`HomeViewModel`，测试改为指向宿主文件内的同文件函数 |
| 2026-10-21 | WS6 第二轮收口 | `node --test node-tests/*.test.cjs` | 通过 | 216 项，0 失败 |
| 2026-10-21 | WS6 Startup Gate | 隔离 worktree 编译 + 单测 + 护栏 | 通过 | 1,168 项 0 失败；字面量 125=125；仅 17 处 private→internal |
| 2026-10-21 | WS6 PR Lab | 隔离 worktree + 共享工作树编译/单测 | 通过 | 234 suite、1,168 项 0 失败；字面量 185=185 |
| 2026-10-21 | WS6 第三轮收口 | `./gradlew :app:checkRefactorGuardrails :app:compileDebugKotlin :app:testDebugUnitTest` | 通过 | 234 suite、1,168 项、0 失败；护栏通过 |
| 2026-10-21 | WS6 第三轮收口 | 两个 lane 字面量多重集独立比对 | 通过 | Startup Gate 125=125、PR Lab 185=185，双向零差异 |
| 2026-10-21 | WS6 第三轮收口 | `node --test node-tests/*.test.cjs`、`check_architecture.py`、`git diff --check` | 通过 | 216 项 0 失败、边界无违规、无空白错误 |
| 2026-10-21 | 独立审计 | 护栏脚本 + Node 216 项 + `--offline` 编译 + 字面量/死代码/重复实现扫描 | 通过（含发现） | 发现 1 处文档行数漂移、8 个待判定小文件、2 处既有重复实现、8 个既有死 composable、7 个剩余大文件 |
| 2026-10-21 | WS7 收口复核 | `./gradlew :app:checkRefactorGuardrails :app:compileDebugKotlin :app:testDebugUnitTest -x prepareNativeRuntime -x verifyNativeRuntimeInputs` | 通过（排除环境阻塞） | 234 suite、1,168 项、0 失败；`prepareNativeRuntime` 因宿主 `cmake` 动态链接失败无法运行，非本次改动 |
| 2026-10-21 | WS7 收口复核 | `node --test node-tests/*.test.cjs`、`check_architecture.py`、`git diff --check` | 通过 | 216 项 0 失败、边界无违规、无空白错误 |
| 2026-10-21 | WS7 收口复核 | 任务图 `--dry-run` 迁移前后比对 + 7 项行为 smoke | 通过 | 132 项任务逐行一致；发布防呆、缺依赖开关、ABI/混淆校验行为不变 |
| 2026-10-21 | 原生运行时重建 | `env -u LD_LIBRARY_PATH ./gradlew :app:prepareNativeRuntime :app:verifyNativeRuntimeInputs` | 通过 | 三个 ABI 的 libc++/libc++abi/libunwind 与 JNI 桥重建完成，耗时 5m20s |
| 2026-10-21 | 原生重建后复核 | `./gradlew :app:checkRefactorGuardrails :app:compileDebugKotlin :app:testDebugUnitTest`（无排除项） | 通过 | 234 suite、1,168 项、0 失败；护栏通过 |
| 2026-10-21 | 原生重建后复核 | `node --test node-tests/*.test.cjs` | 通过 | 216 项，0 失败 |
| 2026-10-21 | WS7 打包实跑 | `env -u LD_LIBRARY_PATH ./gradlew :app:assembleDebug` | 通过 | 产出 arm64-v8a / armeabi-v7a / x86_64 三个 debug APK（约 70MB）；`verifyBundledNodeModules`、`verifyNativeRuntimeInputs`、`verifyFrpcDebugKernels`、`verifyPackagedNodeModulesDebug` 全部执行通过 |
| 2026-10-21 | WS7 打包实跑 | 首次 `packageDebug` 失败后重跑 | 通过 | 首跑 `IncrementalSplitterRunnable` 失败为原生运行时重建后的增量状态残留，单独重跑即恢复，非代码问题 |
| 2026-10-21 | WS7 迁移保真 | 三个预编译脚本插件逐行多重集比对 | 通过 | 1,319 行迁移内容与原 `app/build.gradle.kts` 完全一致（缺失 0、多余 0） |
| 2026-10-21 | WS7 任务图 | `./gradlew :app:preBuild :app:assembleDebug :app:releaseCheck :app:checkRefactorGuardrails --dry-run` | 通过 | 迁移前后任务集合逐行一致（132 项，含 finalizer 与 `mustRunAfter`） |
| 2026-10-21 | WS7 行为校验 | 7 项开关/门禁 smoke（发布防呆告警与静默、缺依赖测试开关 Debug/Release、ABI 校验、混淆开关校验、ABI split） | 通过 | 与迁移前语义一致 |
| 2026-10-21 | WS7 收口 | `./gradlew :app:checkRefactorGuardrails :app:compileDebugKotlin --rerun :app:testDebugUnitTest --rerun` | 通过 | 234 suite、1,168 项、0 失败；编译为 `--rerun` 强制重跑 |
| 2026-10-21 | WS7 收口 | `node --test node-tests/*.test.cjs`、`check_architecture.py`、`audit_runtime_hardcoding.py`、`git diff --check` | 通过 | 216 项 0 失败；架构边界无违规；硬编码审计扫描根已含 `build-logic` |
| 2026-10-21 | WS7 环境阻塞 | `:app:prepareNativeRuntime` | 阻塞 | 宿主 `cmake` 因用户 19:30 的 Termux 包升级损坏；任务体逐行未改动，直接执行 `scripts/build-native-deps.py` 同样失败 |
| 2026-10-21 | WS8 模块骨架 | `./gradlew :core:model:assembleDebug :core:common:assembleDebug :ui:designsystem:assembleDebug --dry-run` | 通过 | 三个新模块配置阶段解析成功（AGP `com.android.library` 9.3.1） |
| 2026-10-21 | WS8 模块骨架 | `./gradlew :core:model:assembleDebug :core:common:assembleDebug :ui:designsystem:assembleDebug :app:compileDebugKotlin` | 通过 | BUILD SUCCESSFUL，7m32s；空模块可产出 AAR，`app` 已接通三个模块依赖 |
| 2026-10-21 | WS8 模块骨架 | `./gradlew :app:checkRefactorGuardrails` | 通过 | 架构边界（含 3 条新模块规则）、资产清单 27 项/28 文件、硬编码审计、护栏自测 9 项 |
| 2026-10-21 | WS8 模块骨架 | `python3 scripts/refactoring/check_architecture.py`、`PYTHONPATH=scripts/refactoring python3 -m unittest discover -s scripts/refactoring -p 'test_*.py'` | 通过 | 边界无违规；护栏自测 9 项（新增 3 项模块规则用例） |
| 2026-10-21 | WS8 模块骨架 | `./gradlew :app:testDebugUnitTest` | 通过 | 234 suite、1,168 项、0 失败（接通模块依赖后重跑，无回归） |
| 2026-10-21 | WS8 模块骨架 | `git diff --check` | 通过 | 无空白错误 |
| 2026-10-22 | WS9 清理 | `./gradlew :app:compileDebugKotlin` | 通过 | 死代码清理与并回宿主后编译通过（分三次迭代，逐次修掉删除带出的引用） |
| 2026-10-22 | WS9 清理 | `./gradlew :app:testDebugUnitTest :app:checkRefactorGuardrails` | 通过 | 210 suite、1,068 项、0 失败；架构边界、资产清单 27 项/28 文件、护栏自测 10 项 |
| 2026-10-22 | WS9 清理 | `node --test node-tests/*.test.cjs` | 通过 | 216 项通过 |
| 2026-10-22 | WS9 清理 | `git diff --check` | 通过 | 无空白错误 |
| 2026-10-22 | WS10 core/compat | 独立 worktree `:app:compileDebugKotlin` | 通过 | HEAD `76f3cc5` + 本 lane 三文件，规避并行 lane 在途编辑 |
| 2026-10-22 | WS10 core/compat | 独立 worktree `:app:testDebugUnitTest :app:checkRefactorGuardrails` | 通过 | 210 suite、1,068 项、0 失败；护栏自测 10 项 |
| 2026-10-22 | WS10 core/compat | `check_architecture.py` / `audit_runtime_hardcoding.py` / `git diff --check` | 通过 | 主工作树执行 |

## 进度日志

### 2026-10-20

- 将用户现有本地改动完整保存为 `bade371`，未执行构建。
- 推送到 `origin/test`，未使用 force。
- 建立本追踪文档。
- 补齐 WS0 四个护栏脚本及其自测，接入 Gradle 任务。
- 运行时资产清单扩展到嵌套路径，Root 增量同步和权限修正改为清单驱动。
- 将下载来源解析、本地弹幕批处理模型和资源键移到 `domain`，移除 `data -> ui` 引用。
- 统一 Shell 转义到 `ShellUtils.shellQuote`，修复两处错误的单引号转义实现。
- 将两套版本比较合并为 `domain.model.compareVersions`，保留预发布版本语义。
- 修复迁移遗漏的 UI/测试导入，以及 `AppUpdateService` 安装失败分支的未绑定变量。
- 将 8 处字节格式化收口为 `ui.common.formatByteSize`，统一单位、精度和 `Locale.ROOT` 小数点。

### 2026-10-21

- 将日志文件路径、级别过滤、大小轮转、console 捕获、Worker 日志输出和关闭逻辑提取到 `app-server-logging.js`。
- `android-server.js` 只保留日志实例与引用；`.env` 热刷新、禁用默认值、64KB 到 200MB 边界和 120ms 关闭超时保持不变。
- 新增 `node-tests/server-logging.test.cjs`，覆盖禁用、级别过滤、幂等捕获、轮转、异常文件恢复和关闭流程。
- 将新模块加入 `app-runtime-assets.json`，并接入 `testAppOutboundBridge`。
- 将 IP 规范化、黑名单判定、设备统计、规则持久化和 `/__access-control` 端点提取到 `app-access-control.js`。
- 新增 `node-tests/access-control.test.cjs`，覆盖转发的客户端地址、内部路径放行、设备上限、原子保存、清理规则和鉴权失败。
- 将访问控制模块加入资产清单，并接入 `testAppOutboundBridge`。
- 将 Worker 创建、ready 握手、请求超时、消息协议、退出清理、重试、环境同步和关闭流程提取到 `server-worker-runtime.js`。
- 保留 10 秒 ready 超时、60 秒请求超时、350ms terminate 超时、200ms 关闭检查与 10 秒关闭截止；Set-Cookie、sourceProbe 取消和 `setEnv` 仅同步 active Worker 的语义不变。
- 新增 `node-tests/worker-runtime.test.cjs`，覆盖请求转发、退出重生、单次重试、禁用回退、环境同步、取消、超时和等待关闭。
- 将 Worker 模块加入资产清单并接入 `testAppOutboundBridge`；`android-server.js` 从约 2204 行降至 1894 行。
- 将 `.env` 解析/应用、保留键保护、删除键回滚、配置目录监听、500ms 去抖、2 秒流量检测和监听地址解析提取到 `app-config-runtime.js`。
- 新增 `node-tests/config-runtime.test.cjs`，覆盖配置加载、回滚、原子目录/文件监听、被动 mtime 检测、端口边界和 IPv6 地址格式化。
- 将 `parse-dotenv-regression.js` 改为直接引用新配置模块，避免继续依赖 `android-server.js` 源码切片。
- 将配置模块加入资产清单并接入 `testAppOutboundBridge`；`android-server.js` 从 1894 行降至 1712 行。
- 将弹幕 XML 计数/识别/偏移与字号改写、URL 范围校验、TTL、条目/总字节上限、并发去重和 `/__danmaku/*` 响应提取到 `app-danmaku-cache.js`。
- 新增 `node-tests/danmaku-cache.test.cjs`，覆盖并发命中、过期淘汰、超限、非法目标、鉴权和 HEAD 语义；原回归脚本改为直接加载新模块。
- 将弹幕缓存模块加入资产清单并接入 `testAppOutboundBridge`；`android-server.js` 从 1712 行降至 1413 行。
- 将 `/proxy` 转发、访问控制/鉴权、目标与上游代理解析、按 `PROXY_URL` 需求启停监听和串行同步提取到 `app-proxy-helper.js`。
- 新增 `node-tests/proxy-helper.test.cjs`，覆盖凭据解码、监听复用、需求消失关闭、关闭态、上游请求参数、POST 体、缺失目标和鉴权失败。
- 将代理辅助模块加入资产清单并接入 `testAppOutboundBridge`；`android-server.js` 从 1413 行降至 1274 行。
- 将主 HTTP 服务器的 `security.txt`、指标更新、两次 session guard、`__source-probe`、`__outbound`、`__health`、`__shutdown`、`__access-control`、设备访问记录、`/api/env` 管理写回、弹幕路由、核心转发及 `app_section` 注入提取到 `app-http-routes.js`。
- 保留原路由顺序与语义：`security.txt` 不经过 session guard；管理写回成功后刷新日志级别/日志配置并同步 Worker；普通转发保留原始 URL（含 token 前缀），核心只处理对应路径；弹幕 offset/fontSize 使用干净 URL 请求核心后改写 XML。
- 恢复抽取时误删的 `_stripTokenPrefix` 和 `readRequestBody`，修复真实进程启动失败；受影响 12 项 host 回归全部通过。
- 新增 `node-tests/http-routes.test.cjs`，覆盖 security.txt 提前返回、健康指标、shutdown 延迟触发、访问控制拒绝、管理写回、第二次 session guard、弹幕分派、普通转发、弹幕改写和 `app_section` 注入。
- 将 HTTP 路由模块加入资产清单并接入 `testAppOutboundBridge`；`android-server.js` 从 1274 行降至 942 行，`app-http-routes.js` 为 509 行。
- WS4 启动：`CoreRepositoryImpl.kt` 从 3,777 行拆分至 3,009 行。
- 提取 `CoreRemoteParsing.kt`（Release/Commit/Atom、版本标签、SHA 比较、归档提交、来源状态、分支等价）。
- 提取 `CoreArchiveLayout.kt`（ZIP 路径判定、danmu 目录提取、根级 `package.json`、进度流）与 `CoreRootProbe.kt`。
- 提取 `CoreArtifactPolicy.kt`、`CoreSourceMetadataStore.kt`、`CoreCandidateRecoveryPolicy.kt`、`CoreDirectoryOps.kt`。
- 提取 `CoreCandidateStore.kt`：保留原 SharedPreferences 名称和键，路径校验要求目标目录规范化匹配、Root 路径一致、备份必须位于核心父目录且带 `.backup-` 前缀。
- 提取 `CoreDirWatchPolicy.kt` 与 `CoreDirWatcher.kt`：保持 800ms 去抖、递归注册、核心目录/logs/.cache/node_modules 忽略和路径相对化语义。
- 提取 `CoreInstallTransaction.kt`、`CoreStagingPolicy.kt`：保持事务标记、暂存目录命名、备份替换与失败回滚。
- 提取 `CoreRemoteUrlPolicy.kt`：保持 API zip 直连优先、分支提交历史 URL 顺序和百分号编码。
- 新增测试：`CoreCandidateStoreTest`、`CoreDirWatchPolicyTest`、`CoreStagingPolicyTest`、`CoreRemoteUrlPolicyTest`，并扩充目录操作、解析和元数据测试。
- 修复 `InjectedPlaybackDialogPolicyTest` 的旧源码守卫，改为同时检查已拆分出的 `app-http-routes.js`。
- 修复 `security-discovery-host` 端口分配竞态，将 direct/Worker 用例串行执行。
- 提取 `CoreRemoteBranchParsing.kt`：默认分支 JSON/HTML、分支列表分页、HTML 回退、分支头提交解析和缓存键，均由 JVM 测试覆盖。
- 提取 `CoreRemoteDataSource.kt`：默认分支、分支列表、分支头、分支 zip、Release、globals 版本、提交历史/Atom 回退、Revision 分页与搜索、Revision 详情文件补丁、Compare 和下载候选；Repository 只保留缓存、状态发布与编排。
- 数据源通过 `CoreRemoteTransport` 隔离 `GithubRemoteService`，JVM 测试使用假传输验证请求顺序、分页终止、作者/关键词回退和错误路径。
- 保持原有缓存语义：`defaultBranchCache`、`branchRemoteSourceCache`、`latestRemoteVersionCache`、`revisionVersionCache`、`revisionVersionFailureCache`、`revisionGlobalsPathCache`、`updateComparisonCache` 仍归 Repository；比较缓存 24 项清空阈值未变。
- 新增 `CoreRemoteBranchParsingTest`、`CoreRemoteDataSourceTest`；`CoreRepositoryImpl.kt` 从 3,777 行降至约 2,517 行。
- 提取 `CoreInstallController.kt`：暂存核心整理、依赖校验、原子替换、Root 同步校验、候选恢复记录、失败回滚及已安装依赖替换均独立；Repository 仅保留操作互斥、状态流和 pending mutation 交接。
- 提取 `CoreInstallMetadataPolicy.kt`：Release 安装与回退来源元数据统一构造，保持 `release.version` 优先、归档提交/SHA 回退及 tag head 懒查询语义。
- 提取 `CoreDependencyRepairPolicy.kt`：保持 RuntimeStart 默认升级为 Start、Restart 优先合并和“启动/重启/修复”动作文案。
- 提取 `CoreDownloadPolicy.kt`：保持 HTTP 401/403、404 与其他状态码错误文案，以及 300ms 进度节流、未知长度和大小时钳制规则。
- 新增 `CoreInstallControllerTest`、`CoreInstallMetadataPolicyTest`、`CoreDependencyRepairPolicyTest`、`CoreDownloadPolicyTest`。
- `CoreRepositoryImpl.kt` 从约 2,517 行降至 2,358 行，`CoreInstallController.kt` 为 126 行。
- 提取 `CoreSourcePersistencePolicy.kt`：来源仅在真正变化时写设置；标准变体只更新同仓库分支。
- 提取 `CoreInstalledStateController.kt`：持久化已解析来源，安装完成后清理过期更新标记并等待刷新。
- 提取 `CoreOperationController.kt`：核心任务互斥、操作 ID、Running/AwaitingDependencyRepair 流转和失效暂存清理独立；Repository 保留同步包装方法。
- 扩充 `CoreDependencyRepairPolicy.kt`：缺包去重排序、同缺包请求复用和恢复动作合并。
- 提取 `CoreRevisionVersionCache.kt`：提交版本缓存键、60 秒失败 TTL、首选 globals 路径和 2,048 条清空规则独立，网络请求顺序不变。
- 新增 `CoreSourcePersistencePolicyTest`、`CoreInstalledStateControllerTest`、`CoreOperationControllerTest`、`CoreRevisionVersionCacheTest`。
- `CoreRepositoryImpl.kt` 从 2,358 行降至 2,270 行；8 个 WS4 目标测试类共 40 项通过。
- 全量 Kotlin 单测排除需本机 frpc 的 `FrpcConfigIntegrationTest`（本地执行会卡在真实进程 stop 脚本读取）后，227 suite、1,131 项、0 失败。
- 提取 `CoreRemoteBranchSourceController.kt`：默认分支缓存、分支列表、分支头快照、globals 版本、分支来源组装与后缀分支解析独立；Repository 继续持有缓存并注入读写回调，请求顺序不变。
- 新增 `CoreRemoteBranchSourceControllerTest`，覆盖默认分支缓存、快照、源缓存命中、后缀分支与无 head 的归档回退。
- `CoreRepositoryImpl.kt` 从 2,270 行降至 2,145 行；11 个 WS4 相关测试类共 69 项通过。
- 全量 Kotlin 单测再次排除环境 frpc 用例后，228 suite、1,139 项、0 失败。
- 提取 `CoreUpdateCheckController.kt`：更新检查（远端版本/提交关系判定、PR 头检查、失败与配额策略）、比较缓存（24 项清空阈值）和最新版本标签缓存从 Repository 迁出；`checkAndMarkUpdate`、`checkAllUpdates`、`fetchUpdateComparison` 只保留门面调用。
- 保持原有语义：配额耗尽时自动上下文静默返回、单个 PR 查询失败不隐藏主分支更新入口、单个 PR 失败计入告警、最新版本标签在仓库或分支变化后失效、已安装状态控制器清理标签仍走同一缓存。
- 新增 `CoreUpdateCheckControllerTest`，覆盖版本领先/落后、比较缺失报错、比较缓存命中、PR 头变化、来源不一致、配额阻断、比较发布与标签失效，共 10 项。
- 提取 `CorePullRequestController.kt`：PR 列表/详情/文件查询、PR 组合准备、暂存候选发布与进度上报从 Repository 迁出；Repository 仅转发调用（`CoreRepositoryImpl.kt` 从 2,145 行降至 1,795 行）。
- 保持原有语义：PR 组合继承已安装 PR 栈、暂存目录标记与清理、缺失依赖自动安装与校验、候选发布不触发全局依赖修复门槛、取消后清理暂存并结束操作、进度状态在 finally 清空。
- 单测保留对更新检查的行为覆盖；PR 查询与合并服务依赖网络，继续由守卫脚本、编译与全量单测兜底。
- 全量 Kotlin 单测（排除环境 frpc 用例）229 suite、1,149 项、0 失败；Node 216 项通过。
- 提取 `CoreInstallPipelineController.kt`：更新计划指纹校验、PR 组合重放、压缩包下载（候选 URL、鉴权、进度节流、取消联动）、解包校验、暂存核心整理与发布、缺依赖候选交接从 Repository 迁出；`installCore`、`updateCore`、`switchCoreBranch`、`rollbackCore` 只保留门面调用。
- 保持原有语义：`USER_AGENT`、`newOutboundCall`、`applyGithubAuth`、401/403/404 文案、300ms 进度节流、`totalBytes` 钳制、缺依赖时保留候选目录并抛 `CoreDependencyRepairRequiredException`、失败时清理暂存、`finally` 清空下载进度。
- 提取 `CoreRevisionController.kt`：两次 `rollbackCore` 重载、`fetchReleaseHistory`、`fetchRevisionHistory`、`fetchRevisionVersion`、`fetchRevisionDetails`、Revision globals 版本解析（60 秒失败 TTL、首选路径缓存）与回退来源元数据构造从 Repository 迁出。
- 保持原有语义：回退后 `refreshCoreInfo` → 等待刷新 → 在自动配额上下文里重新检查更新；历史失败返回空列表；版本缓存命中/记录顺序不变。
- 清理迁移后失效的私有死代码：`buildLatestVersionLabel`、`fetchVersionFromGlobals`、`writeCoreSourceMetadata`。
- `CoreRepositoryImpl.kt` 从 1,795 行降至 1,387 行（Slice 2 结束时为 1,795 行）。
- 全量 Kotlin 单测（排除环境 frpc 用例）229 suite、1,149 项、0 失败；Node 216 项通过；护栏 6 项通过。
- 提取 `CoreInfoRefreshController.kt`：`refreshCoreInfo` / `refreshCoreInfoAndAwait`、`loadCoreState`、`mergeVersionUpdateState`、`ensureCoreDirWatcher`、`scheduleCoreInfoRefresh` 及其票据、去抖任务、监听器与首次加载状态从 Repository 迁出。
- 状态归属调整为控制器持有 `isCoreInfoLoading` 与刷新票据，Repository 通过 getter 暴露；`_coreInfoList`、`mutationMutex`、`repoScope` 与展示缓存仍由 Repository 提供回调注入。
- 保持原有语义：先失效票据再取消旧任务、首次加载期间置 loading、`mutationMutex` 包裹扫描与发布、票据不一致时不写状态、仅在最后持有票据的任务里结束 loading、800ms 去抖、非 Normal 模式停止监听、`isCoreReady` 同步扫描路径不变。
- 清理迁移后失效的 `resolveCoreSourceStatus` 私有包装。
- `CoreRepositoryImpl.kt` 从 1,387 行降至 1,207 行。
- 全量 Kotlin 单测（排除环境 frpc 用例）229 suite、1,149 项、0 失败；Node 216 项通过；架构边界与护栏检查通过。
- 将更新计划生成（`prepareCoreUpdate`）移入 `CoreUpdateCheckController.prepareUpdatePlan`：远程基线校验、逐个 PR 覆盖度确认、缺失/未知 PR 分类、确认头与说明收集、指纹与下载地址组装。
- 保持原有语义：PR 组合基线不一致时直接失败；覆盖度查询失败只记未知并写提示；配额耗尽异常继续上抛；目标提交存在时使用分支 zip 地址。
- 新增 `CoreUpdateCheckControllerTest` 三个用例：未包含 PR 计入待重放、覆盖度查询失败计入未知、来源不一致直接失败。
- WS4 收尾：`CoreRepositoryImpl.kt` 从 839 行降至 798 行，剩余内容为字段装配、来源解析、目录位置、本地状态读取、`isCoreInstalled`/`isCoreReady`、`deleteCore`/`applyWorkDirectoryChange` 编排与纯转发。
- 全量 Kotlin 单测（排除环境 frpc 用例）229 suite、1,152 项、0 失败；Node 216 项通过；架构边界与护栏检查通过。
- 提取 `CorePendingMutationController.kt`：核心操作门（begin/mark/claim/finish/runOwned）、pending 候选状态与锁、在线修复、离线依赖包导入、应用与丢弃候选、已安装核心依赖修复入口、启动依赖提示恢复从 Repository 迁出。
- 锁语义保持：操作门与 pending 状态共用同一把锁，`claimPending` 在候选目录失效时清空候选并重置操作状态、`setPendingMutation` 仅在操作 ID 匹配且无候选时接受、`runOwned` 在取消/失败时清空候选并结束操作、缺依赖异常只标记等待修复。
- 其余语义保持：导入包大小上限与临时文件清理、`package.json` 拷贝前置校验、可复用修复请求按缺失包与恢复动作合并、`_pendingDependencyRepair` 发布开关（PR 组合不触发全局门槛）、修复请求恢复时使用 `RuntimeStart` + 默认恢复动作。
- `CoreRepositoryImpl.kt` 从 1,207 行降至 904 行。
- 全量 Kotlin 单测（排除环境 frpc 用例）229 suite、1,149 项、0 失败；Node 216 项通过；架构边界与护栏检查通过。
- 提取 `CoreCandidateController.kt`：候选记录持久化与路径校验、`_candidateState` 展示流、确认候选、从恢复点回滚（删除失败核心、移动备份、Root 同步、刷新）从 Repository 迁出。
- 保持原有语义：候选校验要求变体/运行模式/安装时间戳完全匹配、备份目录为空或缺失时丢弃候选返回 false、回滚后仅在 Normal 模式之外同步 Root、`mutationMutex` 内完成确认与恢复、恢复完成后触发核心信息刷新。
- `CoreRepositoryImpl.kt` 从 904 行降至 839 行；当前文件内容以字段装配、来源解析、目录位置、`isCoreInstalled`/`isCoreReady`、`prepareCoreUpdate`、`deleteCore` 与纯转发为主。
- 全量 Kotlin 单测（排除环境 frpc 用例）229 suite、1,149 项、0 失败；Node 216 项通过；架构边界与护栏检查通过。

### 2026-10-21 WS5 Runtime Repository（`RuntimeRepositoryImpl.kt`）

- 提取 `RuntimeStateStore.kt`：`runtime` 偏好键、初始状态装载（端口/Token/变体/监听模式）、
  运行时长锚点选择与健康接口补锚、本地/局域网/IPv6 访问地址拼装、配置与接管写回。
- 提取 `RuntimeProcessProbe.kt`：端口探活与等待、带重试的端口探测、Node 进程与前台服务判定、
  健康接口读取与归属校验、退出/回收等待。
- 提取 `RuntimeStatusController.kt`：`mark*` 状态迁移、状态文案与 Root 通知同步、运行时长计数、
  运行态转换（transition）序列、候选核心观察/确认/失败回调与已运行 Root 进程接管。
- 提取 `RuntimeLifecycleController.kt`：启动/停止/重启、服务配置应用与重启、陈旧普通进程回收、
  启动前依赖预检、运行模式切换。
- 提取 `RuntimeStateReconciler.kt`：初始接管（含数据丢失后的 Root 被动恢复）、周期状态纠正、
  前后台切换、离开应用时的前台通知补发。
- 提取 `RuntimeLogController.kt`：核心日志抓取与清空、App 日志聚合、多格式日志块解析与噪声过滤。
- 提取 `RuntimeHotReloadController.kt`：普通模式递归文件监听、Root 目录指纹比对、去抖与抑制窗口。
- 提取 `RuntimeNetworkMonitorController.kt`：系统网络事件订阅与访问地址刷新。
- 提取 `NormalRuntimeStartState.kt`：普通模式启动/重启标记（`pendingRestart`/`startIssuedAtMs`/`pendingExplicitStart`）
  由状态广播、生命周期与状态协调共享。
- 保持原有语义：状态广播仍只处理普通模式、陈旧广播过滤不变、启动/停止/重启的等待与超时阈值不变、
  配置应用仍先停后启、候选核心启动失败仍按"停止 → 还原 → 重启"恢复、热重载仍走同一把操作锁。

### 2026-10-21 WS5 Root 控制与 RuntimePaths（并行拆分）

- 三条拆分并行执行（`RuntimeRepositoryImpl`、`RootRuntimeController`、`RuntimePaths`），
  每个文件单一归属，避免同文件并发编辑；收口时统一跑一次全量验证。
- `RootRuntimeController.kt` 从 1,428 行降至 228 行门面，拆出 `RootRuntimeScripts`（420 行，
  原子同步/增量同步/权限归一/依赖探测/node_modules 校验脚本）、`RootRuntimeSync`（491 行，
  工作目录、环境变量、核心与依赖同步）、`RootRuntimeProcessControl`（288 行，启动/停止/重启与控制标记）、
  `RootRuntimeHealth`（229 行，健康端点、归属判定、进程发现、端口等待）、`RootRuntimeLocation`（37 行，
  进程名与 pid/started-at 路径单一来源）。对外 API 与既有测试零改动。
- `RuntimePaths.kt` 从 770 行降至 270 行，拆出 `RuntimePathResolver`（路径规范化、tree URI、
  写入探针、目录重叠）、`RuntimeWorkDirPreferences`（工作目录偏好）、`RuntimeWorkDirMigration`（迁移事务与回滚）、
  `RuntimeVariantKeyResolver`（核心变体解析）；新增 `RuntimePathResolverTest`、`RuntimeVariantKeyResolverTest`。
- 复核证据：Root 侧三引号脚本块在归一化 `RootRuntimeLocation` 常量引用后逐块一致；Runtime 侧
  11 个脚本块零差异；`RuntimePaths` 与 `RootRuntimeController` 的公开/内部函数集合不变
  （仅三个原 `private` 变体解析函数随迁移改名）。
- 统一收口：231 suite、1,158 项、0 失败；Node 216 项通过；架构边界与护栏自测通过；
  `git diff --check` 无空白错误。
- 清理迁移后失效的死代码：未使用的 `NORMAL_START_TIMEOUT_PRIMARY_MS`/`EXTEND_MS`/`STALE_TIMEOUT_MS`
  常量与 `ConcurrentHashMap` 导入。
- `RuntimeRepositoryImpl.kt` 从 3,047 行降至 503 行，剩余内容为字段装配、串行用户操作入口、
  NodeService 状态广播桥接、候选核心恢复编排与公开 API 转发。
- 验证：`./gradlew :app:compileDebugKotlin`、`:app:checkRefactorGuardrails`、
  `:app:checkNodeRuntimeScripts`、`:app:testAppOutboundBridge`、`:app:testDebugUnitTest`
  （231 suite、1,158 项、0 失败）、`node --test node-tests/*.test.cjs`（216 项）、
  `python3 scripts/refactoring/check_architecture.py`、`git diff --check` 全部通过。

- WS6 起：`DanmuDownloadViewModel.kt` 从 1,982 行降至 303 行，只保留装配、初始装载与公开 API 转发；
  页面状态改由 5 个 Compose 状态容器持有，业务规则分到 6 个控制器与 2 个支撑类。
- 新增 `DownloadSearchController`（搜索、链接解析、剧集加载与来源/格式设置）、
  `DownloadEpisodeSelectionController`（来源筛选、批量选中与“下载选中集”）、
  `DownloadQueueController`（队列统计、分组、排序、清空、重试、暂停与取消）、
  `DownloadQueueRunner`（串行执行、自动重试、链路重建、退避流控）、
  `DownloadRecordsController`（记录预览、删除、目录同步与整季补集）、`DownloadUiState`（状态容器）。
- 支撑类：`DownloadRateLimitBypass`（下载期 `RATE_LIMIT_MAX_REQUESTS` 旁路与恢复）、
  `DownloadTaskInputRebuilder`（旧链路失效后的重新搜索与剧集匹配）、
  `support/DownloadNetworkSupport.kt`（弹幕源基础地址解析、带出站统计的 GET）。
- 行为逐字保留：`processPendingQueue`（561 行）与其余搬移方法经归一化逐行比对与原文一致，
  仅替换状态归属（`x = y` → `state.x = y`）与回调注入（`processPendingQueue()` → `startQueueProcessing()`）；
  对外属性与函数签名全部保留，`DanmuDownloadScreen`、Home 页与记录页调用点零改动。
- 新增 `DownloadNetworkSupportTest`（5 项）：补协议、去尾斜杠、token 追加与去重、空值与空 token。
- 验证：`./gradlew :app:compileDebugKotlin`、`:app:checkRefactorGuardrails`、`:app:checkNodeRuntimeScripts`、
  `:app:testDebugUnitTest`（234 suite、1,168 项、0 失败，含新增 5 项）、
  `python3 scripts/refactoring/check_architecture.py`、`test_guardrails.py`、`git diff --check` 全部通过。
- `HomeViewModel.kt` 从 1,578 行降至 510 行，拆出 `HomeCoreInstallController`（456）、
  `HomeCacheRefreshController`（229）、`HomeAppUpdateController`（229）、`HomeCoreUpdateCheckController`（209）、
  `HomeCoreUpdatePromptController`（198）、`HomeRuntimeQuickActionsController`（148）、`HomeCacheClearController`（109）、
  `HomePendingProxyAction`（16）与两个纯函数支撑（`HomeCacheFileEventPolicy`、`HomeDisplayFormatters`）；
  新增对应 JVM 单测 5 项。原有 106 个函数、174 个属性名保留，89 条字面量双向零差异。
- `SettingsViewModel.kt` 从 1,608 行降至 602 行，按域拆出 9 个控制器（服务控制、工作目录、备份/恢复、
  外观、应用更新、GitHub 账号、Root 自启、控制中心磁贴、电视同步），对外 API 名单零差异、
  213 条字面量零差异、13 个界面调用点零改动。
- 统一收口：234 suite、1,168 项、0 失败；Node 216 项通过；架构边界与护栏自测通过；
  三个 ViewModel 的字面量多重集独立比对通过（差异仅为状态归属前缀与私有符号改名）。

- `HomeScreen.kt` 从 1,937 行降至 714 行，`HomeScreenSections.kt` 拆成 7 个分区文件（108–352 行），
  22 个内联弹窗按域拆成核心/运行时/应用更新/公告/队列缓存宿主文件，公告中心独立到 `announcement` 包；
  188 条字符串字面量零差异，调用顺序与层叠顺序保持不变。
- `ConfigDialogEditors.kt`（1,707 行）拆为 7 个专职编辑器文件（132–373 行），`ConfigVisualEditors.kt`（1,697 行）
  拆为 6 个文件（156–379 行）；两者字面量多重集分别 150=150、375=375 零差异，仅共享辅助函数放宽为 `internal`。
- 按用户新增的拆分粒度约束做了一次收敛：把两个 16 行、单次调用的辅助文件合并回宿主文件
  （`HomeCacheFileEventPolicy` 并入 `HomeCacheRefreshController`、`HomeDisplayFormatters` 并入 `HomeViewModel`），
  测试改为直接引用宿主文件内的同文件函数，保持可测性。
- 第二轮收口：234 suite、1,168 项、0 失败；Node 216 项通过；护栏、架构边界与 `git diff --check` 全部通过。

- `StartupPermissionGate.kt` 从 1,626 行降至 593 行，拆出 `StartupPermissionState`（122，权限状态与动作判定）、
  `StartupGateCards`（384，六个共用卡片）、`StartupGateStepSections`（561，四步内容）；正文逐字节一致、
  字面量 125=125，仅 17 处跨文件声明 `private` → `internal`。
- `PullRequestLabScreen.kt` 从 1,549 行降至 267 行，拆出队列、详情、卡片、构建、状态、头部与共享文案等 8 个文件
  （62–340 行）；`PullRequestLabViewModel.kt` 从 740 行降至 487 行，抽出 `PullRequestLabBuildController`（339，
  组合构建、原子应用与启动恢复流水线）。页面字面量 147=147、ViewModel 38=38，对外 API 与调用点零改动。
- 按粒度约束执行：`SectionTitle`、`SelectedPullRequestRow`、`StatusBadge`、`coreBadgeText`、`normalizeStartupVariant`
  等单调用点小件保留在宿主文件内，未新建文件、未引入抽象层。
- 第三轮收口：234 suite、1,168 项、0 失败；Node 216 项通过；护栏、架构边界与 `git diff --check` 通过。

### 2026-10-21 WS7 Gradle build-logic 拆分

- 新增 `build-logic` 独立构建（`kotlin-dsl`，不依赖 AGP 或第三方库，因此 build-logic 类路径保持纯净），
  根 `settings.gradle.kts` 用 `includeBuild("build-logic")` 接入。
- `app/build.gradle.kts` 从 1,789 行降到 240 行，只保留插件接入、`android {}`、`kotlin {}` 与 `dependencies {}`；
  其余按域剪切到 3 个预编译脚本插件：
  `danmu.runtime-assets`（671 行：Node 依赖闭包读取/裁剪/刷新/校验、缺依赖测试开关、Node smoke 与 APK 运行时校验）、
  `danmu.native-runtime`（747 行：原生运行时来源解析、下载与 SHA-256 校验、JNI 桥现编、ELF 16KB 页对齐门禁、frp 内核校验）、
  `danmu.quality-gates`（96 行：重构护栏任务、发布防呆告警、`releaseCheck` 入口与 `preBuild` 门禁）。
- 跨域共享的配置解析（版本、ABI、混淆开关、签名、`local.properties`、运行时目录与开关）集中到
  `build-logic/src/main/kotlin/danmu/DanmuBuildProperties.kt`（184 行）；模块脚本与三个插件都只调用这里，
  避免同一份清单在模块脚本和插件里各维护一份。
- 迁移方式：按行区间原样剪切，只在装配点改写（`testMissingRuntimePackage`/`missingRuntimeTestAssetsDir` 收敛为单一来源、
  签名改用 `DanmuSigning` 结果、`preBuild`/`preReleaseBuild` 门禁按域拆给各插件）。
  逐行多重集比对：三个插件合计 1,319 行迁移内容与原文件完全一致（缺失 0、多余 0）。
- 连带修正两处跨文件硬编码（迁移必然触及，不改会削弱护栏）：
  `NodeProjectManagerRuntimeDependencyTest` 改为从 `danmu.runtime-assets` 插件读取 `androidRuntimeExcludedNodeModules`；
  `scripts/refactoring/audit_runtime_hardcoding.py` 的扫描根加入 `build-logic/src/main/kotlin`。
- 验证：任务图 `--dry-run` 与迁移前逐行一致（`preBuild`/`assembleDebug`/`releaseCheck`/`checkRefactorGuardrails` 共 132 项）；
  `:app:compileDebugKotlin --rerun`、`:app:checkRefactorGuardrails`、`:app:testDebugUnitTest --rerun`（234 suite、1,168 项、0 失败）、
  `:app:testAppOutboundBridge`、`node --test node-tests/*.test.cjs`（216 项）、`git diff --check` 全部通过。
- 环境阻塞（与本工作流无关，需用户处理）：用户 Termux 在 2026-10-21 19:30–19:34 升级了
  `libc++ 29→30` 与 `cmake 4.4.3→4.4.4`，升级后宿主 `cmake` 无法启动
  （`CANNOT LINK EXECUTABLE "cmake": cannot locate symbol _ZNSt6__ndk113__hash_memoryEPKvm`）。
  因此本轮 `:app:prepareNativeRuntime` 与 `:app:verifyNativeRuntimeInputs` 以 `-x` 排除：这两个任务的正文逐行未改动，
  且不经 Gradle 直接执行 `scripts/build-native-deps.py` 同样失败，可确认是宿主工具链问题。
  宿主 cmake 修复后需补跑完整 `:app:assembleDebug` / `:app:releaseCheck`。

### 2026-10-21 WS8 Gradle 模块骨架

- 新增三个 Android library 模块骨架，`settings.gradle.kts` 接入 `:core:model`、`:core:common`、`:ui:designsystem`，
  `app` 显式依赖三者（本轮模块内尚无代码，依赖接通只为固定方向并让后续迁移有编译反馈）：
  `core/model/build.gradle.kts`（`namespace com.example.danmuapiapp.core.model`，Kotlin serialization）、
  `core/common/build.gradle.kts`（`...core.common`，只依赖 `:core:model`）、
  `ui/designsystem/build.gradle.kts`（`...ui.designsystem`，Compose BOM + material3/foundation/animation + kyant shapes/backdrop）。
- 三个模块统一 `compileSdk 37` / `minSdk 24` / Java 17，与 `app` 对齐；沿用 AGP 9 内置 Kotlin（不额外应用 `kotlin-android`），
  与 `app/build.gradle.kts` 现有插件组合保持一致。
- 模块依赖方向写入 `scripts/refactoring/check_architecture.py` 的 `MODULE_RULES`：
  `:core:model` 不允许任何 `project(...)` 依赖、`:core:common` 只允许 `:core:model`、`:ui:designsystem` 只允许 `:core:*`；
  同时校验源码导入（core 模块禁止 `android.*`/`androidx.*`，三个模块禁止跨模块导入 `app` 侧包）。
  护栏自测新增 3 项用例（平台导入、越权模块依赖、合法依赖），共 9 项。
- `audit_runtime_hardcoding.py` 扫描根加入 `core`、`ui`，并跳过路径中的 `build` 目录，
  避免代码迁入模块后资产清单审计出现盲区（与 WS7 把 `build-logic` 纳入扫描同一处理方式）。
- `.gitignore` 补 `/core/*/build`、`/ui/*/build`，避免新模块构建产物被误提交（WS7 审计已登记的同类风险）。
- 本轮不迁移任何 Kotlin 代码：迁移按模块单独提交，先动 `:core:model`（`domain/model` 为纯 Kotlin，最易先落地）。
- 验证：三个模块 `assembleDebug` 与 `:app:compileDebugKotlin` 通过（BUILD SUCCESSFUL，7m32s）；
  `:app:checkRefactorGuardrails` 通过（含 9 项护栏自测）；`git diff --check` 无空白错误。

## 决策记录

### 2026-10-21 落盘方式

### 2026-10-21 构建环境注意（cmake 链接失败与 Gradle 输出快照）

- 现象一：从 Codex 的 Termux 包装器里执行构建时，`cmake` 报
  `cannot locate symbol "_ZNSt6__ndk113__hash_memoryEPKvm"`。根因不是系统升级未完成，而是包装器把
  `LD_LIBRARY_PATH` 指向了 `node_modules/@mmmbuto/codex-cli-termux/bin`，该目录里有一份旧版
  `libc++_shared.so`，被 `cmake` 优先加载。验证与构建命令统一用 `env -u LD_LIBRARY_PATH ./gradlew ...` 即可；
  直接执行 `cmake --version` 也会复现，清空该变量后返回 4.4.4。
- 现象二：原生任务失败后再次执行，Gradle 报
  `Cannot access output property '$1' ... Could not read directory path '.../prepared-native-runtime/libnode/bin/x86_64'`。
  这是失败运行留下的半成品输出目录与任务快照不一致导致的；把 `app/build/prepared-native-runtime` 移开后
  重新执行即可完整重建（三个 ABI）。
- 结论：这两条都不是重构引入的问题；后续所有 Gradle 验证建议带上 `env -u LD_LIBRARY_PATH`，
  并且原生任务失败后不要直接重跑，先清理输出目录。

### 2026-10-21 拆分粒度约束（WS6 起生效）

用户明确要求禁止过度封装，后续拆分与收口按此执行：

1. 单次调用、只服务于原文件的逻辑不单独建文件；简单逻辑不套抽象类、接口或 DTO。
2. 迁移代码一律「剪切」，不复制新文件再删旧代码，避免 diff 虚高。
3. 不生成重复样板（重复 try/catch、重复日志、重复状态转发）。
4. 只有具备复用价值、职责明确的逻辑才独立成文件；预计拆分后不足约 100 行且只有一个调用点的，
   留在原文件内（可用同文件内的顶层 `internal` 函数保留可测性）。
5. 收口阶段按此规则复查新增文件，把不达标的合并回原文件，并在进度日志记录合并结果。

- WS0-WS4 的改动按职责拆成 5 个语义提交后推送 `origin/test`：
  `555085b` 护栏与追踪文档、`4e1952d` domain 抽取与工具统一、
  `2219eb2` CoreRepository 拆分、`17e5b99` Node 模块化与资产清单驱动、
  `7ebaeb5` 安装续跑结果上报与弹窗内 toast。
- 提交前在最终工作树上执行了完整验证（护栏、Kotlin 编译、全量单测、Node 测试、
  架构边界检查、`git diff --check`），提交后复核护栏任务仍通过。
- `app/build.gradle.kts` 跨两个关注点：护栏任务部分随 `555085b`，
  Node 测试清单部分随 `17e5b99`。

| 决策 | 原因 |
|---|---|
| 先拆职责和边界，再做 Gradle 模块 | 直接改全项目包名会产生不可审查的巨型 diff |
| 先落模块骨架再迁代码 | 模块接线（AGP 9 内置 Kotlin、依赖方向、护栏）本身有失败风险，先独立验证；迁移提交随后只含文件移动 |
| 先改资产清单，再移动 Node 文件 | Kotlin Root 同步和测试存在扁平文件名硬编码，直接移动会导致运行时缺文件 |
| Repository 保留门面 | UI 和跨功能调用接口保持稳定，降低重构回归面 |
| 完整 APK 构建在用户确认后执行 | 用户明确要求 checkpoint 阶段不构建；重构中优先使用静态检查、单测和针对性 smoke |
