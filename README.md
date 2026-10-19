<div align="center">

# 弹幕 API Android

在手机上运行弹幕 API 服务，局域网内任意设备均可访问

<br>

[📦 下载最新版本](https://github.com/lilixu3/danmu-api-android/releases) · [🐛 反馈问题](../../issues) · [💬 讨论](../../discussions)

</div>

---

## 这是什么

一个 Android 应用，让你的手机变成弹幕 API 服务器。安装后在本机启动服务，同一局域网内的电视、电脑、平板都可以直接调用，不需要额外的服务器或电脑常开。

---

## 功能特性

### 🚀 一键启动服务
- 打开 App 点击启动，服务即刻运行
- 支持开机自启，手机重启后自动恢复

### 📡 局域网访问
- 服务启动后，首页会显示本机地址和局域网地址
- 复制地址给播放器直接使用，无需配置

### 🔧 核心管理
- 内置稳定版和开发版两套核心
- 支持一键切换、更新、回退
- 支持安装自定义核心

### ⚙️ 可视化配置
- 无需手动编辑配置文件
- App 内直接修改各项参数
- 改完自动生效无需重启

### 网络线路
- 来源默认线路按核心能力显示：已接入核心代理的来源显示「沿用核心配置」，其他弹幕源显示「使用设备当前网络」；也可选择增强直连或代理节点。明确选择的 App 节点优先级最高，旧版普通直连迁移为默认线路，已有节点绑定和增强直连选择保留。
- 原生支持 HTTP、HTTPS、SOCKS5 节点及用户名/密码，可手动添加或粘贴节点链接。没有内置代理内核和订阅功能。
- 可以连接其他代理客户端提供的本机 HTTP/SOCKS5 端口；节点无效或连接失败不回退直连。
- 增强直连继续使用 Node OpenSSL 4 ECH/H2/H3；GitHub 下载由 Android 网络层独立处理。
- 网络页检查实际运行组件版本，Root 服务沿用旧脚本时明确提示停止后重新启动。
- 使用与验证见 [代理与来源探测](docs/proxy-and-source-probe.md)。

### 🛠️ 内置工具
- **接口调试**：直接在 App 内测试 API 是否正常
- **弹幕推送**：手动推送弹幕到指定视频
- **请求记录**：查看所有经过服务的请求
- **实时日志**：查看服务运行日志
- **设备控制**：设备黑名单管理
- **弹幕下载**：支持下载弹幕到本地
- **本地弹幕**：导入、维护和预览本地弹幕文件；文件名自动解析剧名/年份/季/集，支持批量导入、
  目录直读（需系统「所有文件访问」权限）、预览以及删除单集/整季/整部
- **缓存管理**：查看和管理弹幕缓存

### 💾 备份与恢复
- 支持将配置导出备份
- 通过 WebDAV 自动同步到网盘
- 换机迁移方便快捷

### 🌐 网络优化
- 内置 GitHub 线路测速
- 自动选择最快的下载节点
- 国内网络也能流畅更新核心

### 📱 设备兼容
- 支持 Android 7.0 及以上设备（minSdk 24，Node 26 运行时要求）
- TV/盒子自动进入兼容模式
- 支持扫码同步配置

---

## 快速开始

### 安装

1. 前往 [Releases](https://github.com/lilixu3/danmu-api-android/releases/latest) 页面下载最新版 APK
2. 选择适合你设备架构的 APK 文件：
   - `arm64-v8a`：绝大多数 2017 年后的手机（优先选择）
   - `armeabi-v7a`：较老的 32 位 ARM 设备
   - `x86_64`：x86 架构设备（少数模拟器或特殊机型）
3. 安装 APK 文件（首次安装需要允许「安装未知来源应用」）

### 启动服务

1. 打开应用
2. 点击首页的「启动服务」按钮
3. 服务启动后，首页会显示服务地址
4. 将地址复制到播放器中使用

### 基本配置

1. **核心管理**：在「设置」页面可以切换稳定版/开发版核心
2. **服务配置**：在「配置」页面可以修改服务参数
3. **网络设置**：为各弹幕来源选择默认线路、增强直连或代理节点；GitHub 下载线路、镜像和核心检查间隔在「下载与更新」独立管理

---

## 技术架构

### 项目结构

- **Android 应用**：提供用户界面和系统集成
- **Node.js 服务**：弹幕 API 核心服务
- **公告中心**：服务端公告管理

### 技术栈

- **前端**：Kotlin、Jetpack Compose
- **后端**：Node.js
- **依赖注入**：Hilt
- **异步编程**：Coroutines
- **网络请求**：OkHttp
- **本地存储**：SQLite
- **容器化**：Docker (公告中心)

### 架构特点

- **分层架构**：domain 层、data 层、ui 层清晰分离
- **模块化设计**：按功能拆分，便于维护和扩展
- **依赖注入**：使用 Hilt 实现依赖注入，提高代码可测试性
- **响应式编程**：使用 Coroutines 和 Flow 实现异步操作

---

## 常见问题

### 安装时提示"未知来源"怎么办？
APK 不是从应用商店安装，需要手动允许。在弹出的提示中点击「设置」，开启「允许安装未知来源应用」，然后返回重新安装即可。

### 启动服务后局域网其他设备访问不了？
1. 确认手机和目标设备连接的是同一个 Wi-Fi
2. 检查手机防火墙或安全软件是否拦截了端口（默认 9321）
3. 部分路由器开启了「AP 隔离」，会阻止同一 Wi-Fi 下设备互访，在路由器设置中关闭即可

### 服务运行一段时间后自动停了？
这是 Android 系统的电池优化导致的。解决方法：
1. 进入手机「设置 → 电池 → 电池优化」，找到本 App，选择「不限制」
2. App 内「设置 → 服务配置」中开启无障碍保活（可选，但会增加耗电）

### 提示需要 Root 权限，不 Root 能用吗？
完全可以。Root 模式是可选的增强功能，普通模式下所有核心功能均正常使用。

### 核心安装失败或更新失败？
1. 检查网络连接是否正常
2. 进入「设置 → 网络」，尝试切换 GitHub 下载节点，或配置 GitHub Token 提升下载限额

### App 申请了无障碍权限，会不会读取我的隐私？
不会。无障碍服务仅用于在服务意外停止时自动拉起，已明确关闭窗口内容读取（`canRetrieveWindowContent=false`），不会读取任何屏幕内容或其他应用数据。

---

## 开发与贡献

### 开发环境要求
- Android Studio Arctic Fox 或更高版本
- JDK 17 或更高版本
- Node.js 26.12.0 lite + OpenSSL 4.0.3（正式 APK 的运行时兼容测试必须与内嵌 Node 同 major）
- 其它 Node.js 版本可用于公告中心开发和普通 Debug 构建

### 构建项目

```bash
# 克隆仓库
git clone https://github.com/lilixu3/danmu-api-android

# 进入项目目录
cd danmu-api-android

# 准备 frpc 内核（默认三个 ABI，校验仓库记录的 SHA-256）
./scripts/prepare_frp_kernel.sh

# 构建发布版本；也可通过 DANMU_TARGET_NODE 指定同版本 Node
./gradlew assembleRelease -PtargetNodeExecutable=/path/to/node-v26.12.0
```

Node 运行时使用 **26.12.0 lite + OpenSSL 4.0.3（原生 ECH/H3）**，锁定
`fogtape/nodejs-mobile` 的 `v26.12.0-0` 预发布版（源码快照 `764f001a`，
配方提交 `333a9867`）。运行时来自该固定 Release 资产，
App 发行附件仅提供三个架构的 APK；运行时 ZIP 不随 App 发行上传。
来源及 SHA-256 见
`app/native-runtime-sources.properties`、`app/native-runtime.sha256` 和
`app/native-runtime-provenance.json`。JNI 桥使用同份 ZIP 的头文件与 NDK r29
重新编译，libc++、libc++abi、libunwind 由 `scripts/build-native-deps.py` 从固定源码重建。

离线构建可将校验通过的 `nodejs-mobile-android-lite-26.12.0-0.zip` 放入
`dist/nodejs-mobile/v26.12.0-0-lite/`。缺少本地或 Gradle 缓存时，从固定上游 Release 公开地址下载，无需登录 GitHub CLI；
C++ 依赖源码缓存也必须预先准备并通过指纹校验。构建不提取旧 APK 的原生库。

构建机也可以显式指定已经准备好的运行时 ZIP，或指向同一 ZIP 的固定 HTTPS 下载地址，这两种方式无需登录 GitHub CLI。
这两个入口都继续校验来源清单中的归档大小和 SHA-256，不更换运行时版本或接受其他内容：

```bash
# 只校验 ZIP，无需 NDK 编译；也可把这个参数用于 assembleDebug / assembleRelease
./gradlew :app:verifyNativeRuntimeArchive --offline -PnativeRuntimeArchive=/path/to/nodejs-mobile-android-lite-26.12.0-0.zip

# 从自建镜像或正式资产地址下载同一 ZIP，无需 gh 登录
./gradlew :app:verifyNativeRuntimeArchive -PnativeRuntimeArchiveUrl=https://your-host.example/nodejs-mobile-android-lite-26.12.0-0.zip
```

显式指定的本地文件校验失败会直接报错，不使用缓存代替。公开地址只覆盖下载位置，不能覆盖固定摘要。
当前来源清单与 provenance 记录均指向固定上游 Release 资产，长期或离线构建应保留校验通过的本地 ZIP。

手机测试包可用 `-PabiFilters=arm64-v8a` 构建。产出后运行
`python3 scripts/verify-apk-native.py app/build/outputs/apk/release/app-arm64-v8a-release.apk`，
检查全部原生库的 LOAD 对齐、RELRO 页保护范围、ZIP 对齐及固定 libnode 摘要。
压缩存储的库在安装时解压；未压缩的库必须在 APK 中按 16 KB 对齐。

运行时依赖包以 Node 26 为目标。迁移期间，仅额外接受已经在实际 Node 26 上验证过的
纯 JS Node 24 包 `4be12e7971f079cd357496974113342f6839919a247e24b4fee69a594301c8a7`；
签名、归档摘要和禁止原生模块的校验仍须通过，其他 Node 24 包不在兼容范围内。

正式构建只读取仓库内的 Node 运行时资产，不会隐式读取相邻的 `danmu_api`
仓库或根目录 `node_modules.zip`。维护依赖资产时，运行 `python3 scripts/rebuild-node-dependencies.py`，从
`runtime/node-dependencies/package-lock.json` 在新目录准备源码并打包，再检查变更。

### 代码风格
- 遵循 Kotlin 官方代码风格
- 遵循 Android 官方编码规范
- 使用 Hilt 进行依赖注入
- 使用 Jetpack Compose 构建 UI

### 贡献流程
1. Fork 项目仓库
2. 创建功能分支
3. 提交代码
4. 推送分支
5. 创建 Pull Request

---

## 许可证

本项目基于 [MIT License](LICENSE) 开源。

---

## 联系方式

- **GitHub 仓库**：[https://github.com/lilixu3/danmu-api-android](https://github.com/lilixu3/danmu-api-android)
- **Issues**：[https://github.com/lilixu3/danmu-api-android/issues](https://github.com/lilixu3/danmu-api-android/issues)
- **Discussions**：[https://github.com/lilixu3/danmu-api-android/discussions](https://github.com/lilixu3/danmu-api-android/discussions)

---

<div align="center">

**如果你觉得这个项目对你有帮助，欢迎给个 ⭐️ 支持一下！**

</div>
