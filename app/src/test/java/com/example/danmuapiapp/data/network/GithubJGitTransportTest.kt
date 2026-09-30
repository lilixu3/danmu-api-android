package com.example.danmuapiapp.data.network

import com.sun.net.httpserver.HttpServer
import okhttp3.OkHttpClient
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.TransportHttp
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.net.URL
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class GithubJGitTransportTest {
    private fun tempDir(): File = Files.createTempDirectory("github-transport-test-").toFile()

    @Test fun streamsPostAndPreservesJGitHeadersAndRedirectControl() {
        val root = tempDir()
        val seen = AtomicReference<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/upload") { exchange ->
            seen.set(exchange.requestMethod + ":" + exchange.requestHeaders.getFirst("Content-Type") + ":" + exchange.requestBody.readBytes().toString(Charsets.UTF_8))
            exchange.responseHeaders.add("Content-Type", "application/x-git-upload-pack-result")
            exchange.responseHeaders.add("X-Git-Test", "one")
            val body = "pack-data".toByteArray(); exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.createContext("/redirect") { exchange ->
            exchange.responseHeaders.add("Location", "/upload")
            exchange.sendResponseHeaders(302, -1); exchange.close()
        }
        server.start()
        val factory = GithubJGitTransport(OkHttpClient(), root, null)
        try {
            val connection = factory.create(URL("http://127.0.0.1:${server.address.port}/upload"))
            connection.setRequestMethod("POST")
            connection.setRequestProperty("Content-Type", "application/x-git-upload-pack-request")
            connection.setDoOutput(true); connection.setFixedLengthStreamingMode(6)
            connection.outputStream.use { it.write("wanted".toByteArray()) }
            assertEquals(200, connection.responseCode)
            assertEquals("application/x-git-upload-pack-result", connection.contentType)
            assertEquals(listOf("one"), connection.getHeaderFields("X-Git-Test"))
            assertEquals("pack-data", connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) })
            assertEquals("POST:application/x-git-upload-pack-request:wanted", seen.get())
            assertTrue(root.listFiles().orEmpty().isEmpty())
            val redirect = factory.create(URL("http://127.0.0.1:${server.address.port}/redirect"))
            redirect.setInstanceFollowRedirects(false)
            assertEquals(302, redirect.responseCode)
            assertEquals("/upload", redirect.getHeaderField("Location"))
        } finally { factory.close(); server.stop(0); root.deleteRecursively() }
    }

    @Test fun closeCancelsBlockedCall() {
        val root = tempDir(); val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/blocked") { exchange ->
            entered.countDown(); release.await(5, TimeUnit.SECONDS); exchange.close()
        }
        server.start(); val pool = Executors.newSingleThreadExecutor()
        val factory = GithubJGitTransport(OkHttpClient(), root, null)
        try {
            val result = pool.submit<Boolean> {
                try { factory.create(URL("http://127.0.0.1:${server.address.port}/blocked")).responseCode; false }
                catch (_: java.io.IOException) { true }
            }
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            factory.close()
            assertTrue(result.get(2, TimeUnit.SECONDS))
        } finally { release.countDown(); factory.close(); server.stop(0); pool.shutdownNow(); root.deleteRecursively() }
    }

    @Test fun rejectsDisablingCertificateOrHostnameValidation() {
        val root = tempDir(); val factory = GithubJGitTransport(OkHttpClient(), root, null)
        try {
            val connection = factory.create(URL("https://github.com/repo"))
            assertThrows(java.security.KeyManagementException::class.java) { connection.configure(null, null, null) }
            assertThrows(java.security.KeyManagementException::class.java) { connection.setHostnameVerifier { _, _ -> true } }
        } finally { factory.close(); root.deleteRecursively() }
    }

    @Test fun clonesAndFetchesThroughActualSmartHttp() {
        val root = tempDir(); val source = File(root, "source"); val repositories = File(root, "repos").apply { mkdirs() }
        val bare = File(repositories, "sample.git")
        Git.init().setDirectory(source).call().use { git ->
            File(source, "sample.txt").writeText("first")
            git.add().addFilepattern(".").call()
            git.commit().setMessage("first").setAuthor("Test", "test@example.invalid").call()
            Git.init().setBare(true).setDirectory(bare).call().close()
            git.push().setRemote(bare.absolutePath).call()
        }
        val execPath = ProcessBuilder("git", "--exec-path").start().inputStream.bufferedReader().readText().trim()
        val backend = File(execPath, "git-http-backend")
        assertTrue("git-http-backend must be installed", backend.canExecute())
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            try {
                val rawInput = exchange.requestBody.readBytes()
                val input = if (exchange.requestHeaders.getFirst("Content-Encoding") == "gzip")
                    java.util.zip.GZIPInputStream(rawInput.inputStream()).use { it.readBytes() } else rawInput
                val process = ProcessBuilder(backend.absolutePath).apply {
                    environment().putAll(mapOf(
                        "GIT_PROJECT_ROOT" to repositories.absolutePath, "GIT_HTTP_EXPORT_ALL" to "1",
                        "PATH_INFO" to exchange.requestURI.path, "QUERY_STRING" to exchange.requestURI.rawQuery.orEmpty(),
                        "REQUEST_METHOD" to exchange.requestMethod, "CONTENT_TYPE" to exchange.requestHeaders.getFirst("Content-Type").orEmpty(),
                        "CONTENT_LENGTH" to input.size.toString(), "SERVER_PROTOCOL" to "HTTP/1.1", "REMOTE_ADDR" to "127.0.0.1"
                    ))
                }.start()
                process.outputStream.use { it.write(input) }
                val output = process.inputStream.readBytes()
                val error = process.errorStream.bufferedReader().readText()
                check(process.waitFor() == 0) { error }
                val text = output.toString(Charsets.ISO_8859_1)
                val split = text.indexOf("\r\n\r\n")
                check(split >= 0) { "invalid CGI response" }
                var status = 200
                text.substring(0, split).split("\r\n").forEach { line ->
                    val colon = line.indexOf(':')
                    if (colon > 0) {
                        val name = line.substring(0, colon); val value = line.substring(colon + 1).trim()
                        if (name.equals("Status", true)) status = value.substringBefore(' ').toInt()
                        else exchange.responseHeaders.add(name, value)
                    }
                }
                val payload = output.copyOfRange(split + 4, output.size)
                exchange.sendResponseHeaders(status, payload.size.toLong())
                exchange.responseBody.use { it.write(payload) }
            } catch (error: Exception) { error.printStackTrace(); runCatching { exchange.sendResponseHeaders(500, -1); exchange.close() } }
        }
        server.start(); val factory = GithubJGitTransport(OkHttpClient(), File(root, "requests"), null)
        try {
            val remote = "http://127.0.0.1:${server.address.port}/sample.git"
            val callback = org.eclipse.jgit.api.TransportConfigCallback { transport -> (transport as TransportHttp).setHttpConnectionFactory(factory) }
            val cloned = File(root, "cloned")
            Git.cloneRepository().setURI(remote).setDirectory(cloned).setTransportConfigCallback(callback).call().use { git ->
                assertEquals("first", File(cloned, "sample.txt").readText())
                Git.open(source).use { original ->
                    File(source, "sample.txt").writeText("second")
                    original.add().addFilepattern(".").call()
                    original.commit().setMessage("second").setAuthor("Test", "test@example.invalid").call()
                    original.push().setRemote(bare.absolutePath).call()
                }
                git.fetch().setRemote(remote).setRefSpecs(org.eclipse.jgit.transport.RefSpec("+refs/heads/master:refs/remotes/origin/master")).setTransportConfigCallback(callback).call()
                assertNotNull(git.repository.resolve("refs/remotes/origin/master"))
                Git.open(source).use { original ->
                    assertEquals(original.repository.resolve("HEAD"), git.repository.resolve("refs/remotes/origin/master"))
                }
                assertTrue(File(root, "requests").listFiles().orEmpty().isEmpty())
            }
        } finally { factory.close(); server.stop(0); root.deleteRecursively() }
    }
}
