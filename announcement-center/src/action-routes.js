const ACTION_ROUTE_OPTIONS = [
  {
    group: '底部主页面',
    items: [
      { value: 'home', label: '首页' },
      { value: 'core', label: '核心页' },
      { value: 'tools', label: '工具页' },
      { value: 'settings', label: '设置页' },
    ],
  },
  {
    group: '工具页面',
    items: [
      { value: 'tool_api_test', label: '工具 / API 测试' },
      { value: 'tool_push_danmu', label: '工具 / 推送弹幕' },
      { value: 'tool_danmu_download', label: '工具 / 弹幕下载' },
      { value: 'tool_request_records', label: '工具 / 请求记录' },
      { value: 'tool_console', label: '工具 / 控制台' },
      { value: 'tool_config', label: '工具 / 配置编辑' },
      { value: 'tool_device_access', label: '工具 / 设备访问' },
      { value: 'tool_cache_management', label: '工具 / 缓存管理' },
    ],
  },
  {
    group: '设置页面',
    items: [
      { value: 'settings_runtime_dir', label: '设置 / 运行时与目录' },
      { value: 'settings_theme_display', label: '设置 / 主题显示' },
      { value: 'settings_work_dir', label: '设置 / 工作目录' },
      { value: 'settings_service_config', label: '设置 / 服务配置' },
      { value: 'settings_danmu_download', label: '设置 / 下载设置' },
      { value: 'settings_network', label: '设置 / 网络设置' },
      { value: 'settings_github_token', label: '设置 / GitHub Token' },
      { value: 'settings_backup_restore', label: '设置 / 备份与恢复' },
      { value: 'settings_admin_mode', label: '设置 / 管理员模式' },
      { value: 'settings_about', label: '设置 / 关于页面' },
    ],
  },
];

const ALLOWED_ACTION_ROUTES = new Set(
  ACTION_ROUTE_OPTIONS.flatMap((group) => group.items.map((item) => item.value))
);

function isAllowedActionRoute(value) {
  return ALLOWED_ACTION_ROUTES.has(String(value || '').trim());
}

module.exports = {
  ACTION_ROUTE_OPTIONS,
  ALLOWED_ACTION_ROUTES,
  isAllowedActionRoute,
};
