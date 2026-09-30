package com.example.danmuapiapp.data.tunnel

import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TunnelRegressionTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `stopping during reconnect backoff is a normal cancellation`() {
        val entered = CountDownLatch(1)
        val failure = AtomicReference<Throwable>()
        val worker = Thread {
            runInterruptibleTunnelTask {
                entered.countDown()
                Thread.sleep(60_000)
                fail("cancelled task must not restart the tunnel")
            }
        }.apply { uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, e -> failure.set(e) } }
        worker.start()
        assertTrue(entered.await(2, TimeUnit.SECONDS))
        worker.interrupt()
        worker.join(2000)
        assertFalse(worker.isAlive)
        assertNull(failure.get())
    }

    @Test fun `clearing log retains appenders open file descriptor`() {
        val file = temporary.newFile("frpc.log")
        FileOutputStream(file, true).use { writer ->
            writer.write("old\n".toByteArray())
            assertTrue(TunnelStore.replaceLogContents(file, ""))
            writer.write("new\n".toByteArray())
            writer.flush()
            assertEquals("new\n", file.readText())
        }
    }

    @Test fun `legacy config migrates without losing content or overwriting newer config`() {
        val legacy = temporary.newFile("frpc.toml")
        val content = """{"serverAddr":"example.com"}"""
        legacy.writeText(content)
        val migrated = TunnelStore.migrateConfigFile(temporary.root)
        assertEquals("frpc.conf", migrated.name)
        assertEquals(content, migrated.readText())
        migrated.writeText("new config")
        assertEquals("new config", TunnelStore.migrateConfigFile(temporary.root).readText())
        assertEquals(content, legacy.readText())
    }

    @Test fun `changing imported config port preserves autofill and original text`() {
        val raw = "serverAddr = \"frp.example.com\"\n[[proxies]]\nname = \"api\"\nlocalPort = 9321"
        val settings = TunnelSettings(mode = TunnelMode.Paste, configText = raw)
        for (port in listOf(9321, 12345)) {
            val generated = buildEffectiveFrpcConfig(settings, port)
            val parsed = parseFrpcConfig(generated)
            assertTrue(parsed.hasDnsServer)
            assertTrue(parsed.hasLoginFailExit)
            assertEquals(port, parsed.proxies.single().localPort)
        }
        assertEquals(raw, settings.configText)
        val disabled = settings.copy(form = settings.form.copy(autoFillDefaults = false))
        assertFalse(parseFrpcConfig(buildEffectiveFrpcConfig(disabled, 12345)).hasDnsServer)
    }

    @Test fun `defaults keep newline and yaml document header`() {
        val yaml = "---\nserverAddr: frp.example.com\nproxies:\n  - name: api\n    localPort: 9321"
        val generated = injectFrpcDefaults(yaml)
        assertTrue(generated.startsWith("---\ndnsServer:"))
        assertFalse(generated.contains("9321dnsServer"))
        assertEquals(generated, injectFrpcDefaults(generated))
        val toml = injectFrpcDefaults("serverAddr = \"frp.example.com\"")
        assertTrue(toml.contains("loginFailExit = false\nserverAddr"))
    }

    @Test fun `each http proxy owns its parsed domain array`() {
        val text = """
            serverAddr = "example.com"
            [[proxies]]
            name = "tcp"
            type = "tcp"
            localPort = 9321
            remotePort = 19321
            [[proxies]]
            name = "http"
            type = "http"
            localPort = 9321
            customDomains = [
              "a.example.com",
              'b.example.com',
            ]
        """.trimIndent()
        val proxies = parseFrpcConfig(text).proxies
        assertTrue(proxies[0].customDomains.isEmpty())
        assertNull(proxies[1].remotePort)
        assertEquals(listOf("a.example.com", "b.example.com"), proxies[1].customDomains)
        val base = derivePublicAddress(TunnelSettings(mode = TunnelMode.Paste, configText = text))
        assertEquals("http://a.example.com/test", buildPublicApiUrl(base, "test"))
    }

    @Test fun `ipv6 parsing and public url preserve whole address`() {
        assertEquals(ServerInput("2001:db8::1", null), parseServerInput("2001:db8::1"))
        assertEquals(ServerInput("2001:db8::1", null), parseServerInput("[2001:db8::1]"))
        assertEquals(ServerInput("2001:db8::1", 7001), parseServerInput("[2001:db8::1]:7001"))
        assertEquals(ServerInput("example.com", 7001), parseServerInput("example.com:7001"))
        val form = TunnelFormSettings(serverAddr = "[2001:db8::1]:7001", remotePort = 19321)
        val text = buildFrpcToml(form, 9321)
        assertTrue(text.contains("serverAddr = \"2001:db8::1\""))
        assertTrue(text.contains("serverPort = 7001"))
        assertEquals("http://[2001:db8::1]:19321/test", buildPublicApiUrl(derivePublicAddress(TunnelSettings(form = form)), "test"))
    }
}
