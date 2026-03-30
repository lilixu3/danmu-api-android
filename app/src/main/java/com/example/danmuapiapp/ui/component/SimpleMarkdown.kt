package com.example.danmuapiapp.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class BulletList(val items: List<String>) : MarkdownBlock
    data class CodeBlock(val code: String) : MarkdownBlock
}

@Composable
fun SimpleMarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    maxLinesPerParagraph: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val blocks = parseSimpleMarkdown(markdown)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    Text(
                        text = block.text,
                        style = when (block.level) {
                            1 -> MaterialTheme.typography.titleMedium
                            2 -> MaterialTheme.typography.titleSmall
                            else -> MaterialTheme.typography.bodyMedium
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }

                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = block.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = maxLinesPerParagraph,
                        overflow = overflow
                    )
                }

                is MarkdownBlock.BulletList -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        block.items.forEach { item ->
                            Text(
                                text = "• $item",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = maxLinesPerParagraph,
                                overflow = overflow
                            )
                        }
                    }
                }

                is MarkdownBlock.CodeBlock -> {
                    Text(
                        text = block.code,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        softWrap = false
                    )
                }
            }
        }
    }
}

private fun parseSimpleMarkdown(markdown: String): List<MarkdownBlock> {
    val normalized = markdown.replace("\r\n", "\n").replace('\r', '\n')
    val lines = normalized.lines()
    val blocks = mutableListOf<MarkdownBlock>()
    val paragraph = mutableListOf<String>()
    val bullets = mutableListOf<String>()
    var inCodeBlock = false
    val codeLines = mutableListOf<String>()

    fun flushParagraph() {
        if (paragraph.isEmpty()) return
        val text = paragraph.joinToString("\n") { it.trimEnd() }.trim()
        if (text.isNotBlank()) blocks += MarkdownBlock.Paragraph(text)
        paragraph.clear()
    }

    fun flushBullets() {
        if (bullets.isEmpty()) return
        blocks += MarkdownBlock.BulletList(bullets.toList())
        bullets.clear()
    }

    fun flushCodeBlock() {
        if (codeLines.isEmpty()) return
        blocks += MarkdownBlock.CodeBlock(codeLines.joinToString("\n").trimEnd())
        codeLines.clear()
    }

    for (rawLine in lines) {
        val line = rawLine.trimEnd()
        val trimmed = line.trim()

        if (trimmed.startsWith("```")) {
            flushParagraph()
            flushBullets()
            if (inCodeBlock) {
                flushCodeBlock()
            }
            inCodeBlock = !inCodeBlock
            continue
        }

        if (inCodeBlock) {
            codeLines += line
            continue
        }

        if (trimmed.isBlank()) {
            flushParagraph()
            flushBullets()
            continue
        }

        val headingMatch = Regex("^(#{1,3})\\s+(.+)$").find(trimmed)
        if (headingMatch != null) {
            flushParagraph()
            flushBullets()
            blocks += MarkdownBlock.Heading(
                level = headingMatch.groupValues[1].length,
                text = headingMatch.groupValues[2].trim()
            )
            continue
        }

        val bulletMatch = Regex("^[-*]\\s+(.+)$").find(trimmed)
        if (bulletMatch != null) {
            flushParagraph()
            bullets += bulletMatch.groupValues[1].trim()
            continue
        }

        flushBullets()
        paragraph += line
    }

    if (inCodeBlock) flushCodeBlock()
    flushParagraph()
    flushBullets()
    return blocks
}
