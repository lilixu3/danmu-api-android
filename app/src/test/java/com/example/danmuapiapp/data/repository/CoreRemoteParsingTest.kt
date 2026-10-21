package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.CoreSourceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreRemoteParsingTest {
    @Test
    fun `release JSON keeps empty fields and rejects malformed payloads`() {
        val release = CoreRemoteParsing.parseRelease(
            """{"tag_name":"v1.2.3","name":"core","body":"notes","published_at":"2026-10-01T00:00:00Z","zipball_url":"https://example/zip"}"""
        )
        requireNotNull(release)
        assertEquals("v1.2.3", release.tagName)
        assertEquals("notes", release.body)
        assertEquals("https://example/zip", release.zipballUrl)
        var parseError: Throwable? = null
        assertNull(CoreRemoteParsing.parseRelease("not json") { parseError = it })
        assertTrue(parseError != null)
        assertEquals("", CoreRemoteParsing.parseRelease("{}")!!.tagName)
    }

    @Test
    fun `release arrays drop entries without matching shape and stay null when empty`() {
        assertEquals(2, CoreRemoteParsing.parseReleaseArray("""[{"tag_name":"a"},{"tag_name":"b"}]""")?.size)
        assertNull(CoreRemoteParsing.parseReleaseArray("[]"))
        assertNull(CoreRemoteParsing.parseReleaseArray("{}"))
    }

    @Test
    fun `commit parsing uses first message line and full archive URL`() {
        val commit = CoreRemoteParsing.parseCommitAsRelease("owner/repo", kotlinx.serialization.json.buildJsonObject {
            put("sha", kotlinx.serialization.json.JsonPrimitive("a".repeat(40)))
            put("commit", kotlinx.serialization.json.buildJsonObject {
                put("message", kotlinx.serialization.json.JsonPrimitive("fix search\n\ndetail"))
                put("author", kotlinx.serialization.json.buildJsonObject {
                    put("date", kotlinx.serialization.json.JsonPrimitive("2026-10-01T00:00:00Z"))
                })
            })
        })
        requireNotNull(commit)
        assertEquals("aaaaaaa", commit.tagName)
        assertEquals("fix search", commit.name)
        assertEquals("fix search\n\ndetail", commit.body)
        assertEquals("2026-10-01T00:00:00Z", commit.publishedAt)
        assertEquals("https://api.github.com/repos/owner/repo/zipball/${"a".repeat(40)}", commit.zipballUrl)
    }

    @Test
    fun `commit parsing falls back to SHA title and rejects blank SHA`() {
        val commit = CoreRemoteParsing.parseCommitAsRelease("owner/repo", kotlinx.serialization.json.buildJsonObject {
            put("sha", kotlinx.serialization.json.JsonPrimitive("bbbbbbb"))
        })
        assertEquals("提交 bbbbbbb", commit?.name)
        assertNull(CoreRemoteParsing.parseCommitAsRelease("owner/repo", kotlinx.serialization.json.buildJsonObject {
            put("sha", kotlinx.serialization.json.JsonPrimitive("  "))
        }))
    }

    @Test
    fun `atom parsing reads link and id commits and decodes XML entities`() {
        val xml = """
            <feed>
              <entry>
                <id>tag:github.com,2008:Repository/1/commit/abcdef1234567</id>
                <updated>2026-10-01T00:00:00Z</updated>
                <title>Fix &amp; retry &lt;search&gt;</title>
              </entry>
              <entry>
                <link href="https://github.com/owner/repo/commit/1234567890abcdef" />
                <updated>2026-10-02T00:00:00Z</updated>
                <title>Second</title>
              </entry>
            </feed>
        """.trimIndent()
        val parsed = CoreRemoteParsing.parseCommitAtom("owner/repo", xml)
        assertEquals(2, parsed.size)
        assertEquals("abcdef1", parsed[0].tagName)
        assertEquals("Fix & retry <search>", parsed[0].name)
        assertEquals("1234567", parsed[1].tagName)
        assertEquals("https://api.github.com/repos/owner/repo/zipball/1234567890abcdef", parsed[1].zipballUrl)
    }

    @Test
    fun `atom parsing skips entries without commit identifiers`() {
        val parsed = CoreRemoteParsing.parseCommitAtom(
            "owner/repo",
            "<feed><entry><title>no link</title></entry></feed>"
        )
        assertTrue(parsed.isEmpty())
        assertNull(CoreRemoteParsing.extractCommitSha("<entry></entry>"))
        assertEquals("<a>", CoreRemoteParsing.decodeXmlEntities("&lt;a&gt;"))
    }

    @Test
    fun `version labels accept version and SHA forms but ignore blanks`() {
        assertEquals("v1.2@abc1234", CoreRemoteParsing.buildLatestVersionLabel(" v1.2 ", "abc1234".padEnd(40, '0')))
        assertEquals("1.2", CoreRemoteParsing.buildLatestVersionLabel("1.2", ""))
        assertEquals("", CoreRemoteParsing.buildLatestVersionLabel(" ", "abc"))

        val versionAndSha = CoreRemoteParsing.parseAvailableVersionLabel("v1.2.3@ABCDEF0")
        assertEquals("1.2.3", versionAndSha.version)
        assertEquals("ABCDEF0", versionAndSha.commitSha)
        val shaOnly = CoreRemoteParsing.parseAvailableVersionLabel("abcdef123")
        assertEquals("", shaOnly.version)
        assertEquals("abcdef123", shaOnly.commitSha)
        assertEquals("", CoreRemoteParsing.parseAvailableVersionLabel(null).version)
    }

    @Test
    fun `commit comparison requires nonblank prefixes and ignores case`() {
        assertTrue(CoreRemoteParsing.commitShasEquivalent("ABCDEF1", "abcdef1"))
        assertTrue(CoreRemoteParsing.commitShasEquivalent("abcdef123456", "abcdef1"))
        assertTrue(CoreRemoteParsing.commitShasEquivalent("abcdef1", "abcdef123456"))
        assertFalse(CoreRemoteParsing.commitShasEquivalent("", "abcdef1"))
        assertFalse(CoreRemoteParsing.commitShasEquivalent("zzz", "abcdef1"))
    }

    @Test
    fun `archive commit extraction supports refs and zip suffix`() {
        assertEquals(
            "abcdef1",
            CoreRemoteParsing.extractArchiveCommitSha("https://github.com/o/r/archive/abcdef1.zip")
        )
        assertEquals(
            "ABCDEF1234567890",
            CoreRemoteParsing.extractArchiveCommitSha("https://api.github.com/repos/o/r/zipball/ABCDEF1234567890")
        )
        assertNull(CoreRemoteParsing.extractArchiveCommitSha("https://github.com/o/r/archive/refs/heads/main.zip"))
    }

    @Test
    fun `source status distinguishes missing legacy mismatch and match`() {
        assertEquals(
            CoreSourceStatus.NotApplicable,
            CoreRemoteParsing.resolveCoreSourceStatus(null, "", "main")
        )
        assertEquals(
            CoreSourceStatus.UnknownLegacy,
            CoreRemoteParsing.resolveCoreSourceStatus(null, "owner/repo", "main")
        )
        val metadata = CoreSourceMetadata(repo = "owner/repo", branch = "release/main")
        assertEquals(
            CoreSourceStatus.Matched,
            CoreRemoteParsing.resolveCoreSourceStatus(metadata, "owner/repo", "main")
        )
        assertEquals(
            CoreSourceStatus.Mismatched,
            CoreRemoteParsing.resolveCoreSourceStatus(metadata, "owner/other", "main")
        )
        assertEquals(
            CoreSourceStatus.Mismatched,
            CoreRemoteParsing.resolveCoreSourceStatus(metadata, "owner/repo", "feature/x")
        )
        assertEquals(
            CoreSourceStatus.Matched,
            CoreRemoteParsing.resolveCoreSourceStatus(metadata, "owner/repo", null)
        )
    }

    @Test
    fun `branch matching accepts simple names as final path segments`() {
        assertTrue(CoreRemoteParsing.branchesEquivalent("release/main", "main"))
        assertTrue(CoreRemoteParsing.branchesEquivalent("feature/test", "TEST"))
        assertTrue(CoreRemoteParsing.branchesEquivalent("feature/deep/main", "main"))
        assertTrue(CoreRemoteParsing.branchesEquivalent("feature/deep/test", "test"))
    }
}
