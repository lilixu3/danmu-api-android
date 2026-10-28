package com.example.danmuapiapp.ui.component

import org.intellij.markdown.IElementType
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.parser.MarkdownParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownGfmContractTest {
    @Test
    fun `renderer parser recognizes common github extensions`() {
        val source = """
            | 状态 | 说明 |
            | --- | --- |
            | 完成 | ~~旧实现~~ 新实现 |

            - [x] 已完成
            - [ ] 待处理

            https://github.com/owner/repo/pull/1
        """.trimIndent()

        val root = MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString(source)
        val types = root.collectTypes()

        assertTrue(GFMElementTypes.TABLE in types)
        assertTrue(GFMElementTypes.STRIKETHROUGH in types)
        assertTrue(GFMTokenTypes.CHECK_BOX in types)
        assertTrue(GFMTokenTypes.GFM_AUTOLINK in types)
    }

    @Test
    fun `long github table keeps every row and cell for mobile layout`() {
        val source = """
            | 场景 | 行为变化 |
            | --- | --- |
            | 仅 renren 部署 | **整次请求不触碰 Bangumi Data，零下载**（原版即便无关也触发判定） |
            | 冷启动或过期刷新 | 仍触发下载；边缘运行时在途刷新经 `waitUntil` 延长，完整说明不应被截断 |
            | 前端由关到开 `USE_BANGUMI_DATA` | **立即触发下载**，并同步更新运行时状态 |
        """.trimIndent()

        val root = MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString(source)
        val table = root.findFirst(GFMElementTypes.TABLE)
        assertNotNull(table)

        val layout = markdownTableLayout(requireNotNull(table))
        assertNotNull(layout)
        requireNotNull(layout)
        assertEquals(2, layout.columnCount)
        assertEquals(3, layout.bodyRows.size)
        assertTrue(layout.bodyRows.all { row ->
            row.children.count { it.type == GFMTokenTypes.CELL } == 2
        })
    }

    @Test
    fun `github CRLF body keeps tables after line ending normalization`() {
        val source = listOf(
            "> 该说明位于表格之前",
            "",
            "---",
            "",
            "## 影响范围",
            "",
            "| 场景 | 行为变化 |",
            "| --- | :---: |",
            "| 冷启动 | 自动刷新 |",
            "",
            "## 涉及文件",
            "",
            "| 文件 | 说明 |",
            "| --- | --- |",
            "| `src/index.js` | 更新缓存逻辑 |",
        ).joinToString("\r\n")

        val normalized = normalizeMarkdownLineEndings(source)
        val root = MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString(normalized)

        assertFalse(normalized.contains('\r'))
        assertEquals(2, root.count(GFMElementTypes.TABLE))
    }

    private fun ASTNode.collectTypes(): Set<IElementType> = buildSet {
        fun visit(node: ASTNode) {
            add(node.type)
            node.children.forEach(::visit)
        }
        visit(this@collectTypes)
    }

    private fun ASTNode.findFirst(type: IElementType): ASTNode? {
        if (this.type == type) return this
        children.forEach { child ->
            child.findFirst(type)?.let { return it }
        }
        return null
    }

    private fun ASTNode.count(type: IElementType): Int =
        (if (this.type == type) 1 else 0) + children.sumOf { it.count(type) }
}
