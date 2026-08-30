package com.example.danmuapiapp.desktop.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.desktop.APP_NAME
import com.example.danmuapiapp.desktop.node.DesktopCoreInstaller

@Composable
fun AboutPage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(DesktopTokens.PagePadding),
        verticalArrangement = Arrangement.spacedBy(DesktopTokens.PageGap),
    ) {
        DesktopSurface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(DesktopTokens.CardPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(APP_NAME, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "在 Windows 上运行弹幕 API 服务，让播放器或局域网设备通过完整 API 地址访问。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        DesktopSectionCard(title = "应用信息", supportingText = "桌面端运行环境与核心来源。") {
            AboutRow("版本", "0.1.0（Windows x64）")
            DesktopDivider()
            AboutRow("项目仓库", "github.com/lilixu3/danmu-api-android", monospace = true)
            DesktopDivider()
            AboutRow("核心来源", "${DesktopCoreInstaller.STABLE_REPO} / ${DesktopCoreInstaller.DEV_REPO}")
            DesktopDivider()
            AboutRow("运行时", "Node.js 24.19.0（随桌面包提供）")
            DesktopDivider()
            AboutRow("默认服务", "0.0.0.0:9321 · 默认访问凭证 87654321")
        }

        DesktopSectionCard(title = "快速使用", supportingText = "第一次使用按以下顺序操作即可。") {
            UsageStep("1", "准备核心", "进入“核心”页面，选择 GitHub 直连或加速线路，手动下载需要的核心版本。")
            UsageStep("2", "启动服务", "回到“概览”页面点击“启动服务”，等待状态变为“运行中”。")
            UsageStep("3", "复制访问地址", "复制完整的本机或局域网 API 地址给播放器；地址已包含访问凭证，不要单独传播 Token。")
            UsageStep("4", "调整设置", "在“设置”中修改端口、监听地址、工作目录、开机自启和 GitHub 下载线路。")
            UsageStep("5", "管理核心", "核心更新、重装、删除、回退、提交历史、PR 和文件变更都从“核心”页面进入。")
        }

        DesktopSectionCard(title = "排查提示", supportingText = "出现访问或启动问题时，优先查看可验证的诊断信息。") {
            Text("· 局域网访问失败：确认设备在同一 Wi-Fi，检查 Windows 防火墙是否允许 node.exe 入站，并排除路由器 AP 隔离。", style = MaterialTheme.typography.bodySmall)
            Text("· 核心下载失败：重新选择线路并查看错误详情；应用不会静默切换线路或伪造下载成功。", style = MaterialTheme.typography.bodySmall)
            Text("· 服务异常停止：查看“日志”页面的终端日志和“概览”页面的健康诊断，再决定是否重新启动。", style = MaterialTheme.typography.bodySmall)
            Text("· 工作目录保存后需要重新启动应用，新的运行目录才会生效；原目录默认保留。", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String, monospace: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.28f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            modifier = Modifier.weight(0.72f),
        )
    }
}

@Composable
private fun UsageStep(number: String, title: String, description: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(number, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
