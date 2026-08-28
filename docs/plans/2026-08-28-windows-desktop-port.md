# Windows 桌面端落地总计划

| 字段 | 值 |
|---|---|
| 日期 | 2026-08-28 |
| 状态 | 已规划，尚未开始实现 |
| 当前阶段 | P0 技术可行性与 Windows 构建闭环 |
| 动态交接页 | [`docs/windows-desktop-handoff.md`](../windows-desktop-handoff.md) |

## 1. 目标

为现有 `danmu-api-android` 增加一个可以独立安装、运行和维护的 Windows
桌面端。Windows 端不是 Android 页面在桌面上的简单复制，而是共享弹幕核心、
领域契约和可复用业务逻辑，同时具备符合桌面工作流的 UI、进程管理和发布质量。

正式版至少需要满足以下结果：

1. 可以安装、启动、停止和重启内置 Node 弹幕服务。
2. 可以完成核心安装、切换、更新、回退和自定义核心管理。
3. 可以完成可视化配置、日志、请求记录、接口测试、弹幕推送、下载、缓存、
   设备访问控制和备份恢复等适用于 Windows 的功能。
4. 支持托盘、开机启动、窗口缩放、键盘操作、系统主题、高 DPI、崩溃恢复和
   可靠更新。
5. Android 端行为、构建产物和发布流程不因桌面端引入而回退。
6. 所有“完整”声明都必须由自动化测试和 Windows 实机验收支持。

## 2. 非目标

以下内容不属于第一版范围，除非后续决策记录明确改变：

- 不把 Xposed、Android 无障碍保活、快捷设置磁贴或 APK 安装能力伪装成
  Windows 功能。
- 不在第一版支持 macOS、Linux、Windows ARM64 或 Microsoft Store。
- 不为了共享代码而强行把 Android 全量迁移成 Kotlin Multiplatform。
- 不在没有 Windows 运行证据时声称托盘、安装器、更新、高 DPI 或休眠恢复已完成。
- 不在本计划中执行推送、发版、上传资产或配置签名密钥。

## 3. 当前代码基线

以下数据是 2026-08-28 对提交 `1756205` 的只读盘点，后续接手者应在 P0
重新生成一次并更新动态交接页：

| 区域 | 当前情况 | 对 Windows 的含义 |
|---|---|---|
| Gradle 模块 | 仅 `:app` Android Application | 不能直接添加一个 Windows source set |
| UI | 130 个 Kotlin 文件，约 61,082 行 | 可复用交互语义和部分 Compose 组件，页面布局需桌面化 |
| UI Android 耦合 | 40 个文件直接导入 `android.*` | 文件选择、剪贴板、Activity、安装器等需平台接口 |
| data | 108 个 Kotlin 文件，约 27,802 行 | 运行时、存储、Root、通知和更新实现高度平台化 |
| domain | 30 个 Kotlin 文件，约 2,830 行，无直接 `android.*` 导入 | 第一批共享候选 |
| Node 宿主源码 | 排除依赖后约 3,635 行 | 大部分标准 Node API 可共享，生命周期入口需拆分 |
| JVM 单测 | 107 个文件，约 469 个 `@Test` | 是 Android 回归基线，但不能替代 Windows 测试 |
| Android UI 仪器测试 | 当前没有 | Windows UI 必须建立自己的自动化和实机验收 |

关键现状文件：

- `settings.gradle.kts`
- `app/build.gradle.kts`
- `docs/architecture.md`
- `app/src/main/java/com/example/danmuapiapp/domain/`
- `app/src/main/java/com/example/danmuapiapp/data/service/NodeService.kt`
- `app/src/main/assets/nodejs-project/main.js`
- `app/src/main/assets/nodejs-project/android-server.js`

## 4. 开发环境策略

采用“Termux 主开发 + Windows 早期持续验证”的混合方式。

| 工作内容 | Termux 本机 | Windows 机器或 Windows CI |
|---|---:|---:|
| 代码审计、方案、领域层拆分 | 主环境 | 可复核 |
| Node 平台中立化和契约测试 | 主环境 | 必须复跑 |
| Android 编译和回归测试 | 主环境 | 可选复跑 |
| Desktop JVM 非 UI 单测 | 可尝试 | 必须通过 |
| Windows 窗口和 Compose 渲染 | 不作为验收环境 | 必须 |
| `node.exe` 子进程与休眠恢复 | 无法验收 | 必须 |
| 托盘、开机启动、防火墙、通知 | 无法验收 | 必须 |
| MSI/EXE 安装、升级、卸载、签名 | 无法验收 | 必须 |
| 高 DPI、多显示器、系统主题 | 无法验收 | 必须 |

Windows 验证不得只安排在项目末尾。P0 必须先打通 Windows 构建、启动 Node、
健康检查和测试安装包，否则暂停大规模 UI 开发。

## 5. 暂定技术决策

每项决策都有编号。改变决策时必须在本文和交接页记录原因、影响和迁移步骤。

### D-001：保留 Android `:app`，新增独立桌面模块

状态：确定。

不直接把现有 Android 模块改成多平台模块。先用独立模块隔离风险，再按文件逐步
抽取共享代码。

### D-002：桌面 UI 默认采用 Compose Multiplatform Desktop

状态：暂定，必须通过 P0 验证。

原因：现有团队和代码使用 Kotlin、Compose、Coroutines 与 Flow，可以复用领域模型、
状态管理思想、格式化逻辑和一部分无平台依赖组件。

P0 否决条件：当前 Kotlin/Gradle 组合无法稳定构建；基本可访问性或输入法存在阻断；
安装包不可维护；关键 Windows 集成只能依靠高风险方案。若触发否决，再评估 WinUI 3，
并接受前端和客户端业务层基本重写的成本。

### D-003：桌面 UI 重新设计，不复刻手机布局

状态：确定。

共享的是设计 token、组件语义和业务状态，不默认共享整页布局。桌面端采用左侧导航、
稳定工具栏、可比较的数据表格、多栏详情和鼠标键盘工作流。

### D-004：Windows 使用独立 `node.exe` 子进程

状态：暂定，必须通过 P0 验证。

Windows 不嵌入 Android JNI Node。应用打包与 Android 正式运行时相同版本的 Windows
Node，使用受控子进程启动，共享平台中立 Node 服务代码。

### D-005：共享层按价值逐步抽取

状态：确定。

第一阶段只抽取无 Android 依赖、且 Windows 确实需要的模型、接口、解析器和策略。
禁止一次性移动整个 `domain`、`data` 或 `ui` 目录。

### D-006：Windows 第一版支持 Windows 10/11 x64

状态：暂定，需用户确认最低系统版本。

ARM64、便携模式和商店分发进入后续路线图。第一版先减少打包和测试组合。

## 6. 目标架构

```text
                           +--------------------------+
                           | shared-domain            |
                           | models, contracts,       |
                           | parsers, pure policies   |
                           +------------+-------------+
                                        |
                   +--------------------+--------------------+
                   |                                         |
        +----------v-----------+                  +----------v-----------+
        | Android :app         |                  | Windows :desktop     |
        | Hilt / Android UI    |                  | desktop UI / graph   |
        +----------+-----------+                  +----------+-----------+
                   |                                         |
        +----------v-----------+                  +----------v-----------+
        | AndroidRuntimeHost   |                  | WindowsRuntimeHost   |
        | Service / JNI Node   |                  | child process / tray |
        +----------+-----------+                  +----------+-----------+
                   |                                         |
                   +--------------------+--------------------+
                                        |
                           +------------v-------------+
                           | shared Node runtime      |
                           | HTTP API + core variants |
                           +--------------------------+
```

### 6.1 建议模块布局

以下路径是目标结构，在对应阶段创建，不要求 P0 一次完成：

```text
app/                              # 保留 Android Application
desktop/                          # Windows Desktop Application
shared/
  domain/                         # 模型、仓库接口、纯策略
  runtime-contract/               # 健康状态、管理协议、序列化 DTO
runtime/
  node/                           # 平台中立 Node 服务源码
  android/                        # Android 入口和嵌入式 Node 适配
  windows/                        # Windows 入口、运行时清单和打包脚本
desktop-tests/
  fixtures/                       # 契约与故障注入数据
docs/
  plans/2026-08-28-windows-desktop-port.md
  windows-desktop-handoff.md
```

是否使用 `:shared:domain` 普通 Kotlin/JVM 模块或 Kotlin Multiplatform 模块，由
P1 的依赖审计决定。Android 和 Windows 都运行 JVM，除非有明确收益，不为“多平台”
标签引入额外 source set 复杂度。

### 6.2 依赖方向

允许：

```text
app -> shared-domain
desktop -> shared-domain + runtime-contract
Android platform implementation -> shared interfaces
Windows platform implementation -> shared interfaces
```

禁止：

```text
shared-domain -> android.*
shared-domain -> desktop UI toolkit
shared-domain -> Hilt
runtime Node -> Android 文件路径或 Android 生命周期假设
desktop -> app Android module
```

## 7. Node 运行时平台中立化

### 7.1 拆分原则

现有 `android-server.js` 中 HTTP、配置、核心选择、worker、日志、缓存、管理接口等
保留为共享实现；以下内容必须抽成平台适配：

- 入口文件名和启动参数。
- 嵌入式 Node 不能 `process.exit()` 的特殊退出策略。
- `HOME`、`TMPDIR`、编译缓存和默认工作目录。
- 宿主进程身份、PID、信号与强制终止策略。
- Android 专用注释、错误文案和宿主回调。

建议最终形态：

```text
runtime-server.js       # 创建并管理 HTTP 服务，可测试、可导入
main-android.js          # 嵌入式 Node 入口，等待事件循环自然退出
main-windows.js          # 标准 Node 入口，处理 SIGINT/SIGTERM 和退出码
runtime-platform.js      # 小型平台策略对象，不包含 UI 或系统集成
```

迁移期间保留 `main.js` 作为 Android 兼容入口，直到 APK 构建、Node smoke 和真实设备
启动都通过。不要先改名再补测试。

### 7.2 Windows 进程状态机

桌面宿主必须使用显式状态机：

```text
Stopped -> Preparing -> Starting -> Running -> Stopping -> Stopped
                         |             |          |
                         +-----------> Failed <---+
```

`Running` 的判定同时要求：

1. 子进程仍存活。
2. 配置端口已监听。
3. 健康接口返回预期 runtime identity。
4. 健康接口的工作目录和当前选择一致。

`Stopped` 的判定同时要求：

1. 已请求优雅关闭。
2. 子进程确认退出，或超时后完成受控终止。
3. 旧端口关闭。
4. 旧 runtime identity 不再返回。

禁止仅凭一条 STOPPED 消息或一个进程句柄为空就立即重启。

### 7.3 Windows 环境变量

至少显式传入：

```text
DANMU_API_HOME
DANMU_API_VARIANT
DANMU_API_RUNTIME_IDENTITY
HOME
TEMP
TMP
NODE_COMPILE_CACHE
```

所有路径通过参数或环境注入，不在 JavaScript 中拼接盘符。必须测试空格、中文、
长路径和只读目录。

### 7.4 默认数据目录

暂定目录：

| 数据 | 默认位置 |
|---|---|
| 桌面设置 | `%APPDATA%\DanmuApi\settings.json` |
| 工作目录 | `%LOCALAPPDATA%\DanmuApi\data` |
| Node 和核心运行资产 | `%LOCALAPPDATA%\DanmuApi\runtime` |
| 桌面宿主日志 | `%LOCALAPPDATA%\DanmuApi\logs` |
| 下载目录 | 用户首次选择，未选择时不擅自写入公共目录 |

卸载时默认保留用户工作目录和下载文件。清理用户数据必须是独立、明确且可撤销确认的
操作。

### 7.5 管理接口安全

- 桌面专用控制接口只监听 loopback。
- 每次宿主启动生成高熵 control token，不写入普通日志。
- 局域网公开 API 继续使用用户配置的访问 token 和监听模式。
- 健康接口返回 runtime identity，但不返回密钥、Cookie 或完整文件系统信息。
- 日志、崩溃报告和诊断导出沿用敏感数据脱敏规则。
- 更新包必须校验 SHA-256；正式发布还需验证发布签名。

## 8. Windows 桌面 UI 信息架构

### 8.1 应用外壳

桌面端第一视口直接进入可操作的管理界面，不增加营销首页。

```text
+----------------------+-----------------------------------------------+
| Product / status     | Page title                  global actions   |
|----------------------|-----------------------------------------------|
| Overview             |                                               |
| Core                 |             page content                      |
| Configuration        |                                               |
| Downloads            |                                               |
| Activity             |                                               |
| Tools                |                                               |
| Settings             |                                               |
|----------------------|-----------------------------------------------|
| runtime version      | status bar / background operation progress    |
+----------------------+-----------------------------------------------+
```

基本规则：

- 默认窗口建议 `1280 x 800`，最小可用尺寸暂定 `960 x 640`。
- 宽屏使用列表和详情双栏；窄窗口收敛为单栏，不让文字或按钮互相遮挡。
- 导航使用图标加文字；工具栏常用动作使用熟悉图标并提供 tooltip。
- 数据密集页面优先表格、列表和分区，不堆叠装饰卡片。
- 支持系统浅色、深色和跟随系统；Windows 玻璃或材质效果必须有不透明降级。
- 所有主要流程可由键盘完成，焦点可见，Tab 顺序稳定。
- 支持 100%、125%、150%、200% DPI 和多显示器移动。
- 大列表必须虚拟化，并使用稳定 key；动态状态不能改变固定工具栏尺寸。

### 8.2 页面结构

| 一级页面 | 主要内容 |
|---|---|
| 概览 | 服务状态、地址、启动/停止、核心状态、近期错误、后台任务 |
| 核心 | 稳定/开发/自定义核心、版本、分支、更新、回退、依赖修复 |
| 配置 | 分类表单、搜索、原始 `.env`、凭证编辑、变更预览与应用 |
| 下载 | 搜索、队列、历史、预览、批量操作、目录和限速设置 |
| 活动 | 实时日志、请求记录、筛选、复制、诊断导出 |
| 工具 | API 测试、弹幕推送、缓存、设备访问、专项诊断 |
| 设置 | 运行时、工作目录、网络、GitHub、备份、主题、启动与更新 |

## 9. 功能对照表

状态含义：

- `共享`：复用同一领域契约、Node API 或纯逻辑。
- `替代`：Windows 提供等价系统能力。
- `重做`：业务目标保留，但客户端实现与 UI 重写。
- `不适用`：属于 Android 或宿主播放器注入能力，Windows 不做空壳。

| Android 功能 | Windows 策略 | 第一版验收 |
|---|---|---|
| 启动/停止/重启服务 | 共享协议，重做进程宿主 | 崩溃、端口冲突、快速重启均可恢复 |
| 前台服务通知 | 替代为托盘和桌面通知 | 关闭窗口后可按设置留在托盘 |
| 开机自启 | Windows 登录启动替代 | 可开启、关闭并检测真实注册状态 |
| 无障碍保活 | 不适用 | 不显示无效开关 |
| Root 运行模式 | 不适用 | 数据迁移时安全忽略 Android-only 设置 |
| Xposed/影视壳注入 | 不适用 | About 或兼容性页明确说明平台限制 |
| 快捷设置磁贴 | 不适用 | 托盘菜单提供常用动作 |
| 稳定/开发/自定义核心 | 共享模型和下载协议，重做文件操作 | 安装、切换、更新、回退完整可用 |
| GitHub 代理与测速 | 共享远程逻辑或契约 | 代理、token、限流错误可诊断 |
| 可视化配置 | 共享 schema/解析，重做桌面 UI | 保存前校验和变更预览，运行中可热更新 |
| Bilibili 扫码登录 | 共享本地 API，重做二维码窗口 | 生成、轮询、超时、取消、成功闭环 |
| API 测试 | 共享接口语义，重做工作台 | 请求、响应、历史和错误展示完整 |
| 弹幕推送 | 共享 API，重做表单 | 搜索、选集、参数覆盖和结果反馈完整 |
| 请求记录 | 共享 API，重做表格 | 筛选、详情、复制和清空完整 |
| 实时日志 | 共享 API，增加桌面宿主日志 | 多来源、筛选、暂停滚动、导出完整 |
| 设备访问控制 | 共享 API，重做表格/编辑器 | 扫描、黑名单、校验和保存完整 |
| 弹幕下载 | 共享模型/解析，重做目录和文件 IO | 队列、重试、冲突策略、预览、历史完整 |
| 缓存管理 | 共享 API，重做 UI | 统计、明细、选择性清理完整 |
| WebDAV 备份恢复 | 共享格式，重做凭证存储 | 备份、恢复、冲突、失败回滚完整 |
| 工作目录 | 替代为 Windows 文件夹选择 | 迁移、权限、回滚和中文路径完整 |
| App 更新 | 替代为 Windows 安装包更新 | 下载、校验、退出安装、失败恢复完整 |
| TV/盒子兼容模式 | 不适用 | 不携带手机/电视专用 UI |
| 手机配置同步二维码 | 待产品决策 | P0 确定作为发送端、接收端或暂缓 |

## 10. 分阶段实施计划

任务编号永久稳定。拆分或废弃任务时保留原编号并在交接页标记，避免不同接手者引用
同名但含义不同的任务。

### P0：技术可行性和 Windows 闭环

目标：在投入完整 UI 前消除工具链、运行时和打包三项最高风险。

#### W-0001：记录基线并建立 Windows 构建工作流

输出：

- Windows 构建环境说明。
- 一个只构建、不发布的 Windows CI job 或可复现的 Windows 本机命令。
- 固定 JDK、Gradle、Kotlin、Compose 和 Node 版本。

验收：

- Android 基线测试保持通过。
- Windows job 能检出仓库并执行 Gradle，不依赖开发者私有绝对路径。
- CI 不读取签名密钥，不上传 Release，不推送源码。

#### W-0002：最小 Desktop 模块

建议文件：

```text
desktop/build.gradle.kts
desktop/src/main/kotlin/.../DesktopMain.kt
desktop/src/test/kotlin/.../DesktopSmokeTest.kt
```

验收：

- Windows 上可打开、缩放和关闭一个最小窗口。
- 浅色、深色、中文文本和 150% DPI 无明显异常。
- `gradlew.bat :desktop:test` 通过。

#### W-0003：Node 子进程与健康检查 spike

输出：

- 使用固定 Node 版本启动现有运行时副本。
- 捕获 stdout/stderr。
- 健康检查 runtime identity。
- 优雅停止，超时后受控终止。

验收：

- 连续启动/停止 20 次无残留 `node.exe`。
- 快速重启不会复用旧进程或旧端口。
- 端口占用、入口缺失、依赖缺失都进入明确 Failed 状态。

#### W-0004：测试安装包 spike

验收：

- 生成未签名内部测试安装包。
- 在干净 Windows 用户环境安装、启动、卸载。
- 卸载不误删用户选择的下载目录。

P0 Gate：W-0001 至 W-0004 全部通过后，确认 D-002/D-004 并进入 P1。任一失败都
必须先写决策记录，不允许绕过后继续堆页面。

### P1：共享领域和契约基础

#### W-0101：依赖审计

逐个列出 Windows 首批需要的 domain 模型、repository 接口、解析器、格式化器和策略。
为每个文件标记：直接共享、拆分后共享、Android 保留、暂不迁移。

验收：审计表覆盖首批概览、核心、配置和日志流程，且没有 `android.*`、Hilt 或资源 ID
泄漏进共享模块。

#### W-0102：建立 `shared-domain`

先迁移最小闭环需要的模型和纯逻辑。Android import 路径按小批次更新，每批都运行
Android 测试。

验收：

- Android API 和序列化结果不变。
- 共享模块自身单测通过。
- Android Debug APK 可构建。

#### W-0103：建立 `runtime-contract`

定义运行时状态、健康响应、启动配置、错误分类和宿主操作接口。DTO 使用明确版本，
禁止 UI 直接解析任意 JSON map。

验收：Android 和 Desktop 使用同一 fixture 得到相同模型结果；未知字段向前兼容；
缺少必填字段给出结构化错误。

### P2：共享 Node 运行时与 Windows 宿主

#### W-0201：为现有 Node 行为建立契约测试

覆盖健康、关闭、日志、配置热更新、核心切换、管理 token、错误响应和 runtime identity。
测试必须先针对现有 Android 入口通过，再开始拆文件。

#### W-0202：拆分平台中立 Node server

按第 7 节拆出共享 server 和 Android/Windows 入口。保持 Android `main.js` 兼容直到
所有 smoke 与 APK 启动验证通过。

#### W-0203：实现 `WindowsRuntimeSupervisor`

职责：资产准备、环境变量、进程启动、日志读取、健康轮询、优雅关闭、强制终止、
端口与身份校验、崩溃分类和恢复策略。

#### W-0204：实现 Windows 路径和设置存储

使用原子写入；损坏配置可回退；密钥进入 Windows 凭证保护方案，不以明文出现在
普通设置导出中。

P2 Gate：无 UI 参与也能由集成测试完成准备、启动、健康检查、配置、停止和再次启动。

### P3：桌面设计系统与应用外壳

#### W-0301：确定桌面视觉规范

输出颜色、字体、间距、圆角、边框、状态色、焦点、hover、pressed、disabled、加载、
空状态和错误状态 token。保持项目辨识度，但不依赖 Android RuntimeShader。

#### W-0302：实现应用外壳和导航

实现侧边导航、页面标题区、全局运行状态、后台任务进度、通知区域和响应式窗口规则。

#### W-0303：建立桌面组件集

至少包括：命令按钮、图标按钮、菜单、分段控制、开关、数值输入、路径选择器、
数据表格、虚拟列表、日志查看器、确认对话框和危险操作对话框。

#### W-0304：可访问性与输入验收

覆盖键盘焦点、快捷键、屏幕缩放、中文输入法、长文本、错误提示和颜色对比。

P3 Gate：仅使用假数据也能完成概览、列表详情、设置表单和长日志四类代表页面的
Windows 截图与人工评审。

### P4：首批完整业务流程

#### W-0401：概览与服务控制

状态、地址复制、启动/停止/重启、核心摘要、近期错误、后台任务和托盘状态一致。

#### W-0402：核心管理

稳定/开发/自定义核心的安装、更新、切换、回退、分支和依赖修复全部走真实实现，
不使用永久 mock。

#### W-0403：配置管理

分类表单、原始模式、校验、变更预览、凭证编辑、热更新和需要重启的提示完整。

#### W-0404：活动中心

实时日志、桌面宿主日志和请求记录统一查看；大数据量虚拟化；支持筛选、暂停、复制、
导出和清理。

P4 Gate：新用户可以从安装开始，在不打开命令行的情况下安装核心、配置并启动服务，
从局域网客户端成功调用一次 API，并能在活动中心找到对应记录。

### P5：工具、下载和数据迁移

#### W-0501：API 测试工作台

#### W-0502：弹幕推送与扫码登录

#### W-0503：设备访问与缓存管理

#### W-0504：弹幕下载、队列、历史和预览

#### W-0505：WebDAV 备份恢复与 Android 数据导入

每项必须覆盖成功、取消、超时、部分失败、重试和数据恢复。下载队列使用稳定 key、
虚拟列表和批量操作，至少用 5,000 条任务做性能验收。

### P6：Windows 系统集成

#### W-0601：系统托盘与关闭行为

#### W-0602：开机启动和真实状态检测

#### W-0603：桌面通知与勿扰降级

#### W-0604：防火墙和局域网访问引导

#### W-0605：休眠、唤醒、用户注销和异常退出恢复

#### W-0606：工作目录迁移与磁盘空间处理

P6 Gate：系统集成必须在 Windows 10 和 11 实机通过，不接受仅单测或代码检查。

### P7：更新、安装与发布准备

#### W-0701：版本和通道模型

Android 与 Windows 可以共享产品版本语义，但资产、最低系统和升级规则独立记录。

#### W-0702：更新下载、校验与安装交接

覆盖无更新、有更新、下载中断、摘要不匹配、安装取消、安装失败和回退提示。

#### W-0703：安装、升级、修复和卸载测试

至少覆盖首次安装、覆盖升级、跨版本迁移、保留数据、彻底清理选择和降级阻止。

#### W-0704：签名和发布流程设计

只设计并验证从 CI secret 读取证书的路径。没有用户明确授权时，不上传、不发版。

### P8：稳定性、性能与功能审计

#### W-0801：功能矩阵逐项关闭

所有适用功能需要链接到测试、截图或实机记录。不适用功能需要用户可见的合理说明，
不能只是禁用按钮。

#### W-0802：长时间运行

运行服务 24 小时，执行核心热更新、网络切换、休眠唤醒和高频请求，检查进程、句柄、
内存、日志和端口泄漏。

#### W-0803：性能验收

检查冷启动、二次启动、5,000 条下载任务、50,000 条日志、表格筛选和窗口缩放。

#### W-0804：发布候选验收

在干净 Windows 10/11 x64 环境执行安装到卸载全流程，完成已知问题和发布说明。只有
用户明确授权后才进入实际发布动作。

## 11. 测试与质量门槛

### 11.1 每次共享层改动

Termux 或通用 CI：

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:checkNodeRuntimeScripts \
  :app:testNodeRuntimeParsing \
  :app:testBundledBrotliRuntime \
  :app:testBundledNodeLockClosure \
  :app:testBundledCoreRuntimeDependencies
./gradlew :app:assembleDebug
```

若环境需要指定与内嵌运行时一致的 Node：

```bash
./gradlew :app:verifyEmbeddedNodeCompatibility \
  -PtargetNodeExecutable=/absolute/path/to/node-v24.19.0
```

不要在文档或提交中硬编码个人 JDK、Node 或 Android SDK 绝对路径。

### 11.2 每次 Desktop 改动

Windows，模块建立后：

```powershell
.\gradlew.bat :desktop:test
.\gradlew.bat :desktop:run
```

合并前还需执行 Desktop 打包任务。若 P0 最终任务名不是 `:desktop:packageMsi`，必须
在本文和交接页同步记录真实命令。

### 11.3 Node 契约测试

同一测试集必须在 Termux Node 和打包的 Windows `node.exe` 上运行。至少覆盖：

- 配置解析和序列化。
- 健康检查和 runtime identity。
- 管理 token 拒绝路径。
- 核心加载、切换和失败恢复。
- 热更新与并发请求。
- 优雅关闭、超时终止和端口释放。
- 中文路径、空格路径和超长路径。

### 11.4 Windows 实机矩阵

| 维度 | 最低覆盖 |
|---|---|
| 系统 | Windows 10 x64、Windows 11 x64 |
| DPI | 100%、125%、150%、200% |
| 主题 | 浅色、深色、运行中切换 |
| 权限 | 普通用户；需要提升时必须有明确原因 |
| 网络 | 在线、离线、代理、端口占用、防火墙拒绝 |
| 路径 | ASCII、空格、中文、长路径、只读目录 |
| 生命周期 | 首次启动、快速重启、崩溃、休眠、唤醒、注销 |
| 数据 | 全新、Android 导入、旧 Windows 版本升级、损坏配置 |

## 12. CI 建议

建议建立三个互相独立的 job：

1. `android-regression`：运行现有 Android 单测、Node smoke 和 Debug 构建。
2. `desktop-windows`：在 Windows runner 执行 Desktop 单测、集成测试和测试打包。
3. `runtime-contract`：用目标 Node 版本在至少 Termux/Linux 等价环境和 Windows 执行
   同一套 Node 契约测试。

PR job 不发布资产。发布 workflow 必须手动触发、明确版本、校验工作树来源，并受用户
的发布授权约束。

## 13. 风险登记

| 编号 | 风险 | 预警信号 | 处理 |
|---|---|---|---|
| R-01 | Compose 与当前 Kotlin/Gradle 不兼容 | P0 最小模块无法稳定构建 | 在 P0 调整版本或切换 WinUI 3，不拖到 P3 |
| R-02 | 为共享而破坏 Android | 大规模移动文件、Android 回归频繁 | 小批迁移，每批跑 Android 测试和 APK |
| R-03 | Node 停止/重启竞态复制到 Windows | 残留进程、旧端口、状态闪烁 | 进程退出、端口关闭、identity 三重确认 |
| R-04 | UI 只是手机界面拉宽 | 大量单列卡片、鼠标键盘效率低 | P3 假数据评审先于批量页面实现 |
| R-05 | Windows 专属能力最后才测 | Termux 完成很多代码但没有可运行包 | P0 强制 Windows 构建、进程和安装包 Gate |
| R-06 | 更新器造成不可恢复状态 | 安装失败后无法启动旧版 | 原子替换、摘要校验、保留回退路径 |
| R-07 | 密钥或 Cookie 泄漏 | 日志/备份出现明文 | 凭证存储、脱敏测试、导出审计 |
| R-08 | 功能矩阵名义完成 | 禁用按钮、固定假数据、只有成功路径 | 每项要求真实后端和失败路径证据 |
| R-09 | 分支长期分叉 | Android 与 Windows 各自修同一核心 bug | Node 与 domain 保持单一事实来源 |
| R-10 | 交接上下文丢失 | 接手者重复审计或推翻已验证决策 | 每次工作结束更新交接页和决策记录 |

## 14. 预估投入

以下是一名熟悉 Kotlin/Node 的开发者全职人周估算，不是发布日期承诺：

| 阶段 | 估算 |
|---|---:|
| P0 可行性闭环 | 1-2 周 |
| P1-P2 共享层、Node 与宿主 | 3-5 周 |
| P3-P4 桌面体系和核心流程 | 3-5 周 |
| P5 工具、下载与迁移 | 2-4 周 |
| P6-P8 系统集成、发布和稳定性 | 3-5 周 |
| 合计 | 12-20 人周 |

多个阶段可以局部并行，但 P0、P2 Gate 和发布候选验收不能跳过。

## 15. 第一批实际执行顺序

当用户授权开始实现后，第一批只执行以下任务：

1. 更新 `docs/windows-desktop-handoff.md` 的基线 commit、工作树状态和执行人。
2. 在独立小改动中建立 Windows CI 构建探针，不上传任何资产。
3. 新增最小 `:desktop` 模块，显示一个窗口和构建信息。
4. 在 Windows 上启动固定版本 `node.exe`，运行现有 Node smoke。
5. 实现最小进程 supervisor，只显示真实 Stopped/Starting/Running/Failed 状态。
6. 生成内部测试安装包，在干净 Windows 用户环境验证。
7. 写 P0 决策记录，确认或否决 Compose Desktop 和独立 Node 子进程。

第一批明确不做完整首页、核心管理或配置迁移。P0 的目标是证明技术路线，不是制造一张
看起来完成但没有真实运行时的截图。

## 16. 完成定义

Windows 正式版只有同时满足以下条件才算完成：

- 功能矩阵中所有第一版适用项有真实实现和验收证据。
- Android 全量单测、Node smoke、Debug 构建通过。
- Desktop 单测、Node 契约测试、关键 UI 自动化通过。
- Windows 10/11 实机矩阵通过。
- 安装、升级、卸载、数据保留和失败恢复通过。
- 没有残留 `node.exe`、端口占用、明文凭证或无限增长日志。
- UI 在规定窗口尺寸和 DPI 下无重叠、截断或不可达操作。
- 交接页、已知问题、架构和用户文档更新完成。
- 发布动作获得用户单独明确授权。

## 17. 接手规则

任何开发者或 AI 开始任务前，必须按以下顺序阅读：

1. 本文。
2. `docs/windows-desktop-handoff.md`。
3. `docs/architecture.md`。
4. 当前任务涉及的 Android、Node 或 Desktop 源码。

开始前运行只读状态检查；结束前更新交接页。不得假设旧交接中的构建结果仍然有效，
不得清理或回退用户的无关工作树改动。

## 18. 待用户确认的产品决策

这些问题不阻塞文档和 P0 探针，但在对应阶段前必须确认：

1. Windows 最低版本是否确定为 Windows 10 22H2。
2. 第一版是否只发布 x64。
3. 关闭窗口默认退出，还是默认最小化到托盘。
4. 是否需要“作为 Windows Service 运行”，还是登录用户托盘后台进程即可。
5. 是否需要便携 ZIP，还是只提供安装版。
6. 是否需要接收或生成手机/TV 配置同步二维码。
7. Windows 版是否沿用 Android 产品名，还是使用“弹幕 API Desktop”。
