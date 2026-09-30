# frpc 内核（内网穿透）

App 只打包 **客户端** `frpc`，服务端使用用户自己的第三方 frps（或自建 frps）。
内核来自 [fatedier/frp](https://github.com/fatedier/frp) 官方 Release，Apache-2.0。

二进制不入库；版本、校验值、许可证和准备说明纳入仓库。clone 之后先准备内核：

```bash
scripts/prepare_frp_kernel.sh              # 按默认 ABI（arm64-v8a / armeabi-v7a / x86_64）准备
scripts/prepare_frp_kernel.sh arm64-v8a    # 只准备指定 ABI
```

`app/build.gradle.kts` 会在 release 构建时检查内核是否齐全，缺失直接构建失败；
Debug 构建只告警，但产物里的内网穿透不可用。

## 文件

```
runtime/frp/
├── arm64-v8a/libfrpc.so      frp_<ver>_linux_arm64 的 frpc
├── armeabi-v7a/libfrpc.so    frp_<ver>_linux_arm   的 frpc
├── x86_64/libfrpc.so         frp_<ver>_linux_amd64 的 frpc
└── SHA256SUMS
```

**为什么叫 `libfrpc.so`**：Android 10+ 禁止执行应用私有目录里的文件，
普通模式要把内核放进 `jniLibs`，由安装器解压到 `nativeLibraryDir` 后再用
绝对路径 exec。文件名必须是 `lib*.so` 才会被当作原生库打包；它本质仍是
可执行文件，不会被 `System.loadLibrary` 加载。

## 版本与兼容

- 当前版本：**0.71.0**，与
  `app/src/main/java/com/example/danmuapiapp/data/tunnel/TunnelLogic.kt`
  的 `FRPC_KERNEL_VERSION` 必须一致。
- frpc 与 frps 需要版本匹配（跨大版本可能握手失败），更换服务商或
  升级内核前先用日志确认兼容。

## 安卓上的 DNS

官方 Release 是 `CGO_ENABLED=0` 的纯静态二进制，可直接在 Android 上执行；
但 Android 没有 `/etc/resolv.conf`，Go 的纯静态解析器无法解析域名，
因此配置里需要 `dnsServer`：

- 表单模式：默认已写入 `dnsServer = "223.5.5.5"`；
- 粘贴模式：可打开「自动补 DNS」，保存前会在配置顶部补上。

若想彻底不依赖该字段，可以用 NDK 按 `GOOS=android` 重新编译（Go 会走
bionic 解析器）后替换本目录二进制，并同步更新版本号与 SHA256。

## 更新步骤

1. 从官方 Release 核验新版本的三个架构客户端，更新 `VERSION` 和 `SHA256SUMS`；
2. 同步更新 `TunnelLogic.kt` 的 `FRPC_KERNEL_VERSION`；
3. 运行 `scripts/prepare_frp_kernel.sh`，提交版本与校验值的变更。脚本不会自动信任新校验值。

## 许可

frp 采用 Apache-2.0。随 App 分发时请保留 frp 的 LICENSE / NOTICE 声明。

## 配置与回归检查

运行配置写入 `files/frp/frpc.conf`，由 frpc 根据内容识别 TOML / INI / JSON / YAML。
旧的 `frpc.toml` 会在首次使用时迁移；Root 启动或保存自动启动配置时会更新已安装的开机脚本，保留模块的启用状态。

```bash
./gradlew :app:testDebugUnitTest --tests '*Tunnel*Test' --tests '*Frpc*Test' --tests '*RootAutoStartServiceScriptTest'
./gradlew :app:verifyFrpcReleaseKernels
```

`FrpcConfigIntegrationTest` 使用本机架构的内核执行 `verify`，不连接服务器；未准备内核时该项测试会跳过。

## 在线更新与存储迁移

Root 在线更新先直连官方 GitHub API 读取对应发布资产的 SHA-256 与大小，再流式下载、核对摘要、完整解压并检查 ELF 架构；这些步骤通过后才允许执行版本检查。官方摘要不可用时停止更新，保留旧内核。普通模式仍使用 APK 内置内核。

配置存于设备保护存储的 `files/frp`，允许 Root 开机脚本在首次解锁前读取。升级时在解锁后迁移旧 CE 配置，仅补齐缺少的文件，保留已有 DE 设置和旧进程识别。迁移不会主动重启运行中的隧道；旧实例的日志输出位置会在下次重启隧道时切换。
