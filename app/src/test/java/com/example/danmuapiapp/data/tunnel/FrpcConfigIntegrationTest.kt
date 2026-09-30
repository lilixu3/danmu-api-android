package com.example.danmuapiapp.data.tunnel

import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** 使用已准备的本机架构内核 verify 配置，不连接服务器、不启动隧道。 */
class FrpcConfigIntegrationTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `real duplicate frpc processes are both stopped after pid record is lost`() {
        val abi = when (System.getProperty("os.arch").orEmpty()) {
            "aarch64", "arm64" -> "arm64-v8a"
            "amd64", "x86_64" -> "x86_64"
            else -> "armeabi-v7a"
        }
        val kernel = listOf(File("../runtime/frp/$abi/libfrpc.so"), File("runtime/frp/$abi/libfrpc.so"))
            .firstOrNull { it.isFile && it.canExecute() }?.canonicalFile
        assumeTrue("需要本机架构 frpc 和 /proc", kernel != null && File("/proc/self/stat").exists())
        val dir = temporary.newFolder("orphan-frpc")
        val config = File(dir, "frpc.conf").apply {
            // 仅连接本机关闭的端口，在后台重试；不接触用户的配置或远程服务器。
            writeText("serverAddr = \"127.0.0.1\"\nserverPort = 1\nloginFailExit = false\n")
        }
        val legacy = File(dir, "frpc.toml").apply { writeText(config.readText()) }
        val children = mutableListOf<Process>()
        try {
            for (input in listOf(legacy, config)) {
                children += ProcessBuilder(kernel!!.path, "-c", input.path)
                    .redirectErrorStream(true).redirectOutput(File(dir, "${input.name}.log")).start()
            }
            Thread.sleep(250)
            assertTrue("两个测试 frpc 都应等待重试", children.all { it.isAlive })
            assertFalse(File(dir, "frpc-root.pid").exists())
            val androidShell = File("/system/bin/sh").canExecute()
            val shell = if (androidShell) "/system/bin/sh" else "sh"
            val path = if (androidShell) "export PATH=/system/bin:/system/xbin\n" else ""
            val stop = ProcessBuilder(shell, "-c", path + RootTunnelScripts.stop(dir.path, kernel!!.path))
                .redirectErrorStream(true).start()
            val output = stop.inputStream.bufferedReader().readText()
            assertEquals(output, 0, stop.waitFor())
            children.forEach {
                assertTrue("停止必须真正结束全部实例", it.waitFor(2, java.util.concurrent.TimeUnit.SECONDS))
            }
        } finally {
            children.forEach { if (it.isAlive) it.destroyForcibly() }
        }
    }

    @Test fun `real frpc accepts all imported formats after migration autofill and port change`() {
        val abi = when (System.getProperty("os.arch").orEmpty()) {
            "aarch64", "arm64" -> "arm64-v8a"
            "amd64", "x86_64" -> "x86_64"
            else -> "armeabi-v7a"
        }
        val kernel = listOf(File("../runtime/frp/$abi/libfrpc.so"), File("runtime/frp/$abi/libfrpc.so"))
            .firstOrNull { it.isFile && it.canExecute() }
        assumeTrue("运行 scripts/prepare_frp_kernel.sh 准备本机 frpc 后启用此集成测试", kernel != null)
        val fixtures = listOf(
            """{"serverAddr":"127.0.0.1","serverPort":7000,"proxies":[{"name":"api","type":"tcp","localIP":"127.0.0.1","localPort":9321,"remotePort":19321}]}""",
            """
                ---
                serverAddr: 127.0.0.1
                serverPort: 7000
                proxies:
                  - name: api
                    type: tcp
                    localIP: 127.0.0.1
                    localPort: 9321
                    remotePort: 19321
            """.trimIndent(),
            """
                [common]
                server_addr = 127.0.0.1
                server_port = 7000
                [api]
                type = tcp
                local_ip = 127.0.0.1
                local_port = 9321
                remote_port = 19321
            """.trimIndent(),
            buildFrpcToml(TunnelFormSettings(serverAddr = "127.0.0.1", remotePort = 19321), 9321).trimEnd()
        )
        fixtures.forEachIndexed { index, raw ->
            val directory = temporary.newFolder("format-$index")
            File(directory, "frpc.toml").writeText(raw)
            val config = TunnelStore.migrateConfigFile(directory)
            val settings = TunnelSettings(mode = TunnelMode.Paste, configText = raw)
            config.writeText(buildEffectiveFrpcConfig(settings, 12345))
            assertEquals(12345, parseFrpcConfig(config.readText()).proxies.single().localPort)
            val process = ProcessBuilder(kernel!!.canonicalPath, "verify", "-c", config.absolutePath)
                .redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            assertEquals("format $index: $output", 0, process.waitFor())
        }
    }
}
