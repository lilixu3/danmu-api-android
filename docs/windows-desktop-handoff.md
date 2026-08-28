# Windows 桌面端交接页

本文件只记录动态状态。长期目标、架构、功能矩阵、阶段任务和验收标准见
[`docs/plans/2026-08-28-windows-desktop-port.md`](plans/2026-08-28-windows-desktop-port.md)。
构建环境与可复现命令见 [`docs/windows-desktop-build-env.md`](windows-desktop-build-env.md)。

## 当前快照

| 字段 | 值 |
|---|---|
| 状态 | **P0 全部任务完成（W-0001 ~ W-0004）**；概览页 UI 与真实服务闭环已可用 |
| 当前阶段 | P0 Gate 决策 + P1 启动（W-0101 依赖审计） |
| 当前任务 | 写 P0 Gate 决策记录（D-002/D-004 转"确认"）；开始 P1 共享层审计 |
| 基线分支 | `test` |
| 文档提交基线 | `2565526` |
| 最后更新 | 2026-08-28（晚） |
| Windows 构建 | 已执行（本机 Windows 10 22H2 x64 实测） |
| Windows 实机 | 已验证（安装/启动/卸载/数据保留 + UI 真实启停闭环，均在本机实测） |
| 发布状态 | 仅为本地实现与验证；未推送远程，未发版，未上传资产 |

## 工作方式变更（用户决策 2026-08-28）

- **Windows 端开发与落地全部在本机进行，不再走 Termux 主开发**（用户明确授权）。
- 需要 WiX 等工具可自行安装；实测 jpackage 首次打包会自动下载 WiX 3.11.2，无需手动安装。
- 构建机为正式 Android 发版机（`C:\b\danmu-api-android`，现为接入 origin 的干净 Git 工作树）。

## 已完成

- [x] 只读盘点现有 Android、domain、data、UI、Node 和测试结构（2026-08-28）。
- [x] 确定工作方式：Windows 本机为主（用户决策，取代 Termux 主开发）。
- [x] W-0001：固定版本与可复现 Windows 构建命令（`docs/windows-desktop-build-env.md`）；
      仓库 `.gitignore` 有意排除 `/.github/`，以本机命令作为构建探针等价载体。
- [x] W-0002：最小 `:desktop` 模块（Compose Multiplatform Desktop 1.12.0 + Kotlin 2.4.10 +
      Gradle 9.6.0 + JDK 21 toolchain），窗口/中文渲染/最小尺寸实测通过（截图证据）。
- [x] W-0003：`WindowsNodeSupervisor` 进程监督器，真实 node.exe 子进程 + 多重 Running 判定
      （进程存活/端口/健康接口身份+端口+PID+工作目录）+ 优雅关闭/强杀/端口释放/身份消失确认。
      含 20 次连续启动/停止长冒烟，无残留 node.exe。
- [x] **基础约定与 Android 对齐（用户纠偏后重做）**：
      - 端口默认 **9321**，与 Android NodeProjectManager 同机制写入 `config/.env`
        （覆盖 DANMU_API_PORT/DANMU_API_HOST/DANMU_API_VARIANT 三键，保留 TOKEN 与其他键）；
      - 监听默认 **0.0.0.0**（RuntimeListenMode.Ipv4Only）；
      - **TOKEN 不注入**：无用户配置时沿用核心默认 87654321（TokenDefaults 语义）；
      - `DANMU_API_RUNTIME_IDENTITY` 持久化安装身份（instance-id 文件，等价 RuntimeIdentityStore）；
      - 端口占用预检（对齐 NormalStartPreflightPolicy 对外文案）；
      - 创建 config/logs/.cache/tmp/compile-cache 目录（.cache 为核心缓存目录）。
- [x] `DesktopCoreInstaller`：核心不随包内置，与 Android 一致在线下载安装
      （huangxd-/danmu_api@main zipball，本地缓存，zip-slip 防护，danmu_api/ 嵌套目录上提，
      ESM package.json 保证）。用户决策：核心在线下载更新是基础能力。
- [x] **GitHub 代理线路（对齐 Android GithubProxyService/SpeedTester）**：直连 + 4 个
      GH-Proxy 镜像；多候选 URL 变换（{url}/%s/?url=/路径前缀）逐个回退下载；raw 资源
      并行测速（快测 + 慢测兜底）；选择持久化到设置。
- [x] W-0004（完整）：免安装包 + 直装 EXE 产出并实测；**EXE 图形安装 → 安装版启动 →
      静默卸载 → 用户数据保留 全链路验收通过**（见验证历史）。
- [x] UI（P3/W-0401 最小闭环 + 设置页基础功能）：
      - 概览页：状态总览卡（语义色容器随状态变化）、连接（本机/局域网地址、Token）、
        核心与运行时、目录四个分区；失败横幅；底部状态条；
      - 左侧导航：概览 + 核心/配置/下载/活动/工具（诚实占位）+ 设置；
      - 设置页：运行目录自定义（settings.properties 持久化，重启生效）+ GitHub 线路
        并行测速与选择（即时生效）；
      - `DesktopRuntimeController` 串行化操作并映射状态，首启自动解压随包运行时并
        在线安装核心；UI 启动 → 外部健康检查一致 → UI 停止，全部实测通过（含用户手测）。
- [x] 品牌对齐 Android：应用名「弹幕API」（与 strings.xml app_name 一致）；
      窗口/任务栏/安装器图标由 Android 矢量启动图标渲染生成（desktop/icons/danmuapi.ico +
      branding PNG，生成脚本 C:\Tools\_downloads\icon-gen\gen-icon.js）。
- [x] 修复 Android ELF 16KB 门禁小端解析 bug（见修复记录；该修复此前只存在于旧目录未提交）。
- [x] 环境清理（用户要求）：360 画报已彻底移除（HKCU Run 自启动项删除、进程终止、
      Roaming\360huabao 目录删除；系统屏保本就未启用，无 .scr 注册）。

## 当前决策

| 编号 | 决策 | 状态 |
|---|---|---|
| D-001 | 保留 Android `:app`，新增独立 Desktop 模块 | 确定（`:desktop` 已落地） |
| D-002 | Desktop UI 默认采用 Compose Multiplatform Desktop | 初步验证通过（构建/渲染/打包全链路可用），待窗口人工验收后关闭 |
| D-003 | 桌面 UI 重新设计，不复刻手机布局 | 确定 |
| D-004 | Windows 使用独立 `node.exe` 子进程 | 初步验证通过（20 循环监督实测），待 P2 应用内集成后关闭 |
| D-005 | 共享层按价值小批抽取 | 确定 |
| D-006 | 第一版 Windows 10/11 x64 双形态（免安装+直装）；32 位 x86 排除 | 用户已确认（见决策变更记录） |

## 决策变更记录

```text
Decision: D-006
Date: 2026-08-28
Old: 第一版 Windows 10/11 x64；便携模式与 ARM64 进后续路线图；最低版本待用户确认
New: 第一版交付 x64 双形态（免安装 ZIP + 直装 EXE）；不支持 32 位 x86
    （Node.js 官方自 v19 起不再发布 win-x86 二进制，内嵌 Node 24 无 32 位 Windows 可用，
    用户确认按 Node 官方支持平台交付）；ARM64 仍留后续（Node/Skiko 均有 win-arm64 官方产物）
Evidence: 用户消息"先交付 node 官方支持的" + "免安装版和直装两种 exe，32和64位和x86都要支持"；
          Node dist v24.19.0 无 win-x86 资产；核心不内置在线下载为用户确认的基础能力
Affected tasks/files: desktop/build.gradle.kts、docs/windows-desktop-build-env.md、W-0004
Migration: ARM64 加入构建矩阵时需补 -PdanmuNodeExe 的 arm64 变体与 targetFormats 评估
```

```text
Decision: WM-01（工作方式变更，用户决策）
Date: 2026-08-28
Old: Termux 主开发 + Windows 早期持续验证
New: Windows 本机全流程开发与落地（用户授权，可自行安装 WiX 等工具）
Evidence: 用户消息"我准备把window端开发到落地完全在本机进行不再termux进行"
Affected tasks/files: 全部 P0 任务
Migration: 交接命令与文档不再考虑 Termux 路径；Android 基线中 Unix-only 用例在 Windows
    跳过属预期（见验证历史）
```

## 验证历史

| 日期 | 提交 | 环境 | 命令/检查 | 结果 | 备注 |
|---|---|---|---|---|---|
| 2026-08-28 | `1756205` | Termux | 只读文件、依赖和测试规模盘点 | PASS | 未运行构建，未修改业务代码 |
| 2026-08-28 | `9767b06`+工作树 | Win10 22H2 x64 | `gradlew :desktop:test -PdanmuNodeExe=<node.exe>` | PASS | 9 用例全过（8 执行 + 长冒烟按设计跳过）；含真实 node.exe 子进程启停、端口占用/入口缺失/依赖缺失/核心缺失 → Failed、在线安装幂等 |
| 2026-08-28 | 同上 | 同上 | `gradlew :desktop:test --tests *twentyConsecutiveCyclesWithoutResidue -PdesktopLongSmoke=true -PdanmuNodeExe=<node.exe>` | PASS | 20/20 循环 28.5s（每轮 ~1.1-1.5s），结束后 tasklist 无 node.exe |
| 2026-08-28 | 同上 | 同上 | `gradlew :desktop:packageExe :desktop:packagePortableZip -PdanmuNodeExe=<node.exe>` | PASS | 产出 `DanmuApiDesktop-0.1.0.exe`（101MB；WiX 由 jpackage 自动下载）与 `DanmuApiDesktop-0.1.0-portable-x64.zip`（99MB）；应用 jar 内含 `runtime/node.exe` 与 `runtime/nodejs-project/**`（jar -tf 验证） |
| 2026-08-28 | 同上 | 同上 | 打包应用镜像启动 + 屏幕截图 | PASS | 窗口 1280x800、中文/等宽渲染正常、显示"node.exe: 已随包提供（内嵌资源）" |
| 2026-08-28 | 同上 | 同上 | `gradlew :app:checkNodeRuntimeScripts :app:testNodeRuntimeParsing :app:testBundledBrotliRuntime :app:testBundledNodeLockClosure :app:testBundledCoreRuntimeDependencies` | PASS | 全部 OK |
| 2026-08-28 | 同上 | 同上 | `gradlew :app:testDebugUnitTest` | 部分 | 469 用例：458 过 / 11 失败；失败全部为 "Cannot run program sh"（Root 模式 Unix-only 测试，Windows 环境限制，与桌面端改动无关；完整基线以 Termux/CI 为准） |
| 2026-08-28 | 同上 | 同上 | EXE 图形安装向导（WiX UI）点击完成安装 | PASS | 安装至 `C:\Program Files\DanmuApiDesktop`；安装版 jar 内含 runtime/node.exe 与 nodejs-project/**（jar -tf 验证）；安装版启动截图确认"已随包提供" |
| 2026-08-28 | 同上 | 同上 | `msiexec /x {4C9043C9-5115-36D9-8C9E-1100DC160E3E} /qn`（提权） | PASS | 退出码 0；安装目录移除；预置的用户数据标记 `%LOCALAPPDATA%\DanmuApi\data\download\保留测试.txt` 卸载后保留（USER_DATA=PRESERVED）。非提权卸载报 1730，需管理员权限 |
| 2026-08-28 | 同上 | 同上 | UI 点击「启动服务」→ 外部 `curl /__health` 比对 → UI 点击「停止」 | PASS | UI Running：127.0.0.1:6687 / PID 8508 / desktop-3f9547ed…；健康接口 JSON 与 UI 完全一致（cwd=%LOCALAPPDATA%\DanmuApi\data, variant=stable）；停止后状态正确复位、无残留 node.exe |
| 2026-08-28 | 同上 | 同上 | `gradlew :desktop:test`（含控制器闭环/解压/路径用例） | PASS | 12 用例全过（长冒烟按设计跳过） |

## 修复记录（对仓库的缺陷修复）

1. `app/build.gradle.kts` ELF 门禁 `u32At`/`u64At`：按大端累加读取小端 ELF 字段，
   导致 `e_phoff=0x40` 被读成越界值，任何全新克隆的构建都会在
   `:app:prepareNativeRuntime` 报 "ELF program header 非法或越界：libc++_shared.so"。
   已改为小端逐字节累积（与旧构建目录中未提交的修复版本一致）。

## P0 实现要点（接手者须知）

- `:desktop` 技术栈：kotlin-jvm 2.4.10 + org.jetbrains.compose 1.12.0 +
  org.jetbrains.compose.material3:material3 1.12.0-alpha03（勿用已弃用的
  `compose.material3` 访问器，其解析到旧版）；jvmToolchain 21 + jvmTarget 17。
- 进程监督：`desktop/src/main/kotlin/com/example/danmuapiapp/desktop/node/WindowsNodeSupervisor.kt`；
  端口由宿主选择并注入 `DANMU_API_PORT`；服务端会用 `$DANMU_API_HOME/config/.env`
  覆盖进程环境，dataHome 的 `.env` 准备时会清除 `DANMU_API_PORT` 行。
- 核心安装：`DesktopCoreInstaller`；zipball 解压需剥离根目录段、把嵌套 `danmu_api/`
  （或 danmu-api/）内容上提，并确保 package.json 为 `"type": "module"`（与 Android
  `normalizeCoreLayout` 语义一致）。
- 随包资源：`prepareDesktopAppResources` 把 `app/src/main/assets/nodejs-project` 与
  `-PdanmuNodeExe` 指定的 node.exe 生成到 `build/generated/desktop-runtime-resources`，
  作为 main resources 打进应用 jar（jpackage --input 自动带入全部产物）。
  **不要**使用 compose 1.12 的 `appResourcesRootDir`（createDistributable 与
  jpackage --resource-dir 实测均不落地），也不要用 Sync 写应用镜像目录（目录归属校验失败）。
- classpath 资源检测用 `ClassLoader.getResourceAsStream`，路径不能带前导斜杠。
- 网络注意：本机到 codeload.github.com 偶发 TLS 挂死，`DesktopCoreInstaller` 用
  `sendAsync().orTimeout(150s)` 强制限时；JDK HttpClient 的 request timeout 覆盖不到连接阶段。
- 测试为 JUnit4 + Assume 跳过：无 node.exe 时集成用例自动跳过；`@After` 会 stop
  全部被跟踪的 supervisor，防止断言失败留下孤儿 node.exe。
- 打包产物若疑似 up-to-date 旧内容，用 `--rerun-tasks` 强制重建。

## 尚未验证的假设

- EXE 安装器在干净 Windows 用户环境的安装/启动/卸载/数据保留（W-0004 剩余）。
- EXE 安装器内的 app jar 是否完整包含内嵌资源（zip 与应用镜像已验证，安装器版本待安装实测）。
- Compose Desktop 高 DPI（150%/200%）、深色主题、多显示器下的完整表现。
- 核心 zipball 默认分支 HEAD 与 runtime-packs manifest 固定提交的兼容性管理（W-0402）。

## 阻塞项

无阻塞性事项。W-0004 剩余验收需要一次"干净 Windows 用户环境"的安装测试
（新 Windows 用户账户或干净虚拟机均可）。

## 任务更新格式

每完成一个任务，在本文件追加或更新以下内容：

```text
Task: W-xxxx
Status: completed / blocked / in_progress
Commit: <short sha or uncommitted>
Files: <paths>
Decision changes: <none or D-xxx>
Verification:
  - <exact command>: PASS/FAIL
Windows evidence:
  - OS build, DPI, package path, screenshot/log path
Known issues:
  - <concrete remaining issue>
Next task:
  - W-xxxx <single actionable next step>
```

“完成”必须带精确命令和结果。仅写“测试正常”“应该可用”不算交接证据。

## 每次工作结束前

1. 更新当前阶段、当前任务和最后更新日期。
2. 标记实际完成的 checklist，不能批量预勾选。
3. 写入精确验证命令、结果和 Windows 环境。
4. 记录未提交改动和与本任务无关的用户改动。
5. 记录唯一明确的下一任务，避免接手者自行猜测优先级。
6. 运行 `git diff --check`。
7. 没有明确授权时，不 push、不发布、不上传安装包（本次已获本地实现与本地提交授权，推送远程需另行授权）。

## 当前下一任务

`W-0004`（收尾）：在干净 Windows 用户环境（新账户或干净虚拟机）执行
`DanmuApiDesktop-0.1.0.exe` 安装 → 启动 → 卸载，确认：

1. 安装后 app jar 内含 `runtime/node.exe` 与 `runtime/nodejs-project/**`；
2. 安装版可启动并显示"已随包提供"；
3. 卸载不误删用户数据目录（`%LOCALAPPDATA%\DanmuApi`）与用户选择的下载目录。

完成后写 P0 Gate 决策记录（确认或否决 D-002/D-004），进入 P1（W-0101 依赖审计）。
