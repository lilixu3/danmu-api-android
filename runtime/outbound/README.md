# App 独立增强直连

此目录维护 App 自带的网络组件，不依赖核心是否实现 OUTBOUND 配置。
源码基于 `UPSTREAM` 记录的提交，保留 AGPL-3.0 许可证；Go 模块依赖由
`src/go.mod` / `src/go.sum` 固定。各 ABI 的产物从这里的源码构建，不加入 Git。

## 用户使用

在「设置 → 网络设置 → 增强直连」打开总开关。默认处理巴哈姆特与 TMDB，
不改变核心搜索来源和顺序，已有匹配代理/反代优先。

- 巴哈姆特必须协商 ECH；auto 可从 ECH+H3 回退到 ECH+H2，不回退普通 SNI。
- TMDB 使用增强解析和 H2/H3，不强制 ECH，核心的 TMDB_API_KEY 保持原用法。
- h3 为强制诊断模式，失败不回退。DoH 留空使用组件内置解析路径。
- 状态「已就绪」仅代表组件启动完成；「连通性诊断」才会访问源站。
- TMDB 诊断不发送 API Key，HTTP 401 只验证解析、证书与 HTTP 响应。
- 网络仍受设备 VPN、UDP 可达性及源站地域限制影响，不修改系统代理。

配置存于 App 的设备保护目录（DE）`files/outbound/settings.json`，与核心 `.env` 和 envs.js
无关。核心更新不会覆盖它。普通模式、Root 手动启动和 Root 开机自启均传入
同一配置路径；配置可在首次解锁前由 Root 自启读取。原生组件路径每次从当前 APK 安装目录解析。

## 构建

需要 Go 1.26+ 和 Android NDK（推荐 r28+），从项目根目录运行：

```sh
scripts/prepare_outbound_kernel.sh
# 或只准备正在构建的 ABI
scripts/prepare_outbound_kernel.sh arm64-v8a
./gradlew :app:verifyOutboundReleaseKernels
```

脚本默认读取 ANDROID_NDK_HOME / ANDROID_NDK_ROOT / local.properties 中的 ndk.dir。
生成 Android API 24+、CGO/PIE 可执行 ELF，64 位支持 16 KB 页对齐，链接 liblog。
`libdanmu_outbound.so` 是为了 JNI 打包命名的可执行程序，不通过 System.loadLibrary
加载。Gradle 仅把二进制复制至 `app/build/prepared-outbound-jni`，不把源码或许可证
目录当作 JNI 内容；Release 缺少任意选定 ABI 时构建失败。APK 同时包含许可证。

## 接入与兼容范围

Node 来源宿主只启动一个组件，经随机回环端口和每次启动的随机认证 token 通信；
GitHub 使用下文的独立按需组件。
接入层早于核心 import 安装：原生 fetch 与 http/https.request/get（包括内置
node-fetch）按精确目标域名选择路由。Worker 接收同一组件状态，模块缓存与
请求包装在各自线程安装。直接导入兜底路径同样支持。

支持 HTTPS/443 的精确域名：
- 巴哈姆特：api.gamer.com.tw，强制 ECH。
- TMDB：api.tmdb.org、api.themoviedb.org，不强制 ECH。
- 弹弹play：api.danmaku.weeblify.app 强制使用自身配置的 ECH；nipaplay.aimes-soft.com 使用增强解析，不强制 ECH。原 HTTP/IP 备用线路保留。
- Animeko：danmaku-global.myani.org、api.bangumi.vip 强制使用自身配置的 ECH；api.animeko.org、danmaku-cn.myani.org、s1.animeko.openani.org 使用增强解析，不强制 ECH。

api.bgm.tv 本轮未通过直连验证，保留原请求方式。来源的节点切换和健康缓存继续由核心管理。
默认来源仍为 bahamut、tmdb，既有配置保持；在 App 设置中勾选弹弹play或 Animeko 后启用新增来源。
业务域名和证书验证保持；DNS/ECH 按 TTL 更新，不硬编码业务 IP 或密钥。
显式代理 Agent、dispatcher、自定义 TLS 选项尊重调用方；原始 socket、独立
HTTP/2 客户端等不在首版接入范围。自定义核心需使用支持的请求接口。
App 接管时给核心传递的 OUTBOUND_MODE 为 off，避免未来核心重复接管；不修改 .env。

取消通过本地连接断开传递到 Go 上游 context。fetch 的请求体最多 32 MiB，
重定向重新按域名选路，跨域移除认证头。auto 仅竞争握手，不重复发送业务请求。
适配层请求上限 30 秒；核心传入的 AbortSignal 可更早取消。组件请求内部共享
DNS、连接和响应读取截止时间。配置变更、服务停止和父进程 stdin EOF 均清理组件。

App 提供 `/__outbound` 状态与 POST `?source=bahamut|tmdb|dandan|animeko` 诊断接口：本机或
管理凭据校验后使用，不返回本地组件 endpoint/token。

## 验证

```sh
node --test node-tests/app-outbound-bridge.test.cjs node-tests/app-outbound-runtime.test.cjs node-tests/app-outbound-host.test.cjs
(cd runtime/outbound/src && go test ./... && go vet ./...)
./gradlew :app:testDebugUnitTest --tests '*AppOutboundSettingsTest' --tests '*RootAutoStartServiceScriptTest'
./gradlew :app:assembleDebug
```

网络测试默认关闭，离线测试不需要调整系统 VPN。正式分发前还应在实际安装的
APK 中验证 nodejs-mobile spawn、后台停止/恢复、Root 自启和源站连通性；Termux
执行生成的 Android ELF 不等同于普通 App 的 SELinux/进程限制验证。

## 本次本地验收（2026-09-30）

在本地 main 的未提交改动基础上实现，未改写核心仓库。Node 离线覆盖原生 fetch、node-fetch、Cookie、压缩、重定向、上传/响应取消、旧核心 Worker/直接导入与核心切换、组件异常恢复。Go 离线测试覆盖解析/ECH 更新、协议回退和回环适配，实际生成的 Android arm64 ELF 完成启动/重配置/停止测试。Kotlin 配置与 Root 自启脚本测试通过，三个 ABI 产物通过打包与 ELF 检查。没有安装或重启当前手机上的正式 App，也没有把本轮离线结果作为无代理源站连通结论。

## 空闲开销与默认值

默认关闭。开启并运行服务后会多一个 Go 进程，不能把它等同于零耗电。
组件不定时查询业务 DNS/ECH，仅在请求需要时按 TTL 读取或更新。
源站 H2 的空闲 PING、H3 保活和源站 TCP keepalive 已关闭；H2/H3 在
空闲约一分钟后释放连接，后续请求重新建立连接，连续请求仍可复用。
本地状态文件每 30 秒更新一次，仅开启时创建定时器，不向 Worker 重发配置。
状态或配置改变仍立即通知界面与 Worker。界面使用 90 秒的状态过期阈值。
这些措施减少额外唤醒和网络活动，但具体耗电需要安装 APK 后对比测量；
本项目目前没有可据此宣称“零耗电”或默认开启的真机数据。

## 来源扩展（2026-09-30）

新增弹弹play、Animeko 的精确域名策略，同步 App-owned Go 组件与 JS 接入层，旧核心也可使用。
弹窗按用户勾选来源并行诊断；Animeko 诊断在一个总预算内切换节点。取消诊断会传递到上游请求。
原有来源配置不自动迁移或扩大，默认开关仍关闭。ECH 域名缺配置或协商失败不会降级普通 SNI，
仅巴哈姆特可用共享配置；其他域名不借用共享公钥。并发 DoH 冷启动查询合并，减少重复请求。
普通 H2 保留 TLS 1.2 兼容，ECH/H3 最低 TLS 1.3；移动端关闭源站周期保活的优化保持。


## GitHub 网络接入（2026-09-30）

在“网络设置 → GitHub 网络 → 测速并选择线路”中选择“GitHub 官方（增强直连）”。
既有选择和默认值不自动改变，GitHub 线路独立于弹幕来源增强开关，也不需要启动或安装核心。
普通模式、Root 模式和兼容模式的公共 OkHttp 客户端均使用同一 App 侧接入；旧核心同样可用。

GitHub 当前策略为可靠 DoH 解析 + 普通 TLS/H2（必要时由 OkHttp 协商 HTTP/1.1），
不借用巴哈姆特 ECH，不强制 H3。Go 组件增加 `--github-proxy` 模式，在随机本机端口
提供经随机 Bearer token 认证的 CONNECT 隧道；TLS、原域名证书验证、HTTP 协议协商、
Cookie、跳转和跨域认证头处理继续由 OkHttp 完成。精确限制 github.com、api.github.com、
raw.githubusercontent.com、codeload.github.com、release-assets.githubusercontent.com、
objects.githubusercontent.com、github-releases.githubusercontent.com 的 443 端口，DNS 排除私有地址。
不接受任意子域，也不接管第三方代理站。IPv4/IPv6 仅竞争连接，不重复业务请求。

GitHub helper 由 Android App 按需启动，独立于 Node 的来源 helper；同一 App 进程复用一个实例。
活动隧道不会被空闲回收打断，隧道无流量约 65 秒关闭，此后组件空闲约 60 秒退出；
父进程 stdin EOF 清理。没有定时业务 DNS 查询或网络保活。组件读取现有 DoH 和连接超时配置，
这些配置在下次组件启动时生效；来源 httpVersion 设置不改变 GitHub 的普通 TLS 策略。
不在状态文件或日志暴露端口 token、GitHub Token 或签名下载地址。

覆盖 App Release 检查与 HTML 回退、APK 下载、核心 ZIP 安装/更新、运行时依赖清单/签名/下载、
PR 元数据、官方 API Token 校验、FRP 更新检查和下载，以及 JGit PR 克隆与 fetch。
JGit 使用每个 TransportHttp 的 HttpConnectionFactory，不修改全局工厂；上传体临时文件传输，
下载 pack 流式读取，取消或结束清理请求。FRP 沿用现有解压和安装事务，其压缩包仍按原逻辑读入内存。
外部浏览器打开的网页由浏览器自行联网，不属于 App 请求接入范围。

“GitHub 网络 → 连通性测试”并行检测更新接口、raw 配置文件和最新 APK 的少量数据。
下载项会取得实际 release asset 并经过跳转，确认 APK 头；只取样，不是整包下载速度或完整性测试。
线路列表测速仍明确使用 raw 文件，增强和普通直连分别强制使用自己的路径。
取消 APK、核心 ZIP、依赖包下载会取消正在等待或读取的 OkHttp 调用，保留原有进度与校验。

新增离线测试覆盖 CONNECT 认证/精确域名限制、33 MiB 流、认证不泄露、取消 DNS、空闲回收，
以及真实本地 Git Smart HTTP 的 clone/fetch、POST/header、重定向控制、取消和证书验证不可关闭。

## 升级与安全约束

- Go 构建脚本为每个 ABI 记录源码/依赖/脚本指纹和二进制摘要。源码或构建参数发生变化后需重新准备组件；Release 校验会拒绝过期产物。
- GitHub 请求固定线路和配置快照。切换线路或 DoH/超时时，新请求使用新连接身份，旧下载继续完成；旧组件在连接结束后退出。
- 内置 DoH 在首选路径变慢时启动有界竞争，GitHub DNS 只占 CONNECT 预算的部分时间。显式自定义 DoH 不擅自切换服务。
- FRP 配置迁到设备保护目录，保留旧 CE 配置和进程识别兼容；已安装 Root 模块在升级、主进程启动或保存增强配置时后台刷新，保持启用状态。
- Root FRP 的 stdout/stderr 经组件的本地日志模式限额写入；该模式不联网、不定时轮询，在 frpc 关闭输出后退出。普通模式同样限制日志大小。
- FRP 在线更新的 SHA-256 和大小必须来自 GitHub 官方 HTTPS API，反代只提供压缩包数据。摘要缺失、校验失败、归档损坏或架构不匹配时拒绝执行；先验证，再保留原有安装事务与回滚。
