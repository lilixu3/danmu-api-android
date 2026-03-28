# App Diagnostic Logging Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 为 DanmuApiApp 增加独立的 App 诊断日志与 Root 启动引导日志，并在现有控制台中统一聚合展示，解决核心未启动时无法反馈 App 侧故障的问题。

**Architecture:** 保持核心日志仍然只走 `/api/logs`，新增位于 `noBackupFilesDir/app-logs/` 的 App 诊断日志滚动文件，并通过 `RuntimeRepositoryImpl` 把核心日志、App 文件日志和内存事件日志统一合并到控制台。Root 模式额外捕获启动脚本阶段输出，写入独立的 `root-bootstrap.log`。

**Tech Stack:** Kotlin, Hilt, Android Service/BroadcastReceiver/Application, Jetpack Compose, Java IO/File APIs

---

### Task 1: 建立 App 诊断日志基础设施

**Files:**
- Create: `app/src/main/java/com/example/danmuapiapp/data/service/AppLogSource.kt`
- Create: `app/src/main/java/com/example/danmuapiapp/data/service/AppDiagnosticLogger.kt`
- Modify: `app/src/main/java/com/example/danmuapiapp/domain/model/Models.kt`

**Step 1: 定义日志来源模型**

- 为 `LogEntry` 增加 `source` 与 `tag`
- 新增 `AppLogSource` 枚举，至少包含 `Core`、`App`、`RootBootstrap`

**Step 2: 实现滚动日志器**

- 把 App 诊断日志写入 `noBackupFilesDir/app-logs/app.log`
- 限制为 `app.log`、`app.log.1`、`app.log.2`
- 单文件 `512KB`

**Step 3: 实现 Root 引导日志读写**

- 额外支持 `root-bootstrap.log`
- 限制为 `128KB` 主文件 + `1` 个备份

**Step 4: 提供读取与清理接口**

- 读取最近日志
- 清空 App 诊断文件
- 清空 Root 引导文件

### Task 2: 接入关键启动链路日志

**Files:**
- Modify: `app/src/main/java/com/example/danmuapiapp/DanmuApiApplication.kt`
- Modify: `app/src/main/java/com/example/danmuapiapp/data/service/RuntimeWarmupCoordinator.kt`
- Modify: `app/src/main/java/com/example/danmuapiapp/data/service/BootReceiver.kt`
- Modify: `app/src/main/java/com/example/danmuapiapp/data/service/MyPackageReplacedReceiver.kt`
- Modify: `app/src/main/java/com/example/danmuapiapp/data/service/KeepAliveAccessibilityService.kt`
- Modify: `app/src/main/java/com/example/danmuapiapp/data/service/NodeService.kt`

**Step 1: 替换关键 `Log.*` 调用**

- 让关键诊断日志同时进 `Logcat` 和 `AppDiagnosticLogger`
- 优先处理启动、恢复、超时、崩溃、跳过与失败路径

**Step 2: 保持行为兼容**

- 不改原有广播、通知和状态机逻辑
- 只补诊断日志，不重构业务流程

**Step 3: 控制噪声**

- 保留有价值的 `info/warn/error`
- 不把高频普通调试日志批量落盘

### Task 3: 接入 Root 启动引导日志

**Files:**
- Modify: `app/src/main/java/com/example/danmuapiapp/data/service/RootRuntimeController.kt`

**Step 1: 为 Root 启动脚本增加引导日志文件**

- Root 启动前先准备 `root-bootstrap.log`
- 启动脚本 stdout/stderr 重定向到该文件，而不是 `/dev/null`

**Step 2: 在失败路径补充提示**

- 启动失败或端口超时时，把最近的 bootstrap 摘要纳入错误详情
- 控制台可继续查看完整引导日志

**Step 3: 保持 Root 模式原有行为**

- 不改变 Root 启动、停止、重启时序
- 只增加日志捕获

### Task 4: 扩展运行日志聚合与清理

**Files:**
- Modify: `app/src/main/java/com/example/danmuapiapp/data/repository/RuntimeRepositoryImpl.kt`
- Modify: `app/src/main/java/com/example/danmuapiapp/domain/repository/Repositories.kt`

**Step 1: 聚合多来源日志**

- 读取 `/api/logs`
- 读取 `app.log`
- 读取 `root-bootstrap.log`
- 与 `_eventLogs` 合并并按时间排序

**Step 2: 统一来源标记**

- 核心日志标记为 `Core`
- App 诊断日志和内存事件日志标记为 `App`
- Root 引导日志标记为 `RootBootstrap`

**Step 3: 改造清除逻辑**

- 清核心日志
- 清 `_eventLogs`
- 清 `app.log*`
- 清 `root-bootstrap.log*`

### Task 5: 调整控制台显示

**Files:**
- Modify: `app/src/main/java/com/example/danmuapiapp/ui/screen/console/ConsoleViewModel.kt`
- Modify: `app/src/main/java/com/example/danmuapiapp/ui/screen/console/ConsoleScreen.kt`

**Step 1: 增加来源筛选**

- 支持全部 / 核心 / App / Root 启动

**Step 2: 完整展示元数据**

- 列表中显示来源与标签
- 复制日志时输出来源、标签、级别和消息

**Step 3: 保持现有页面结构**

- 不新建页面
- 不破坏现有搜索、级别筛选和预览入口

### Task 6: 验证与回归检查

**Files:**
- Modify: `app/src/main/java/com/example/danmuapiapp/data/service/AppDiagnosticLogger.kt`
- Modify: `app/src/main/java/com/example/danmuapiapp/data/repository/RuntimeRepositoryImpl.kt`
- Modify: `app/src/main/java/com/example/danmuapiapp/ui/screen/console/ConsoleScreen.kt`
- Modify: `app/src/main/java/com/example/danmuapiapp/data/service/RootRuntimeController.kt`

**Step 1: 运行 Kotlin 编译**

Run: `./gradlew :app:compileDebugKotlin`
Expected: PASS

**Step 2: 检查关键行为**

- 普通模式控制台可见 App 日志
- 清除日志后文件被删除或清空
- Root 失败时能读取 bootstrap 日志

**Step 3: 仅在必要时补充细节修正**

- 若 UI 过挤，压缩来源展示样式
- 若日志过多，进一步收紧写入点
