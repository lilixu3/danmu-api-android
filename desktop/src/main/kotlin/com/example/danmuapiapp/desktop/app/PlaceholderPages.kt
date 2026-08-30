package com.example.danmuapiapp.desktop.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 尚未实现页面的诚实占位：只说明规划内容与对应任务编号，
 * 不展示假数据或假开关（风险登记 R-08：不允许名义完成）。
 */
@Composable
fun PlaceholderPage(page: DesktopPage) {
    val plan = pagePlan(page)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.PagePadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = page.label,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Card(shape = RoundedCornerShape(Dimens.CardCorner)) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = plan.summary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "规划内容",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                plan.items.forEach { item ->
                    Text(
                        text = "· $item",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "对应计划任务：${plan.taskIds}（当前阶段：P0 技术闭环，页面从 P3 开始铺开）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private data class PagePlan(val summary: String, val items: List<String>, val taskIds: String)

private fun pagePlan(page: DesktopPage): PagePlan = when (page) {
    DesktopPage.Core -> PagePlan(
        summary = "稳定 / 开发 / 自定义核心的安装、更新、切换、回退与依赖修复。",
        items = listOf("核心列表与版本", "在线更新与回退", "分支选择", "依赖包修复"),
        taskIds = "W-0402（P4）",
    )
    DesktopPage.Logs -> PagePlan(
        summary = "桌面宿主、Node 运行时与核心业务日志的统一查看、筛选和导出。",
        items = listOf("关键字搜索", "来源与级别筛选", "当前结果复制", "当前结果导出"),
        taskIds = "W-0404（P4）",
    )
    DesktopPage.Configuration -> PagePlan(
        summary = "可视化分类配置、原始 .env 编辑、凭证管理与变更预览。",
        items = listOf("分类表单", "原始模式编辑器", "凭证编辑", "热更新与重启提示"),
        taskIds = "W-0403（P4）",
    )
    DesktopPage.Downloads -> PagePlan(
        summary = "弹幕搜索、下载队列、历史记录与预览。",
        items = listOf("搜索与选集", "下载队列与重试", "历史与预览", "目录与限速设置"),
        taskIds = "W-0504（P5）",
    )
    DesktopPage.Activity -> PagePlan(
        summary = "实时日志、桌面宿主日志与请求记录统一查看。",
        items = listOf("实时日志与筛选", "请求记录表", "暂停滚动与导出", "诊断导出"),
        taskIds = "W-0404（P4）",
    )
    DesktopPage.Tools -> PagePlan(
        summary = "API 测试工作台、弹幕推送、设备访问控制与缓存管理。",
        items = listOf("API 测试", "弹幕推送与扫码登录", "设备访问控制", "缓存管理"),
        taskIds = "W-0501 ~ W-0503（P5）",
    )
    DesktopPage.Settings -> PagePlan(
        summary = "运行时、工作目录、网络、GitHub 线路、备份恢复、主题与启动行为。",
        items = listOf("工作目录选择与迁移", "网络与代理", "WebDAV 备份恢复", "开机启动与主题"),
        taskIds = "W-0204 / W-0601 ~ W-0606（P2/P6）",
    )
    DesktopPage.Overview -> PagePlan("概览页已实现。", emptyList(), "W-0401")
    DesktopPage.About -> PagePlan("关于页已实现。", emptyList(), "—")
}
