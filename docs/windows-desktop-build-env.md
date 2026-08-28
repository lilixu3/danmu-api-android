# Windows 桌面端构建环境说明（W-0001）

本文记录 `:desktop` 模块在 Windows 上完全可复现的本机构建命令与固定版本。
目标：任何一台 Windows 10/11 x64 机器按此文档可独立完成构建与测试，
不依赖开发者私有绝对路径，不读取签名密钥，不上传任何资产。

## 固定版本

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | 21（Temurin 21.0.12.1+1 验证通过） | 需 `JAVA_HOME` 指向 JDK 17+，推荐 21（jpackage 依赖） |
| Gradle | 9.6.0 | 与 `gradle/wrapper/gradle-wrapper.properties` 一致；Kotlin 2.4.10 官方矩阵到 9.5，9.6 实测可用（KGP 走 gradle813 变体） |
| Kotlin | 2.4.10 | 与 `:app` 一致 |
| Compose Multiplatform | 1.12.0 | Desktop 插件 `org.jetbrains.compose`；material3 显式坐标 `1.12.0-alpha03`（勿用已弃用的 `compose.material3` 访问器，其解析到旧版） |
| Node.js | 24.19.0（win-x64） | 与 Android 内嵌运行时同版本；通过 `-PdanmuNodeExe` 或环境变量 `DANMU_DESKTOP_NODE_EXE` 提供 |
| Android SDK | 与 `:app` 相同 | 配置期需要（`ANDROID_HOME`），即使只构建 `:desktop` |
| WiX Toolset | 3.11.2 | 无需手动安装：jpackage 首次打包 EXE/MSI 时自动下载 |

## 平台支持决策（D-006 变更，2026-08-28）

- 交付架构：**Windows x64（64 位）**，双形态：免安装版（`packagePortableZip`，解压即用）+ 直装版（jpackage EXE 安装器）。
- **不支持 32 位（x86）**：Node.js 官方自 v19 起不再发布 win-x86 二进制，内嵌 Node 24 运行时无 32 位 Windows 可用（用户已确认按 Node 官方支持平台交付）。
- ARM64（win-arm64）Node 与 Skiko 均有官方产物，留待后续加入构建矩阵。

## 常用命令（仓库根目录）

```bat
:: 桌面单测（含 Windows Node 子进程集成测试；无 node.exe 时相关用例自动跳过）
gradlew.bat :desktop:test -PdanmuNodeExe=C:\path\to\node.exe

:: 20 次连续启动/停止长冒烟（W-0003 验收）
gradlew.bat :desktop:test -PdanmuNodeExe=C:\path\to\node.exe -PdesktopLongSmoke=true

:: 免安装包（packagePortableZip，解压即用）与直装安装包（EXE，jpackage 自动下载 WiX）
gradlew.bat :desktop:packagePortableZip -PdanmuNodeExe=C:\path\to\node.exe
gradlew.bat :desktop:packageExe         -PdanmuNodeExe=C:\path\to\node.exe

:: 应用镜像目录（binaries\main\app\DanmuApiDesktop.exe 可直接运行）
gradlew.bat :desktop:createDistributable -PdanmuNodeExe=C:\path\to\node.exe

:: 开发运行窗口
gradlew.bat :desktop:run

:: Android 回归基线（桌面改动不破坏 Android 的证据）
gradlew.bat :app:testDebugUnitTest
gradlew.bat :app:checkNodeRuntimeScripts :app:testNodeRuntimeParsing :app:testBundledBrotliRuntime :app:testBundledNodeLockClosure :app:testBundledCoreRuntimeDependencies
```

产物位置：`desktop\build\compose\binaries\main\`（`exe\` 安装器、`zip\` 免安装包、`app\` 应用镜像）。

## CI 说明

仓库 `.gitignore` 有意排除 `/.github/`，当前不提交 workflow 文件。
W-0001 的"只构建不发布探针"以上述本机命令为等价载体；如后续需要
`windows-latest` CI job，须先单独决策是否解除该排除，且 CI 不读取签名
密钥、不上传 Release、不推送源码。

## 已知限制

- Kotlin 2.4.10 官方支持 Gradle 至 9.5；9.6 属矩阵外一格，实测可用，升级 Gradle 时需回归。
- 打包产物未签名（P0 使用内部测试包）；代码签名属 W-0704，需单独授权。
- 随包运行资源（node.exe + nodejs-project）以内嵌 classpath 资源进应用 jar，
  首次启动由宿主解压到可写数据目录；不要使用 compose 1.12 的 `appResourcesRootDir`
  （实测不落地）。
- `suggestRuntimeModules` 在新增依赖（网络、SQL、XML 等）后需要复跑并同步 `modules(...)` 列表。
