package com.example.danmuapiapp.desktop.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.PrintWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList

class AppInstanceLockTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun controlServerAcceptsAuthenticatedShowCommandAndCleansEndpoint() {
        val lockFile = File(temp.root, "app.lock")
        val commands = CopyOnWriteArrayList<InstanceCommand>()
        assertTrue(AppInstanceLock.tryAcquire(lockFile))
        try {
            assertEquals(null, AppInstanceLock.startControlServer(lockFile) { commands += it })
            val endpointFile = File(temp.root, "instance.endpoint")
            assertTrue(endpointFile.isFile)
            val endpoint = endpointFile.readLines(StandardCharsets.UTF_8)
                .associate { line -> line.substringBefore('=') to line.substringAfter('=') }
            val port = endpoint.getValue("port").toInt()
            val token = endpoint.getValue("token")

            Socket().use { socket ->
                socket.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 1_000)
                PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)
                    .println("$token\t${InstanceCommand.SHOW_SETTINGS.name}")
                assertEquals("OK", socket.getInputStream().bufferedReader(StandardCharsets.UTF_8).readLine())
            }
            val deadline = System.currentTimeMillis() + 1_000
            while (InstanceCommand.SHOW_SETTINGS !in commands && System.currentTimeMillis() < deadline) {
                Thread.sleep(10)
            }
            assertEquals(listOf(InstanceCommand.SHOW_SETTINGS), commands.toList())

            Socket().use { socket ->
                socket.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 1_000)
                PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)
                    .println("invalid-token\t${InstanceCommand.SHOW_OVERVIEW.name}")
                assertTrue(socket.getInputStream().bufferedReader(StandardCharsets.UTF_8).readLine().startsWith("ERROR"))
            }
            assertEquals(listOf(InstanceCommand.SHOW_SETTINGS), commands.toList())
        } finally {
            AppInstanceLock.release()
        }
        assertFalse(File(temp.root, "instance.endpoint").exists())
    }
}
