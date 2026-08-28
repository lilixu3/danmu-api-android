# Windows 桌面端交接页

本文件只记录动态状态。长期目标、架构、功能矩阵、阶段任务和验收标准见
[`docs/plans/2026-08-28-windows-desktop-port.md`](plans/2026-08-28-windows-desktop-port.md)。

## 当前快照

| 字段 | 值 |
|---|---|
| 状态 | 规划完成，尚未实现 |
| 当前阶段 | P0 技术可行性和 Windows 闭环 |
| 当前任务 | W-0001 记录基线并建立 Windows 构建工作流 |
| 基线分支 | `test` |
| 规划前基线提交 | `1756205` |
| 文档提交基线 | `2565526` |
| 最后更新 | 2026-08-28 |
| Windows 构建 | 尚未执行 |
| Windows 实机 | 尚未验证 |
| 发布状态 | 仅本次计划文档推送已授权；未授权发版、上传资产或业务代码推送 |

## 已完成

- [x] 只读盘点现有 Android、domain、data、UI、Node 和测试结构。
- [x] 确定采用 Termux 主开发、Windows 早期持续验证的工作方式。
- [x] 建立总计划、功能矩阵、阶段 Gate、风险登记和完成定义。
- [ ] 建立 Windows 构建环境或 Windows CI 探针。
- [ ] 创建最小 Desktop 模块。
- [ ] 完成 Node 子进程 spike。
- [ ] 生成并安装测试包。
- [ ] 关闭 P0 Gate。

## 当前决策

| 编号 | 决策 | 状态 |
|---|---|---|
| D-001 | 保留 Android `:app`，新增独立 Desktop 模块 | 确定 |
| D-002 | Desktop UI 默认采用 Compose Multiplatform Desktop | P0 待验证 |
| D-003 | 桌面 UI 重新设计，不复刻手机布局 | 确定 |
| D-004 | Windows 使用独立 `node.exe` 子进程 | P0 待验证 |
| D-005 | 共享层按价值小批抽取 | 确定 |
| D-006 | 第一版 Windows 10/11 x64 | 待用户确认 |

## 下一位接手者的起点

不要先写完整 UI。按顺序执行：

1. 阅读总计划的第 4、5、6、10、11 和 15 节。
2. 确认用户是否授权开始实现；当前只有本次计划文档推送授权，没有业务代码推送或
   发布授权。
3. 检查工作树，不得覆盖无关改动。
4. 重新记录当前 commit、分支、JDK、Gradle、Node 和 Android 基线结果。
5. 执行 W-0001：增加只构建不发布的 Windows CI 探针，或在可用 Windows 机器上
   记录完全可复现的构建命令。
6. W-0001 通过后才执行 W-0002 最小 Desktop 模块。

## 开始前检查

```bash
git status --short --branch
git rev-parse --short HEAD
git diff --check
sed -n '1,260p' docs/plans/2026-08-28-windows-desktop-port.md
sed -n '1,220p' docs/windows-desktop-handoff.md
```

Termux 基线命令：

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:checkNodeRuntimeScripts \
  :app:testNodeRuntimeParsing \
  :app:testBundledBrotliRuntime \
  :app:testBundledNodeLockClosure \
  :app:testBundledCoreRuntimeDependencies
./gradlew :app:assembleDebug
```

若 `JAVA_HOME` 或目标 Node 未配置，先使用当前 Termux 环境已有的工具链定位方式，
不要把个人绝对路径提交进仓库。

## 当前已知事实

- 仓库当前只有 Android `:app` 模块。
- Android UI 使用 Jetpack Compose、Android Navigation、Hilt 和 Android 生命周期组件。
- `domain` 是最干净的共享候选，但不能未审计就整目录移动。
- `android-server.js` 大部分是标准 Node 逻辑，同时包含嵌入式 Node 的退出约束。
- Windows 必须重新实现进程宿主、文件路径、托盘、开机启动、通知、安装和更新。
- Android 的 Root、Xposed、无障碍保活、快捷设置和 APK 安装不是 Windows 功能。
- 现有 JVM 测试能保护 Android 行为，但当前没有 Windows 构建或 UI 验证结果。

## 尚未验证的假设

- 当前 Kotlin、Gradle 与选定 Compose Desktop 版本可以稳定组合。
- Desktop 安装包能按预期携带固定版本 Node 和运行时依赖。
- 当前 Node server 在 Windows 中文/空格路径下无需额外修复。
- 现有健康、关闭和日志接口足够支持 Windows supervisor。
- 现有玻璃设计能在 Desktop 上获得合格、稳定的降级效果。

任何接手者不得把这些假设写成“已支持”。P0 的职责就是给出证据。

## 阻塞项

当前没有阻塞文档工作的事项。开始实现前需要至少满足一项：

- 有可操作的 Windows 10/11 x64 机器；或
- 用户允许新增仅构建测试用途的 `windows-latest` CI job。

代码签名证书不阻塞 P0，P0 使用内部未签名测试包。发布和签名仍需单独授权。

## 验证历史

| 日期 | 提交 | 环境 | 命令/检查 | 结果 | 备注 |
|---|---|---|---|---|---|
| 2026-08-28 | `1756205` | Termux | 只读文件、依赖和测试规模盘点 | PASS | 未运行构建，未修改业务代码 |

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

## 决策变更格式

```text
Decision: D-xxx
Date: YYYY-MM-DD
Old: <old decision>
New: <new decision>
Evidence: <test, prototype, issue or user decision>
Affected tasks/files: <list>
Migration: <required follow-up>
```

## 每次工作结束前

1. 更新当前阶段、当前任务和最后更新日期。
2. 标记实际完成的 checklist，不能批量预勾选。
3. 写入精确验证命令、结果和 Windows 环境。
4. 记录未提交改动和与本任务无关的用户改动。
5. 记录唯一明确的下一任务，避免接手者自行猜测优先级。
6. 运行 `git diff --check`。
7. 没有明确授权时，不 commit、不 push、不发布、不上传安装包。

## 当前下一任务

`W-0001`：确认 Windows 验证载体，建立不发布资产的 Windows 构建探针，并记录固定
JDK、Gradle、Kotlin、Compose 和 Node 版本。完成前不开始完整页面开发。
