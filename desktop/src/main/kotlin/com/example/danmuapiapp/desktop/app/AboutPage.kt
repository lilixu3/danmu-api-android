package com.example.danmuapiapp.desktop.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.example.danmuapiapp.desktop.APP_NAME
import com.example.danmuapiapp.desktop.node.DesktopCoreInstaller

@Composable
fun AboutPage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.PagePadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "关于",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Card(shape = RoundedCornerShape(Dimens.CardCorner)) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = APP_NAME,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                AboutRow("版本", "0.1.0（Windows 桌面端）")
                AboutRow("Android 对应版本", "v1.0.5.81（核心与交互约定以 Android 端为准）")
                AboutRow("项目仓库", "github.com/lilixu3/danmu-api-android")
                AboutRow("核心来源", DesktopCoreInstaller.STABLE_REPO + " / " + DesktopCoreInstaller.DEV_REPO + "（在线安装）")
                AboutRow("依赖包仓库", "lilixu3/danmu-api-runtime-packs")
                AboutRow("Node 运行时", "nodejs-mobile v24.19.0（win-x64，随包内置）")
                AboutRow("技术栈", "Kotlin + Compose Multiplatform Desktop 1.12.0")
            }
        }
        Card(shape = RoundedCornerShape(Dimens.CardCorner)) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "说明",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "· 桌面端与 Android 端共享同一 danmu_api 核心与配置约定（.env 环境变量、" +
                        "默认端口 9321、默认 Token 87654321）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "· 核心不随包内置，首次启动在线下载；无法直连 GitHub 时请在" +
                        " 设置 → GitHub 线路 选择加速镜像。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "· 卸载应用默认保留运行目录（含配置、缓存与下载内容）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 16.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
