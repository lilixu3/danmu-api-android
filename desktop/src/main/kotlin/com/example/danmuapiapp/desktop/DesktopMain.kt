package com.example.danmuapiapp.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.example.danmuapiapp.desktop.node.DesktopCoreInstaller
import java.awt.Dimension
import java.io.File

/**
 * W-0002 最小桌面窗口：只展示构建信息与随包运行资源状态，用于验证
 * Compose Multiplatform Desktop 在 Windows 上的构建、渲染与打包链路。
 */
fun hasClasspathResource(path: String): Boolean {
    val loader = Thread.currentThread().contextClassLoader
    // ClassLoader.getResourceAsStream 不接受前导斜杠
    return loader.getResourceAsStream(path.removePrefix("/"))?.use { true } ?: false
}

fun buildInfoLines(): List<String> {
    val lines = mutableListOf(
        "弹幕 API Desktop — P0 技术验证（最小窗口）",
        "OS: ${System.getProperty("os.name")} ${System.getProperty("os.version")} ${System.getProperty("os.arch")}",
        "Java: ${System.getProperty("java.version")}（${System.getProperty("java.vendor")}）",
        "核心策略: danmu_api 核心不随包内置，首次启动在线下载（${DesktopCoreInstaller.STABLE_REPO}）",
    )
    val nodeBundled = hasClasspathResource("/runtime/node.exe")
    val hostBundled = hasClasspathResource("/runtime/nodejs-project/main.js")
    lines += if (nodeBundled) {
        "node.exe: 已随包提供（内嵌资源，首启解压至数据目录）"
    } else {
        "node.exe: 未打包（开发运行；打包需 -PdanmuNodeExe）"
    }
    lines += if (hostBundled) {
        "nodejs-project: 已随包提供（内嵌资源）"
    } else {
        "nodejs-project: 未打包（开发运行）"
    }
    return lines
}

@Composable
fun DesktopApp() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                buildInfoLines().forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

fun main() = application {
    val windowState = rememberWindowState(width = 1280.dp, height = 800.dp)
    Window(
        onCloseRequest = ::exitApplication,
        title = "弹幕 API Desktop",
        state = windowState,
    ) {
        window.minimumSize = Dimension(960, 640)
        DesktopApp()
    }
}
