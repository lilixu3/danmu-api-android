package com.example.danmuapiapp.data.service

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.net.URI
import java.net.URLDecoder
import java.util.Locale

/** GitHub's HTML is a fallback transport, not Markdown and not a stable set of CSS classes. */
internal object AppReleaseHtmlParser {
    fun notes(html: String): String {
        val document = Jsoup.parse(html)
        val body = document.selectFirst("[data-test-selector=body-content]")
            ?: document.selectFirst(".release-entry .markdown-body, .release .markdown-body")
        if (body != null) return render(body).trim()
        // Metadata is only a last resort; never mix the rest of the release page into the notes.
        return document.selectFirst("meta[property=og:description], meta[name=twitter:description]")
            ?.attr("content")?.trim().orEmpty()
    }

    fun assets(html: String, repo: String, tag: String): List<AppUpdateService.ApkAsset> {
        val document = Jsoup.parse(html, "https://github.com/")
        val prefix = "/$repo/releases/download/$tag/"
        return document.select("a[href]").mapNotNull { anchor ->
            val raw = anchor.attr("href").trim()
            // Some URL-rewriting proxies prepend their own endpoint to official links.
            val official = raw.indexOf("https://github.com/").takeIf { it >= 0 }
                ?.let { raw.substring(it) } ?: anchor.absUrl("href")
            val uri = runCatching { URI(official) }.getOrNull() ?: return@mapNotNull null
            if (uri.scheme != "https" || uri.host != "github.com" || uri.port !in listOf(-1, 443) ||
                uri.userInfo != null || !uri.rawPath.startsWith(prefix)) return@mapNotNull null
            val encodedName = uri.rawPath.removePrefix(prefix)
            if (encodedName.isBlank() || '/' in encodedName) return@mapNotNull null
            val name = runCatching { URLDecoder.decode(encodedName.replace("+", "%2B"), "UTF-8") }
                .getOrNull() ?: return@mapNotNull null
            if (!name.lowercase(Locale.ROOT).endsWith(".apk") || '/' in name || '\\' in name) return@mapNotNull null
            val item = anchor.closest("li")
            val size = item?.select("span")?.asSequence()?.mapNotNull { parseSize(it.text()) }?.firstOrNull()
                ?: parseSize(item?.ownText().orEmpty()) ?: 0L
            AppUpdateService.ApkAsset(name, uri.toString(), size)
        }.distinctBy { it.url }
    }

    private fun parseSize(text: String): Long? {
        val match = Regex("""([0-9]+(?:\.[0-9]+)?)\s*(KiB|MiB|GiB|KB|MB|GB|B)""", RegexOption.IGNORE_CASE)
            .matchEntire(text.trim()) ?: return null
        val number = match.groupValues[1].toDoubleOrNull() ?: return null
        val factor = when (match.groupValues[2].uppercase(Locale.ROOT)) {
            "KB", "KIB" -> 1024.0
            "MB", "MIB" -> 1024.0 * 1024
            "GB", "GIB" -> 1024.0 * 1024 * 1024
            else -> 1.0
        }
        return (number * factor).toLong().coerceAtLeast(0)
    }

    private fun render(node: Node): String {
        if (node is TextNode) return node.text()
        if (node !is Element) return ""
        val tag = node.normalName()
        // Preserve code bytes and line breaks before ordinary HTML whitespace normalization.
        if (tag == "pre") {
            val content = (node.selectFirst("code") ?: node).wholeText().replace("\r\n", "\n").replace('\r', '\n')
            val longest = Regex("`+").findAll(content).maxOfOrNull { it.value.length } ?: 0
            val fence = "`".repeat(maxOf(3, longest + 1))
            return "\n\n${fence}text\n${content.trimEnd('\n')}\n$fence\n\n"
        }
        if (tag in setOf("script", "style", "svg", "clipboard-copy", "button", "img")) return ""
        val text = node.childNodes().joinToString("") { render(it) }
        return when (tag) {
            "h1", "h2", "h3", "h4", "h5", "h6" -> "\n\n${"#".repeat(tag.last().digitToInt())} ${text.trim()}\n\n"
            "p" -> "\n\n${text.trim()}\n\n"
            "li" -> "\n- ${text.trim()}\n"
            "ul", "ol" -> "\n${text.trim()}\n\n"
            "br" -> "  \n"
            "strong", "b" -> "**$text**"
            "em", "i" -> "*$text*"
            "code" -> {
                val fence = "`".repeat(maxOf(1, (Regex("`+").findAll(text).maxOfOrNull { it.value.length } ?: 0) + 1))
                "$fence $text $fence"
            }
            "a" -> {
                val href = node.attr("href")
                // Only links that the renderer already permits; encode Markdown delimiters.
                if (href.startsWith("https://") || href.startsWith("http://"))
                    "[${text.replace("[", "\\[").replace("]", "\\]")}](${href.replace("(", "%28").replace(")", "%29")})"
                else text
            }
            "div", "section", "blockquote" -> "\n${text.trim()}\n"
            else -> text
        }
    }
}
