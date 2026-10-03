package com.example.danmuapiapp.data.service

import org.junit.Assert.*
import org.junit.Test
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser

class AppReleaseHtmlParserTest {
    private fun fixture(name: String) = requireNotNull(javaClass.getResource("/app-update/$name")).readText()

    @Test fun `current GitHub HTML retains separate bullets and complete SHA256 code fence`() {
        val notes = AppReleaseHtmlParser.notes(fixture("release-body.html"))
        assertTrue(notes.startsWith("# 更新日志"))
        assertEquals(7, notes.lines().count { it.startsWith("- ") })
        assertTrue(notes.contains("[#43](https://github.com/lilixu3/danmu-api-android/issues/43)"))
        val code = notes.substringAfter("```text\n").substringBefore("\n```").lines()
        assertEquals(3, code.size)
        assertTrue(code.all { Regex("[a-f0-9]{64}  app-1\\.0\\.5\\.106-194-.+\\.apk").matches(it) })
        assertFalse(notes.contains("clipboard-copy"))
        assertFalse(notes.contains("&gt;"))
    }

    @Test fun `converted real release parses as seven list items and one code block in the app renderer`() {
        val notes = AppReleaseHtmlParser.notes(fixture("release-body.html"))
        val root = MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString(notes)
        fun count(node: ASTNode, type: org.intellij.markdown.IElementType): Int =
            (if (node.type == type) 1 else 0) + node.children.sumOf { count(it, type) }
        assertEquals(7, count(root, MarkdownElementTypes.LIST_ITEM))
        assertEquals(1, count(root, MarkdownElementTypes.CODE_FENCE))
    }

    @Test fun `nested containers cannot truncate notes and code newlines are preserved`() {
        val notes = AppReleaseHtmlParser.notes("""<div data-test-selector='body-content'><div><h1>日志</h1><ul><li>第一项 <strong>重点</strong></li><li>第二项<br>说明 &amp; A</li></ul></div><div><pre class='notranslate'><code>abc  one.apk
123  two.apk
</code></pre><p>最后一段 &lt;内容&gt;</p></div></div>""")
        assertTrue(notes.contains("- 第一项 **重点**\n"))
        assertTrue(notes.contains("- 第二项  \n说明 & A"))
        assertTrue(notes.contains("```text\nabc  one.apk\n123  two.apk\n```"))
        assertTrue(notes.contains("最后一段 <内容>"))
    }

    @Test fun `asset extraction uses release links rather than removed CSS classes`() {
        val assets = AppReleaseHtmlParser.assets(fixture("assets.html"), "lilixu3/danmu-api-android", "v1.0.5.106")
        assertEquals(listOf("arm64-v8a", "armeabi-v7a", "x86_64").map { "app-1.0.5.106-194-$it.apk" }, assets.map { it.name })
        assertTrue(assets.all { it.url == "https://github.com/lilixu3/danmu-api-android/releases/download/v1.0.5.106/${it.name}" })
        assertTrue(assets.all { it.size > 40 * 1024 * 1024 })
    }

    @Test fun `old asset markup quoted hrefs proxy prefixes and encoded names still work`() {
        val html = """<ul><li><a href='/owner/repo/releases/download/v1/app-arm64-v8a.apk'><span class='Truncate-text text-bold'>app-arm64-v8a.apk</span></a><span>40.5 MB</span></li><li><a href='https://proxy.invalid/https://github.com/owner/repo/releases/download/v1/app%2Buniversal.apk'>download</a><span>3 KiB</span></li></ul>"""
        val assets = AppReleaseHtmlParser.assets(html, "owner/repo", "v1")
        assertEquals(listOf("app-arm64-v8a.apk", "app+universal.apk"), assets.map { it.name })
        assertEquals((40.5 * 1024 * 1024).toLong(), assets[0].size)
        assertEquals(3072L, assets[1].size)
    }

    @Test fun `SHA ending in bytes token cannot be mistaken for asset size`() {
        val html = """<li><a href='/owner/repo/releases/download/v1/app-3GB-arm64-v8a.apk'>app-3GB-arm64-v8a.apk</a><span>sha256:aaaaaaaaaaaa12b</span><span>42.9 MiB</span></li>"""
        val assets = AppReleaseHtmlParser.assets(html, "owner/repo", "v1")
        assertEquals((42.9 * 1024 * 1024).toLong(), assets.single().size)
    }

    @Test fun `foreign hosts wrong tag encoded path separators and duplicate assets are rejected`() {
        val prefix = "https://github.com/owner/repo/releases/download/v1/"
        val html = listOf(prefix+"app.apk", prefix+"app.apk", prefix+"dir%2Fapp.apk", prefix+"dir%5Capp.apk", prefix+"archive.zip",
            "https://evil.invalid/owner/repo/releases/download/v1/app.apk", "https://github.com/owner/repo/releases/download/v2/app.apk",
            "https://github.com@evil.invalid/owner/repo/releases/download/v1/app.apk").joinToString("") { "<a href='$it'>file</a>" }
        assertEquals(listOf("app.apk"), AppReleaseHtmlParser.assets(html, "owner/repo", "v1").map { it.name })
    }

    @Test fun `metadata fallback reads content without leaking page navigation`() {
        assertEquals("first\nsecond & third", AppReleaseHtmlParser.notes("""<meta content="first
second &amp; third" property="og:description"><nav>noise</nav>"""))
        assertEquals("", AppReleaseHtmlParser.notes("<html><h1>Sign in</h1></html>"))
    }

    @Test fun `embedded backticks cannot end the SHA code fence`() {
        val notes = AppReleaseHtmlParser.notes("<div data-test-selector='body-content'><pre><code>a```b\nx</code></pre></div>")
        assertTrue(notes.startsWith("````text\n"))
        assertTrue(notes.endsWith("\n````"))
    }
}
