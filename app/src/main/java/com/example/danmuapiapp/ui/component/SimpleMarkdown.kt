package com.example.danmuapiapp.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.annotator.annotatorSettings
import com.mikepenz.markdown.annotator.buildMarkdownAnnotatedString
import com.mikepenz.markdown.coil2.Coil2ImageTransformerImpl
import com.mikepenz.markdown.compose.components.MarkdownComponent
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeFence
import com.mikepenz.markdown.compose.elements.MarkdownTableBasicText
import com.mikepenz.markdown.compose.elements.material.MarkdownBasicText
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.elements.MarkdownCheckBox
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.ImageData
import com.mikepenz.markdown.model.ImageTransformer
import com.mikepenz.markdown.model.MarkdownState
import com.mikepenz.markdown.model.NoOpImageTransformerImpl
import com.mikepenz.markdown.model.markdownDimens
import com.mikepenz.markdown.model.markdownPadding
import com.mikepenz.markdown.model.rememberMarkdownState
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.flavours.gfm.GFMElementTypes.HEADER
import org.intellij.markdown.flavours.gfm.GFMElementTypes.ROW
import org.intellij.markdown.flavours.gfm.GFMTokenTypes.CELL

/**
 * App-wide GitHub-flavoured Markdown renderer.
 *
 * The historical name is kept because announcements, release notes and PR details already share
 * this API. Parsing and syntax highlighting happen off the main thread.
 */
@Composable
fun SimpleMarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    maxLinesPerParagraph: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    retainedMarkdownState: MarkdownState? = null,
) {
    if (markdown.isBlank()) return

    val compact = maxLinesPerParagraph != Int.MAX_VALUE
    val colorScheme = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val bodyStyle = type.bodyMedium.copy(color = colorScheme.onSurfaceVariant)
    val codeStyle = type.bodySmall.copy(
        color = colorScheme.onSurface,
        fontFamily = FontFamily.Monospace,
    )
    val normalizedMarkdown = remember(markdown) {
        normalizeMarkdownLineEndings(markdown)
    }
    val markdownState = retainedMarkdownState ?: rememberMarkdownState(
        content = normalizedMarkdown,
        retainState = true,
    )
    val platformUriHandler = LocalUriHandler.current
    val safeUriHandler = remember(platformUriHandler) {
        object : UriHandler {
            override fun openUri(uri: String) {
                if (MarkdownUriPolicy.canOpenLink(uri)) {
                    runCatching { platformUriHandler.openUri(uri) }
                }
            }
        }
    }
    val paragraph = compactParagraphComponent(maxLinesPerParagraph, overflow)
    val components = markdownComponents(
        codeFence = {
            MarkdownHighlightedCodeFence(
                content = it.content,
                node = it.node,
                style = it.typography.code,
                showHeader = true,
            )
        },
        codeBlock = {
            MarkdownHighlightedCodeBlock(
                content = it.content,
                node = it.node,
                style = it.typography.code,
                showHeader = true,
            )
        },
        paragraph = paragraph,
        table = {
            GithubMarkdownTable(it)
        },
        checkbox = {
            MarkdownCheckBox(it.content, it.node, it.typography.text)
        },
    )

    CompositionLocalProvider(LocalUriHandler provides safeUriHandler) {
        Markdown(
            markdownState = markdownState,
            modifier = modifier.fillMaxWidth(),
            colors = markdownColor(
                text = colorScheme.onSurfaceVariant,
                codeBackground = colorScheme.surfaceContainerHighest,
                inlineCodeBackground = colorScheme.surfaceContainerHighest,
                dividerColor = colorScheme.outlineVariant,
                tableBackground = colorScheme.surfaceContainerLow,
            ),
            typography = markdownTypography(
                h1 = type.titleLarge.copy(
                    color = colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                ),
                h2 = type.titleMedium.copy(
                    color = colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                ),
                h3 = type.titleSmall.copy(
                    color = colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                ),
                h4 = type.bodyLarge.copy(
                    color = colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                ),
                h5 = type.bodyMedium.copy(
                    color = colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                ),
                h6 = type.bodySmall.copy(
                    color = colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                ),
                text = bodyStyle,
                paragraph = bodyStyle,
                ordered = bodyStyle,
                bullet = bodyStyle,
                list = bodyStyle,
                quote = bodyStyle.copy(
                    color = colorScheme.onSurface.copy(alpha = 0.88f),
                ),
                code = codeStyle,
                inlineCode = bodyStyle.copy(
                    color = colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                ),
                textLink = TextLinkStyles(
                    style = bodyStyle.copy(
                        color = colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        textDecoration = TextDecoration.Underline,
                    ).toSpanStyle(),
                ),
                table = type.bodySmall.copy(color = colorScheme.onSurface),
            ),
            padding = markdownPadding(
                block = 3.dp,
                list = 2.dp,
                listItemTop = 2.dp,
                listItemBottom = 2.dp,
                listIndent = 10.dp,
            ),
            dimens = markdownDimens(
                codeBackgroundCornerSize = 6.dp,
            ),
            imageTransformer = if (compact) {
                NoOpImageTransformerImpl()
            } else {
                SafeCoilImageTransformer
            },
            components = components,
            loading = { Box(it) },
            error = {
                Text(
                    text = markdown,
                    modifier = it,
                    style = bodyStyle,
                    maxLines = maxLinesPerParagraph,
                    overflow = overflow,
                )
            },
        )
    }
}

@Composable
internal fun rememberSimpleMarkdownState(markdown: String): MarkdownState {
    val normalizedMarkdown = remember(markdown) {
        normalizeMarkdownLineEndings(markdown)
    }
    return rememberMarkdownState(
        content = normalizedMarkdown,
        retainState = true,
    )
}

internal fun normalizeMarkdownLineEndings(markdown: String): String =
    if ('\r' in markdown) {
        markdown.replace("\r\n", "\n").replace('\r', '\n')
    } else {
        markdown
    }

@Composable
private fun GithubMarkdownTable(model: MarkdownComponentModel) {
    val layout = remember(model.node) { markdownTableLayout(model.node) } ?: return

    val rows = remember(layout) { listOf(layout.header) + layout.bodyRows }
    val scrollState = rememberScrollState()
    val colorScheme = MaterialTheme.colorScheme
    val borderColor = colorScheme.outlineVariant
    val shape = RoundedCornerShape(6.dp)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, borderColor, shape)
    ) {
        val tableWidth = maxOf(maxWidth, (layout.columnCount * 148).dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
        ) {
            Column(modifier = Modifier.width(tableWidth)) {
                rows.forEachIndexed { rowIndex, row ->
                    GithubMarkdownTableRow(
                        model = model,
                        row = row,
                        columnCount = layout.columnCount,
                        isHeader = rowIndex == 0,
                        alternate = rowIndex > 0 && rowIndex % 2 == 0,
                        borderColor = borderColor,
                    )
                    if (rowIndex < rows.lastIndex) {
                        HorizontalDivider(color = borderColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun GithubMarkdownTableRow(
    model: MarkdownComponentModel,
    row: ASTNode,
    columnCount: Int,
    isHeader: Boolean,
    alternate: Boolean,
    borderColor: androidx.compose.ui.graphics.Color,
) {
    val colorScheme = MaterialTheme.colorScheme
    val cells = remember(row) { row.children.filter { it.type == CELL } }
    val background = when {
        isHeader -> colorScheme.surfaceContainerHighest
        alternate -> colorScheme.surfaceContainerLow
        else -> colorScheme.surface
    }
    val style = model.typography.table.copy(
        color = colorScheme.onSurface,
        fontWeight = if (isHeader) FontWeight.SemiBold else FontWeight.Normal,
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(background)
    ) {
        repeat(columnCount) { columnIndex ->
            if (columnIndex > 0) {
                VerticalDivider(
                    modifier = Modifier.fillMaxHeight(),
                    color = borderColor,
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 10.dp, vertical = 9.dp)
            ) {
                cells.getOrNull(columnIndex)?.let { cell ->
                    MarkdownTableBasicText(
                        content = model.content,
                        cell = cell,
                        style = style,
                        maxLines = Int.MAX_VALUE,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
        }
    }
}

internal data class MarkdownTableLayout(
    val header: ASTNode,
    val bodyRows: List<ASTNode>,
    val columnCount: Int,
)

internal fun markdownTableLayout(node: ASTNode): MarkdownTableLayout? {
    val header = node.findChildOfType(HEADER) ?: return null
    val columnCount = header.children.count { it.type == CELL }
    if (columnCount == 0) return null
    return MarkdownTableLayout(
        header = header,
        bodyRows = node.children.filter { it.type == ROW },
        columnCount = columnCount,
    )
}

private fun compactParagraphComponent(
    maxLines: Int,
    overflow: TextOverflow,
): MarkdownComponent = if (maxLines == Int.MAX_VALUE) {
    com.mikepenz.markdown.compose.components.CurrentComponentsBridge.paragraph
} else {
    { model: MarkdownComponentModel ->
        val settings = annotatorSettings()
        val text = buildAnnotatedString {
            pushStyle(model.typography.paragraph.toSpanStyle())
            buildMarkdownAnnotatedString(
                content = model.content,
                node = model.node,
                annotatorSettings = settings,
            )
            pop()
        }
        MarkdownBasicText(
            text = text,
            style = model.typography.paragraph,
            maxLines = maxLines,
            overflow = overflow,
        )
    }
}

private object SafeCoilImageTransformer : ImageTransformer {
    @Composable
    override fun transform(link: String): ImageData? {
        if (!MarkdownUriPolicy.canLoadImage(link)) return null
        return Coil2ImageTransformerImpl.transform(link)
    }

    @Composable
    override fun intrinsicSize(painter: Painter): Size =
        Coil2ImageTransformerImpl.intrinsicSize(painter)
}
