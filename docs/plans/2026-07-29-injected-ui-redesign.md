# 注入弹窗 UI 重写方案

目标是把 LSPosed 注入到宿主播放器里的整套 UI 完全重写（不是在旧代码上打补丁），自成一套设计语言，暗色/亮色都支持，遥控器焦点作为一等公民。App 自身的 Compose UI 完全不动。

## 背景

注入到宿主播放器（影视壳 / OK影视 / FongMi）里的 UI 是手写 Android View（宿主进程内无法用 Compose）。现状问题：

- 设置子页 `DanmuXposedSettingsOverlay` 走「贴到宿主 content 上、克隆宿主行背景、hook 宿主返回键方法 `p0()`/`q()`、再用 80ms 轮询守卫自动关闭」这套脆弱路线，宿主一改版就废。
- 为配合上面这套，还有 `DanmuXposedHostBackgrounds` + `HostBackgroundColorPolicy` 去截屏裁剪宿主壁纸。
- 宿主设置页里被注入了一行「APP弹幕」入口（`DanmuXposedSettingsRowInjector`）。
- 主弹窗 `DanmuXposedManualSearchDialog` 是竖向单列分步流程，横屏（TV/平板的主要场景）浪费大量空间；遥控器焦点只靠 `interactiveRoundRect` 的 `state_focused` 兜一下，没有明确的初始焦点和方向键遍历。

## Scope

- In: `xposed` 包内所有 UI 层代码（token / 组件 / 弹窗 / 设置 / 推送记录）、相关源码策略测试。
- Out: bridge / push / episode repository / settings store 等业务逻辑；弹幕核心协议；App 自身 Compose UI；`DanmuXposedPlaybackControls`（控制条按钮继续克隆宿主控制项样式）。

## 删除

| 文件 | 原因 |
|---|---|
| `xposed/DanmuXposedSettingsOverlay.java` | 设置改为注入弹窗内的入口，不再融入宿主页面 |
| `xposed/DanmuXposedSettingsRowInjector.java` | 宿主设置页那一行整行去掉 |
| `xposed/DanmuXposedHostBackgrounds.java` | 只被 overlay 使用 |
| `xposed/HostBackgroundColorPolicy.java` | 只被 `DanmuXposedHostBackgrounds` 使用 |

连带清理 `DanmuXposedModule.java`：`settingsOverlay` 字段与其 Host 实现、`installBackInterceptor` / `findHostBackHandlerMethod`、`settingsRowInjector`、`showInjectionSettingsDialog`、`injectSettingsRow`。`scheduleInject` 只保留 `injectButton`。

`DanmuXposedModels.java` 删除 `SettingsOverlayState`、`SettingsRowViews`；`StringValueCallback` / `IntValueCallback` / `FilterSelectListener` 保留。域模型（`Anchor` / `ShellMedia` / `CandidateHandle` / `SourceFilter` / `EpisodeCandidate` / `InjectionSettings` / `BridgeResult` 等）全部保留。

## 重写：三层 UI 基座

### 1. `xposed/DanmuTheme.java`
保持 `of(boolean dark)` 选色 + `dp()` 的入口形状，内容重写：

- 暗/亮双调色板都补齐 focus 专用 token：`focusRing`、`focusFill`、`focusText`，与 `accent` 分离。遥控器焦点和「已选中」必须视觉可区分（旧代码两者共用 accent，选中态上再获得焦点看不出来）。
- 间距 / 圆角 / 字号标尺保留同名常量（`SPACE_*`、`RADIUS_*`、`TEXT_*`），新增 `RADIUS_XL`、`TEXT_MICRO`。
- 焦点绘制统一收口到 `Drawable focusable(...)`，state 顺序 `focused > pressed > selected > default`，focused 用 2dp `focusRing` 描边 + `focusFill`。替换 `interactiveRoundRect`。
- 保留 `roundRect` 两个重载。

### 2. `xposed/DanmuUi.java`
仍是无状态静态方法 + 显式传 `DanmuTheme`：

- 焦点默认打开：可交互组件统一 `setFocusable(true)`、`setFocusableInTouchMode(false)`、`setStateListAnimator(null)`、`setElevation(0f)`、48dp 最小触摸区。
- 组件集：`text` / `sectionLabel` / `statusLine` / `primaryButton` / `secondaryButton` / `ghostButton` / `iconButton` / `chip` / `filterChip` / `textField` / `resultRow`（替代 `listRow`）/ `episodeCell` + `styleEpisodeCell` / `toggleChip` + `styleToggleChip` / `emptyState` / `divider` / `panel`。
- `styleEpisodeCell(c, t, cell, label, selected, titleMode)` 签名保留，内部改用新 focus token。
- 新增 `wireGridFocus(ViewGroup grid, int columns)`：给分集网格逐格设置 `nextFocusLeft/Right/Up/Down`，实现方向键按行列遍历（Android 默认几何寻焦在 ScrollView 里的 wrap 网格上不可靠）。

### 3. `xposed/DanmuDialog.java`
保留 `AlertDialog` 承载（宿主进程里最稳）：

- `create(Activity, View)` 保留。
- `showCentered(dialog, activity[, maxWidthDp])` 保留并强化：`Gravity.CENTER`、透明窗口背景、`dimAmount`、`FLAG_DIM_BEHIND`；宽度按屏幕方向取值，高度加屏高 88% 上限，避免横屏双列被顶出屏幕。
- `showTextInput(...)` / `showSingleChoice(...)` 保留，保留 `InputValidator` 与「就地报错不关窗」行为。
- `root(Activity, DanmuTheme)` 保留作为统一外壳。
- 新增 `focusFirst(AlertDialog, View)`：`show()` 后把初始焦点落到指定控件，遥控器打开弹窗必须有落点。

## 重写：主注入弹窗 `xposed/DanmuXposedManualSearchDialog.java`

对外唯一依赖仍是 `Host` 接口（`readInjectionSettings` / `queryBridgeAnimeSearch` / `loadAnimeDetailDirect` / `pushCandidate` / `autoPushCurrent` / `readShellMedia` / `episodeCandidate` 等），bridge 与 push 逻辑零改动。

信息架构：

- 搜索框常驻顶部，不再是独立的搜索步骤，省掉一次跳转。顶部一行 = 搜索框 + 搜索按钮 + 设置入口（齿轮）+ 推送记录入口（带未读小红点）。
- 横屏双列：左列结果列表（`resultRow`），右列选中条目的分集网格，左右各自滚动。
- 竖屏分步：结果列表 → 分集网格两步，带返回。
- 状态文案固定底部（`statusLine`），保持现有推送反馈文案。
- 平台过滤 chip 行保留在结果列表上方。
- 保留交互约定：从 `readShellMedia` 自动预填并自动搜索、分集列数按宽度 clamp（横屏放宽上限）、滚动定位到当前集、再点一次已选中的分集 = 推送。

异步与生命周期（保留现有正确性，测试继续钉住）：

- `SearchDialogState` 继续持有 `searchRequestId` / `detailRequestId` / `active` / stage / mode / gridColumns / showTitles。
- `dialog.setOnDismissListener(d -> state.active = false)`。
- 回调开头 `if (!state.active || requestId != state.searchRequestId) return;`（detail 同理）。

## 新增：`xposed/DanmuXposedSettingsDialog.java`

用新弹窗风格承载原 overlay 的 5 项设置，从主弹窗齿轮入口打开：

- 界面主题（黑色 / 白色）、集详情显示（带标题 / 数字格）、时间轴偏移、弹幕大小（默认 / 20 / 24 / 28 / 32 / 自定义）、影视壳端口（1–65535）。
- 选择类用 `DanmuDialog.showSingleChoice`，数值类用 `showTextInput` + `InputValidator`（端口范围校验沿用现有规则）。
- 复用 `DanmuXposedSettingsStore.readInjectionSettings` / `saveInjectionSettings`（key 与默认值不变，`darkTheme` 默认 `false`），沿用「读全量 → 改一个字段 → 整体写回」。
- 自身 Host 只需 `readInjectionSettings` / `saveInjectionSettings` / `readEpisodeShowTitles` / `saveEpisodeShowTitles` / `warn`。不再需要 `installBackInterceptor`，`AlertDialog` 自带返回键关闭。
- 主题切换后关闭并让调用方重建主弹窗。

## 新增：`xposed/DanmuXposedPushHistoryDialog.java`

把 `showPushHistoryDialog` 从 `DanmuXposedPushCoordinator` 搬出业务层，用新样式重画（记录读取仍留在 coordinator，通过 getter 暴露）。

同时解耦 coordinator 里的 UI 直写：`pushCandidate` / `autoPushCurrent` 的 `TextView statusText, TextView pushInfoText` 参数改为回调接口

```java
interface PushFeedback { void onStatus(String status); void onPushInfo(String info); }
```

push 逻辑不再持有 View 引用（弹窗关闭后不会写死 View），主弹窗自己决定往哪个 `TextView` 渲染。`notifyDot` 同理换成 `Runnable` / 布尔查询。

## 同步改写测试断言

- `InjectedPlaybackDialogPolicyTest.kt`：`DanmuXposedPlaybackControls` 的 4 个测试原样保留（行为不变）。弹窗断言指向新结构，继续钉住 `DanmuDialog.create(activity, root)`、`showCentered`、`SearchDialogState`、`setOnDismissListener(d -> state.active = false)`、`requestId != state.searchRequestId/detailRequestId`；新增横屏双列分支、`focusFirst`、`wireGridFocus` 的存在性断言；保留 `Gravity.BOTTOM`、宿主伪类名反射那几条 `assertFalse` 防回归。
- `InjectedSettingsOverlayPolicyTest.kt`：重写为设置弹窗策略测试。删掉 3 个关于 overlay 与壁纸截屏的测试（`android.R.id.content`、`attachHostNavigationCloseGuard`、`findHostBackHandlerMethod` 的 `"p0"`/`"q"`、`resolveHostPageBackground`、`captureScreenAlignedDrawable`），改为断言这些符号已从 `xposed` 源码中消失。保留并迁移「`showTextInput` 就地校验不关窗」那条断言。
- `DanmuXposedTextPolicyTest.kt` 不动（纯逻辑，无 UI 断言）。

## 主要改动文件

```
新增  app/src/main/java/com/example/danmuapiapp/xposed/DanmuXposedSettingsDialog.java
新增  .../xposed/DanmuXposedPushHistoryDialog.java
重写  .../xposed/DanmuTheme.java
重写  .../xposed/DanmuUi.java
重写  .../xposed/DanmuDialog.java
重写  .../xposed/DanmuXposedManualSearchDialog.java
改    .../xposed/DanmuXposedModule.java          (拆掉 overlay / 设置行 wiring)
改    .../xposed/DanmuXposedPushCoordinator.java (移出历史弹窗，TextView → PushFeedback)
改    .../xposed/DanmuXposedModels.java          (删 SettingsOverlayState / SettingsRowViews)
删    .../xposed/DanmuXposedSettingsOverlay.java
删    .../xposed/DanmuXposedSettingsRowInjector.java
删    .../xposed/DanmuXposedHostBackgrounds.java
删    .../xposed/HostBackgroundColorPolicy.java
不动  .../xposed/DanmuXposedPlaybackControls.java
重写  app/src/test/java/.../InjectedPlaybackDialogPolicyTest.kt
重写  app/src/test/java/.../InjectedSettingsOverlayPolicyTest.kt
```

## 执行顺序

1. 基座三件（`DanmuTheme` → `DanmuUi` → `DanmuDialog`），先编译通过。
2. 删除 4 个文件 + 清理 `DanmuXposedModule` / `DanmuXposedModels` wiring。
3. `DanmuXposedPushCoordinator` 的 `PushFeedback` 解耦 + 新推送记录弹窗。
4. 新设置弹窗。
5. 重写主搜索弹窗（横屏双列 / 竖屏分步 / 常驻搜索框 / 焦点链）。
6. 重写两个策略测试。

## 验证

```bash
cd DanmuApiApp
./gradlew :app:compileDebugJavaWithJavac
./gradlew testDebugUnitTest --tests 'com.example.danmuapiapp.xposed.*'
```

`assembleRelease` 较重，最后跑一次确认打包（`merges += "META-INF/xposed/*"` 与 `java_init.list` 入口不变，注入点没动）。

单测跑不到真实渲染。以下需要装到宿主上手测：横屏双列排布、遥控器方向键在分集网格里的遍历、暗/亮主题切换后重建、设置入口保存后生效。

## 落地状态

已完成。App 内弹窗同步收敛为共享居中弹窗，注入层已按本文方案完成独立搜索、设置与推送记录弹窗重写；宿主设置页 overlay、设置行注入、壁纸截屏和返回键 hook 均已移除。

发布前验证：

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:releaseCheck
```
