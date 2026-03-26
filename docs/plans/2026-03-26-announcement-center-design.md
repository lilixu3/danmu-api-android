# 公告中心设计

## 目标

为 `DanmuApiApp` 增加一套可独立运营的公告系统：

- 管理员可在 VPS 后台创建、编辑、发布、下线公告
- App 在前台恢复时自动拉取公告
- 命中条件的公告以弹窗形式展示给用户
- 管理端与弹幕 API 核心解耦，不把公告逻辑塞进现有 Node 核心

## 约束

- VPS 当前可直接使用 `Docker`，并已有 `MySQL` 服务
- VPS 当前没有 `Nginx/Caddy`，因此首版直接使用 `IP:端口`
- App 端已有远程检查、弹窗组件、设置持久化、Hilt 注入能力，应优先复用
- 首版需要支持管理员独立账号密码登录

## 总体架构

### VPS 端

独立部署一个 `announcement-center` 服务：

- 提供后台管理页
- 提供 App 使用的公告查询 API
- 使用独立管理员账号密码
- 数据持久化到 VPS 上的 MySQL

### Android 端

新增公告模块：

- 公告服务地址配置项
- 前台公告检查器
- 公告模型与远程拉取服务
- 首页弹窗展示与本地已读/稍后提醒状态

## 数据模型

公告核心字段：

- `id`
- `title`
- `summary`
- `content_markdown`
- `cover_image_url`
- `severity`
- `status`
- `popup_enabled`
- `force_popup`
- `allow_snooze_today`
- `target_variants`
- `min_app_version`
- `max_app_version`
- `start_at`
- `end_at`
- `primary_button_text`
- `primary_button_url`
- `secondary_button_text`
- `secondary_button_url`
- `created_at`
- `updated_at`
- `published_at`

## App 投放判定

App 请求公告时会带上：

- 当前 App 版本
- 当前核心变体：`stable` / `dev` / `custom`
- 平台：`android`

服务端根据这些条件返回当前唯一优先公告：

1. 状态必须为已发布
2. 当前时间落在生效区间内
3. App 版本满足最小/最大版本范围
4. 变体命中 `all` 或当前核心变体
5. 按优先级、发布时间排序，返回最应展示的一条

## 弹窗逻辑

App 本地维护：

- 最近一次自动检查时间
- 当日稍后提醒截止时间
- 最近已展示公告 ID
- 最近已确认公告 ID

行为：

- 命中新公告且未被确认时弹窗
- 点“知道了”后记为已确认
- 点“今日不提醒”后进入当日静默
- 强提醒公告不提供“今日不提醒”

## 后台能力

后台不是单输入框，而是完整管理页：

- 登录页
- 公告列表页
- 公告编辑页
- 草稿/发布/下线控制
- 生效时间设置
- 版本范围设置
- 目标变体多选
- 按钮跳转配置
- Markdown 预览

## 部署方式

首版部署路径：

- 代码目录：`/opt/danmu-announcement-center`
- 访问地址：`http://117.72.165.47:18086/admin`
- App API：`http://117.72.165.47:18086/api/app/announcements/active`

## 不纳入首版

- 多管理员角色
- 公告已读统计
- 推送通知
- 富文本图片上传
- 反向代理和 HTTPS
