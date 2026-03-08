# 项目架构说明

## 分层约定

- `domain/`：领域模型与仓库接口，只放稳定抽象。
- `data/repository/`：业务数据编排，负责把服务能力组织成上层可用的仓库实现。
- `data/remote/github/`：GitHub 相关远程访问能力，统一处理代理、鉴权、Release 拉取与元数据请求。
- `data/service/`：运行时、文件、系统交互、更新、辅助工具等基础设施能力。
- `ui/screen/<feature>/`：按功能拆分页面代码，避免单文件堆叠所有职责。

## 页面目录约定

### `ui/screen/home/`
- `HomeScreen.kt`：首页状态汇总与页面编排。
- `dialog/`：首页弹窗与表单类 UI。
- `section/`：首页卡片、队列、面板等可复用区块。
- `support/`：首页辅助弹窗、枚举映射、展示格式化函数。

### `ui/screen/config/`
- `ConfigScreen.kt`：配置页入口与模式切换。
- `section/`：视觉模式、原始模式等页面区块。
- `editor/`：复杂编辑器、专用编辑器、凭证编辑器。
- `util/`：配置页专用解析与序列化工具。

### `ui/screen/download/`
- `DanmuDownloadScreen.kt`：下载页入口与顶层导航。
- `model/`：下载页 UI 模型。
- `section/`：搜索页、队列页、记录页等独立内容区域。

## 本次重构重点

- 抽出 `GithubRemoteService`，统一 GitHub Release 与元数据请求逻辑。
- 抽出 `GithubProxySpeedTester`，消除多个 ViewModel 中重复的代理测速实现。
- 抽出 `CoreVersionParser`，统一核心版本解析逻辑。
- 将首页、配置页、下载页中的超长文件拆为按职责划分的子文件。
- 保持原有功能入口与调用关系不变，优先保证行为兼容。

## 后续新增代码建议

- 新功能优先按功能目录归类，不要继续把大型组件堆回入口文件。
- 远程接口或 GitHub 请求优先放到 `data/remote/`，不要直接散落在多个仓库或服务里。
- 页面私有模型优先放到对应功能的 `model/` 目录。
- 复杂表单、弹窗、列表区块优先拆到 `section/`、`dialog/`、`editor/` 子目录。

## 第二轮深度重构补充

- `ui/common/AppUpdateInstallerController.kt`：统一处理 App 安装包下载、浏览器跳转、安装器拉起与安装状态弹窗。
- `ui/common/ProxyPickerController.kt`：统一处理 GitHub 线路选择弹窗、测速状态与确认逻辑。
- `ui/common/ViewModelUiFormatters.kt`：统一处理 Root 权限错误文案、字节格式化、`.env` 文本解析。
- `ui/screen/download/support/DanmuDownloadParsing.kt`：统一处理下载页的搜索结果解析、剧集匹配、标题归一化与状态预构建。

## 当前重构结果

- UI 入口文件主要保留编排职责，重逻辑迁移到控制器、支持文件或子区块文件。
- 重复实现集中收口，避免 Home/Settings/Download 各自维护同一套逻辑。
- 与现有页面调用保持兼容，优先不破坏已有界面层代码。
