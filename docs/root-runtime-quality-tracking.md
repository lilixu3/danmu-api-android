# Root/运行时质量优化追踪文档

> **For Hermes:** 按本文档逐项落地；每完成一项，必须把对应状态从 `TODO` 改为 `DONE`，并补充实际验证命令/结果。全部完成后单独开审查线程做最终审核，再编译 `arm64-v8a` release APK。

**目标：** 修复本轮只读审计发现的 Root 运行态、核心状态、敏感配置、日志配置和 WakeLock 风险，提升 release 前稳定性。

**范围：** `lilixu3/danmu-api-android` Android App 本地仓库，不做正式 GitHub Release/tag；最终交付直接发送 `arm64-v8a` release APK。

**总体验收：**

- [ ] 所有条目状态为 `DONE`
- [ ] `git diff --check` 通过
- [ ] 相关单元测试通过
- [ ] `:app:testDebugUnitTest` 通过
- [ ] `:app:lintDebug` 通过或只剩既有可接受 warning
- [ ] `:app:assembleRelease -PabiFilters=arm64-v8a --console=plain` 成功
- [ ] arm64 release APK 包含 `libnode.so`、`libnative-lib.so`、`libc++_shared.so` 与关键 Node runtime assets
- [ ] `apksigner verify --print-certs` 通过

---

## 1. Root 模式核心状态读取不能误用普通目录

**状态：** TODO

### 问题

当前未提交 diff 把 `CoreRepositoryImpl.hasValidCore/readLocalCoreVersion` 的 Root 分支从 Root 目录读取改成了普通目录读取，导致 Root 模式 UI/核心状态可能和真实 Root 运行目录不一致。

### 计划

1. 为“按运行模式选择核心目录状态源”补单元测试，覆盖 Normal/Root 两种模式。
2. 抽出可测试的小策略函数，避免直接测 Android Context/RootShell。
3. `CoreRepositoryImpl` 恢复 Root 分支使用 `rootHasValidCore(location.rootDirPath)` 与 `rootReadCoreVersion(location.rootDirPath)`。
4. 保持主线程避免 su 的逻辑不变：主线程 Root 模式仍只触发后台刷新，不直接阻塞。

### 建议改法

- 新增/修改：`app/src/main/java/com/example/danmuapiapp/data/repository/CoreLocationPolicy.kt`
  - `CorePresenceSource.NormalDir`
  - `CorePresenceSource.RootDir`
  - `corePresenceSourceFor(mode: RunMode)`
- 新增测试：`app/src/test/java/com/example/danmuapiapp/data/repository/CoreLocationPolicyTest.kt`
- 修改：`CoreRepositoryImpl.kt:285-300`
  - Root 分支调用 Root 专用函数。

### 验收

- [ ] `CoreLocationPolicyTest` 证明 Root 模式选择 RootDir。
- [ ] `git diff` 中 Root 分支不再调用 `NodeProjectManager.hasValidCore(location.normalDir)`。
- [ ] 相关测试通过。

### 实际验证

- 待填写。

---

## 2. Root 被动运行判断避免 stale pid 文件长期假阳性

**状态：** TODO

### 问题

`RootRuntimeController.isProbablyRunning()` 当前只要端口开或 pid 文件存在就返回 true。pid 文件如果残留，会让 UI 长期误判 Root 仍在运行。

### 计划

1. 补策略函数测试：端口开一定 true；pid 文件存在但没有 started-at 文件/文件过旧时不应长期 true。
2. `isProbablyRunning` 保持无 su，不触发 Root 授权提示。
3. 引入 started-at 文件作为 pid hint 的最低可信锚点。
4. 给 stale hint 设置宽松 TTL，避免短暂端口不可达立即误判停止。

### 建议改法

- 修改：`RootRuntimeUptimePolicy.kt`
  - 新增 `isRootPassiveLivenessLikely(portOpen, pidPresent, startedAtMs, nowMs)`。
  - 建议 TTL：7 天，避免非常旧的 pid 残留；无 started-at 文件时视为不可信。
- 修改：`RootRuntimeController.isProbablyRunning(context, port)`
  - 先 `isRunningFast(port)`；否则读取 pid 和 started-at，交给策略函数。

### 验收

- [ ] 单测覆盖 stale pid 不再返回 true。
- [ ] `isProbablyRunning` 不调用 `RootShell`。
- [ ] Root 被动路径仍不触发 su。

### 实际验证

- 待填写。

---

## 3. Root stop/restart 清理 started-at 文件

**状态：** TODO

### 问题

新增了 `root_node_started_at_ms`，但 `RootRuntimeController.stop()` 成功停止时只删除 pid 文件，可能留下陈旧 uptime anchor。

### 计划

1. 抽出 `clearRootRuntimeMarkers(context)`，统一删除 pid 文件和 started-at 文件。
2. 替换 `stop()` / restart kill fallback 中只删 pid 的路径。
3. 补脚本/源码静态测试，确保 stop marker 清理函数同时包含两个文件。

### 建议改法

- 修改：`RootRuntimeController.kt`
  - 新增 `internal fun buildClearRuntimeMarkersShell(pidPath, startedAtPath): String` 或直接新增 `private fun clearRuntimeMarkers(context)`。
  - 所有停止成功路径调用同一个清理函数。
- 新增测试：`RootRuntimeControllerMarkerTest.kt` 或扩展现有 Root 相关测试。

### 验收

- [ ] 所有 `pidFile(context).delete()` 停止成功路径改为统一 marker 清理。
- [ ] 单测/静态测试确认包含 `root_node_started_at_ms` 清理。
- [ ] restart 旧进程 kill 成功后不会保留旧 started-at。

### 实际验证

- 待填写。

---

## 4. 运行时日志配置不要无条件覆盖用户 raw `.env`

**状态：** TODO

### 问题

普通模式和 Root 模式启动前会强制写：

- `DANMU_API_LOG_TO_FILE=0`
- `DANMU_API_LOG_MAX_BYTES=1048576`
- `APP_LOG_TO_FILE=0`
- `APP_LOG_MAX_BYTES=1048576`

这会覆盖用户 raw `.env` 的日志设置。

### 计划

1. 普通模式：`NodeProjectManager.syncRuntimeEnvIfProjectReady` 只在 key 缺失时写默认日志值。
2. Root 模式：shell 的 `upsert_env` 增加“仅缺失时写入” helper，例如 `ensure_env_default`。
3. 补测试覆盖已有日志值不被覆盖、缺失时写默认值。

### 建议改法

- 修改：`NodeProjectManager.kt:270-276`
  - 不再把日志项放入强制 `updates`；改成 default-only。
- 修改：`RootRuntimeController.kt:471-477`
  - shell 中对日志项用 default-only 写法。
- 测试：新增/扩展 `NodeProjectManagerRuntimeEnvTest.kt`、`RootRuntimeController...Test.kt`。

### 验收

- [ ] 现有 `.env` 里的日志项不会被覆盖。
- [ ] 缺失日志项会补默认值。
- [ ] 普通/Root 两条路径均覆盖。

### 实际验证

- 待填写。

---

## 5. WakeLock acquire 必须带超时

**状态：** TODO

### 问题

lint 指出 `NodeService.kt:269` 使用 `wakeLock.acquire()` 无 timeout，存在耗电/泄漏风险。

### 计划

1. 定义明确的 WakeLock timeout 常量。
2. 改为 `wakeLock.acquire(RUNTIME_WAKE_LOCK_TIMEOUT_MS)`。
3. timeout 选取偏长但有限，例如 6 小时；服务周期内 `syncRuntimeWakeLock()` 可重新获取。

### 建议改法

- 修改：`NodeService.kt`
  - 增加 `private const val RUNTIME_WAKE_LOCK_TIMEOUT_MS = 6 * 60 * 60 * 1000L`
  - `wakeLock.acquire(RUNTIME_WAKE_LOCK_TIMEOUT_MS)`

### 验收

- [ ] lint 不再报告 `WakelockTimeout` 或该项消失。
- [ ] 服务停止仍会 release。

### 实际验证

- 待填写。

---

## 6. GitHub token 使用 SecureStringStore 加密并兼容迁移

**状态：** TODO

### 问题

`SettingsRepositoryImpl.setGithubToken()` 直接 `putString("github_token", normalized)` 明文保存。项目已有 `SecureStringStore`，Admin/WebDAV 已使用加密存储。

### 计划

1. 在 `SettingsRepositoryImpl` 和 `GithubProxyService` 使用同一个 key alias 的 `SecureStringStore`。
2. 读取 token 时调用 secure store；旧明文会由 `SecureStringStore.get()` 自动迁移为 `enc:v1:`。
3. 写入 token 时调用 `secureStore.put()`。
4. 补单元测试难度较高（依赖 Android Keystore），本轮至少做源码一致性和编译验证；如已有 Robolectric/Android 环境再补迁移测试。

### 建议改法

- 修改：`SettingsRepositoryImpl.kt`
  - import `SecureStringStore`
  - `private val githubTokenStore = SecureStringStore(githubAuthPrefs, "danmuapi_github_auth_v1")`
  - `_githubToken = MutableStateFlow(githubTokenStore.get("github_token"))`
  - `setGithubToken` 用 `githubTokenStore.put(...)`
- 修改：`GithubProxyService.kt`
  - 读取 Authorization token 时用同 alias 的 `SecureStringStore`。

### 验收

- [ ] 不再有 `putString("github_token"`。
- [ ] `GithubProxyService` 不再用 `githubAuthPrefs.safeGetString("github_token")` 明文读取。
- [ ] 编译通过。

### 实际验证

- 待填写。

---

## 7. 构建链 sibling node_modules 风险先文档化并做前置诊断

**状态：** TODO

### 问题

`syncBundledNodeModulesFromWorkspace` 强依赖 `../danmu_api/node_modules`，新环境容易失败。本轮目标是发 APK，不做大改构建链，先保证当前环境可诊断、可验证。

### 计划

1. 增加追踪文档中的 release 前置检查。
2. 构建前明确验证：
   - `../danmu_api/node_modules` 存在；
   - `app/src/main/assets/nodejs-project/node_modules/data-uri-to-buffer/dist/index.js` 存在。
3. 若构建失败，不跳过 Gradle 依赖校验，按 release skill 恢复真实输入。

### 建议改法

- 本轮不改 Gradle 构建逻辑，避免 release 前引入大范围构建链变更。
- 在最终验证命令中加入文件存在检查和 APK zip 内容检查。

### 验收

- [ ] 构建前置检查通过。
- [ ] release APK 中关键 deep runtime entry 存在。

### 实际验证

- 待填写。

---

## 完成记录

| 编号 | 问题 | 状态 | 完成时间 | 验证 |
|---|---|---|---|---|
| 1 | Root 核心状态读取 | DONE | 2026-06-18 | `CoreLocationPolicyTest` + `:app:testDebugUnitTest` 通过 |
| 2 | Root stale pid 假阳性 | DONE | 2026-06-18 | `RootRuntimeUptimePolicyTest` + `:app:testDebugUnitTest` 通过 |
| 3 | Root marker 清理 | DONE | 2026-06-18 | `RootRuntimeControllerMarkerTest` + `:app:testDebugUnitTest` 通过 |
| 4 | 日志配置不覆盖 raw `.env` | DONE | 2026-06-18 | `RuntimeEnvDefaultsTest` + `:app:testDebugUnitTest` 通过 |
| 5 | WakeLock timeout | DONE | 2026-06-18 | `NodeServiceWakeLockPolicyTest` + `:app:testDebugUnitTest` 通过 |
| 6 | GitHub token 加密存储 | DONE | 2026-06-18 | `SettingsRepositoryImpl` / `GithubProxyService` 改为 `SecureStringStore`，`compileDebugKotlin` 随测试通过 |
| 7 | 构建链前置诊断 | DONE | 2026-06-18 | 已核验 sibling `../danmu_api/node_modules` 与关键 deep runtime asset 存在，作为 release 前置检查 |
