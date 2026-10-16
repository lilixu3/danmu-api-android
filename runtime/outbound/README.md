# 增强直连：Node 与 Android 原生实现

当前实现不再构建、启动或打包 `libdanmu_outbound.so`。此目录的 Go 源码、许可证
和旧构建记录仅保留作来源归属与迁移参考。`scripts/prepare_outbound_kernel.sh`
已退出构建流程；frpc 本身仍是独立穿透内核，不属于本次移除的网络组件。

弹幕请求由 Node 进程内的 `app-outbound-native.js` 和 `app-outbound-dns.js`
承接，复用既有 `fetch`、`https.request`、node-fetch 与 Worker 接入。回环适配器
仍使用随机端口和随机 token，但服务端位于宿主 Node 进程内，不再启动子进程。

- 需要本仓库对应的 Node 26.11.1 / OpenSSL 4 运行时，同时具备
  `tls.ECH_SUPPORTED` 和 `node:quic`；JNI/Root 启动参数启用 `--experimental-quic`。
  仅 OpenSSL 版本升级、Node 24 的 H3 能力，都不能满足 ECH 要求。
- 巴哈姆特、弹弹play 与 Animeko 的既有精确域名策略保留。要求 ECH 的域名在
  配置缺失或握手失败时关闭请求，不回退普通 SNI；只有巴哈姆特允许既有共享
  Cloudflare ECH 密钥，其他域名只使用自己的 HTTPS 记录和别名链中的 ECH 密钥。
- `auto` 只竞争 H3/H2 握手，胜者确定后发送一次业务请求。强制 `h3` 失败不
  回退。业务请求已开始后不跨协议重放，POST/PUT 等同样如此。
- DNS、ECH、ALPN 来自 DoH，并按 TTL 缓存；并发 DNS/连接建立合并。独立请求
  保留独立取消信号。DNS 私网、保留地址、无效问题、压缩循环、未知 mandatory
  SvcParam 被拒绝。源站 IP 和 ECH 密钥不硬编码。
- DoH 引导同时尝试 IPv4/IPv6；Cloudflare 的共享 ECH 记录中发布的公共前端地址
  也参与加密 DoH 握手。连接保持原 DoH 主机名证书验证，并要求 ECH 被接受，
  避免内置 IPv4 地址在当前网络不可达时拖死所有来源。前端地址按 DNS TTL 更新。
  对 ECH 公共名称同为 `cloudflare-ech.com` 的来源，也可竞争这些前端地址；
  来源仍使用自己的 ECHConfigList，并验证来源域名证书，不会借用其他来源的密钥。
- A/AAAA 中已有一个地址族可用时，只为另一个地址族保留短暂等待窗口，
  避免单个地址族的 DNS 黑洞阻塞已有的可用地址。
- 配置的连接超时分别约束 DNS 查询和 TLS/QUIC 握手，完整请求仍受总预算约束。
  DoH ECH 密钥被拒绝时仅刷新一次；失败时不会降低源站的 ECH 验证要求。
- 保留原域名证书验证、Cookie、压缩字节、跨域去认证、重定向和响应流取消。
  单次来源请求总预算 60 秒，调用方 AbortSignal 可以更早取消。
- 连接空闲约 60 秒关闭，不发送 H2 PING 或 QUIC keepalive；配置变更和停止服务
  关闭旧传输。活动响应不会因新连接替换旧连接而中断。
- 开关与选中来源维持原设置，状态文件与 `/__outbound` 不公开端口和 token。

豆瓣增强直连接入核心使用的 `m.douban.com` 与 `api.douban.com`，保留请求方法、
请求体、Referer 和 Cookie。当前接口采用 DoH + 验证原域名证书的 H2，不强制
没有发布的 ECH/H3。连通性测试访问匿名公开详情并验证条目 ID 与标题，Cookie
不是必填项；若用户已配置，则照常传递。搜索接口可能单独拒绝匿名请求，不能据此
判定整个豆瓣连接不通。公开详情被拒绝、限流或返回验证码时仍判失败，不能仅凭
TLS 握手成功显示通过。新来源默认不勾选，已有选择保持不变。

爱壹帆暂不加入增强直连列表。2026-10-15 在用户关闭代理的网络上测试
`api.tripdata.app` 和 `rankv21.tripdata.app`，普通 TLS 和已成功接受 ECH 的连接
均返回 Cloudflare 1009 / HTTP 403。该错误是站点按访问者出口 IP 的国家/地区
拒绝访问；DNS/ECH/H3 不改变出口 IP，不能解决这项限制。
参见 [Cloudflare 1009 说明](https://developers.cloudflare.com/support/troubleshooting/http-status-codes/cloudflare-1xxx-errors/error-1009/)。

GitHub 增强直连由 Android `GithubDohDns.kt` 配合原 OkHttp 客户端完成，不依赖
核心、Node 服务或 Root。精确 GitHub 官方域名用 DoH 解析，原域名 TLS 校验、
H2/HTTP1、跳转、流式下载与 JGit 保留。配置快照决定连接池身份；旧下载保留旧
配置，新请求读取新配置。取消会传入正在执行的 DoH 请求。GitHub 路线仍不强制
ECH/H3，也不借用弹幕来源的 ECH 配置。

Root FRP 的 FIFO 日志改用 APK 内 `RootFrpcLogEntry`（app_process 入口），继续
限额保存 1 MiB / 保留尾部 256 KiB、解析同一次启动的连接状态并在 EOF 后退出。
不再为了写日志保留 Go 网络程序。常规 FRP 服务不受此变更影响。

验证命令：

```sh
node --test node-tests/*.test.cjs
./gradlew :app:testDebugUnitTest --tests '*Github*' --tests '*AppOutboundSettingsTest' --tests '*RootTunnel*' --tests '*RootAutoStartServiceScriptTest'
./gradlew :app:assembleDebug
```

关闭代理后，使用对应的 Node 26 CLI 验证真实来源；并发模式同时测试五个来源：

```sh
node --experimental-quic scripts/smoke-native-outbound.cjs auto
DANMU_OUTBOUND_PARALLEL=1 node --experimental-quic scripts/smoke-native-outbound.cjs auto
```

上述脚本默认与 App 一样使用 3000 ms 连接预算，可通过
`DANMU_OUTBOUND_CONNECT_TIMEOUT_MS` 明确覆盖。脚本本身不切换 VPN、系统代理或路由器代理，
需在测试前确认这些出口处于预期状态。HTTP 成功和 ECH 接受结果应以当前测试输出为准。

Node 配方的测试/构建进度见 `docs/OPENSSL-4.md`。离线传输替身的通过结果不能
作为实际 ECH 接受、H3 网络可达性或已安装 APK 运行结果。新运行时资产必须在
真实构建与握手验证后再更新摘要及来源清单。
