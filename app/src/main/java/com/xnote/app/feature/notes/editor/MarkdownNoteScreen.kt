package com.xnote.app.feature.notes.editor

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.xnote.app.R
import com.xnote.app.design.XNoteMaximumContentWidth
import com.xnote.app.design.XNoteIconSizeMedium
import com.xnote.app.design.XNoteRadiusSmall
import com.xnote.app.design.XNoteSmoothCornerShape
import com.xnote.app.design.XNoteSpacingMedium
import com.xnote.app.design.XNoteSpacingSmall

// -- Type Definitions

private data class InlineToken(
    val opening: String,
    val closing: String,
    val style: (Color, Color, Color) -> SpanStyle,
)

// -- Composables

@Composable
internal fun MarkdownNoteScreen(
    session: NoteEditorSession,
    contentPadding: PaddingValues,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(contentPadding)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = XNoteMaximumContentWidth)
                .fillMaxWidth(),
        ) {
            when (session.markdownMode) {
                MarkdownEditorMode.Editing -> MarkdownSourceEditor(
                    value = session.markdownText,
                    onValueChange = session::updateMarkdownText,
                )
                MarkdownEditorMode.Preview -> MarkdownPreview(
                    markdown = session.markdownText,
                    modifier = Modifier.testTag("xnote-markdown-preview"),
                )
            }
        }
    }
}

@Composable
private fun MarkdownSourceEditor(
    value: String,
    onValueChange: (String) -> Unit,
) {
    val style = MaterialTheme.typography.bodyLarge.copy(
        color = MaterialTheme.colorScheme.onBackground,
        fontFamily = FontFamily.Monospace,
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = style,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 360.dp)
            .testTag("xnote-markdown-editor"),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        text = stringResource(R.string.editor_markdown_placeholder),
                        style = style,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                inner()
            }
        },
    )
}

@Composable
private fun MarkdownPreview(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val blocks = parseMarkdownPreview(markdown)
    if (blocks.isEmpty()) {
        Text(
            text = stringResource(R.string.editor_markdown_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.fillMaxWidth(),
        )
        return
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        blocks.forEach { block -> MarkdownPreviewBlock(block) }
    }
}

@Composable
private fun MarkdownPreviewBlock(block: MarkdownPreviewBlock) {
    when (block) {
        is MarkdownPreviewBlock.Heading -> Text(
            text = markdownInlineText(block.content),
            style = when (block.level) {
                1 -> MaterialTheme.typography.headlineLarge
                2 -> MaterialTheme.typography.headlineSmall
                else -> MaterialTheme.typography.titleMedium
            },
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth(),
        )
        is MarkdownPreviewBlock.Paragraph -> Text(
            text = markdownInlineText(block.content),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth(),
        )
        is MarkdownPreviewBlock.Quote -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                    shape = XNoteSmoothCornerShape(XNoteRadiusSmall),
                )
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
                    shape = XNoteSmoothCornerShape(XNoteRadiusSmall),
                )
                .padding(XNoteSpacingMedium),
        ) {
            Text(
                text = markdownInlineText(block.content),
                style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        is MarkdownPreviewBlock.Bullet -> MarkdownListRow(block.indent, "•", block.content)
        is MarkdownPreviewBlock.Numbered -> MarkdownListRow(block.indent, "${block.number}.", block.content)
        is MarkdownPreviewBlock.Checklist -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = (block.indent * 20).dp),
            horizontalArrangement = Arrangement.spacedBy(XNoteSpacingSmall),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                painter = painterResource(
                    if (block.checked) R.drawable.ic_keyline_stroke_square_check else R.drawable.ic_keyline_stroke_square,
                ),
                contentDescription = null,
                tint = if (block.checked) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(XNoteIconSizeMedium),
            )
            Text(
                text = markdownInlineText(block.content),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
        }
        is MarkdownPreviewBlock.Code -> Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = XNoteSmoothCornerShape(XNoteRadiusSmall),
                )
                .padding(XNoteSpacingMedium),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            block.language?.let { language ->
                Text(
                    text = language,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = block.content,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        is MarkdownPreviewBlock.Table -> MarkdownTable(block)
    }
}

@Composable
private fun MarkdownListRow(indent: Int, marker: String, content: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (indent * 20).dp),
        horizontalArrangement = Arrangement.spacedBy(XNoteSpacingSmall),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = marker,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = markdownInlineText(content),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MarkdownTable(table: MarkdownPreviewBlock.Table) {
    val rows = listOf(table.header) + table.rows
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                shape = XNoteSmoothCornerShape(XNoteRadiusSmall),
            )
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        rows.forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                row.forEach { cell ->
                    Text(
                        text = markdownInlineText(cell.replace("<br>", "\n")),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (rowIndex == 0) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                color = if (rowIndex == 0) {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                } else {
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
                                },
                                shape = XNoteSmoothCornerShape(8.dp),
                            )
                            .padding(8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun markdownInlineText(source: String): AnnotatedString {
    val primary = MaterialTheme.colorScheme.primary
    val highlight = MaterialTheme.colorScheme.tertiaryContainer
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    return buildAnnotatedString {
        appendMarkdownInline(source, primary, highlight, codeBackground)
    }
}

// -- Functions

private fun AnnotatedString.Builder.appendMarkdownInline(
    source: String,
    linkColor: Color,
    highlightColor: Color,
    codeBackground: Color,
) {
    var index = 0
    while (index < source.length) {
        if (source[index] == '\\' && index + 1 < source.length) {
            append(source[index + 1])
            index += 2
            continue
        }
        if (source.startsWith("<br>", index, ignoreCase = true)) {
            append('\n')
            index += 4
            continue
        }
        val linkEnd = if (source[index] == '[') source.indexOf("](", index + 1) else -1
        if (linkEnd >= 0) {
            val destinationEnd = findUnescaped(source, ")", linkEnd + 2)
            if (destinationEnd >= 0) {
                withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                    appendMarkdownInline(source.substring(index + 1, linkEnd), linkColor, highlightColor, codeBackground)
                }
                index = destinationEnd + 1
                continue
            }
        }
        if (source[index] == '`') {
            val delimiterLength = source.substring(index).takeWhile { it == '`' }.length
            val delimiter = "`".repeat(delimiterLength)
            val end = findUnescaped(source, delimiter, index + delimiterLength)
            if (end >= 0) {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)) {
                    append(source.substring(index + delimiterLength, end).trimSingleCodePadding())
                }
                index = end + delimiterLength
                continue
            }
        }
        val token = inlineTokenAt(source, index)
        if (token != null) {
            val end = findUnescaped(source, token.closing, index + token.opening.length)
            if (end >= 0) {
                withStyle(token.style(linkColor, highlightColor, codeBackground)) {
                    val content = source.substring(index + token.opening.length, end)
                    if (token.opening == "`") {
                        append(content)
                    } else {
                        appendMarkdownInline(content, linkColor, highlightColor, codeBackground)
                    }
                }
                index = end + token.closing.length
                continue
            }
        }
        append(source[index])
        index += 1
    }
}

private fun inlineTokenAt(source: String, index: Int): InlineToken? = when {
    source.startsWith("***", index) -> InlineToken("***", "***") { _, _, _ ->
        SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
    }
    source.startsWith("**", index) -> InlineToken("**", "**") { _, _, _ -> SpanStyle(fontWeight = FontWeight.Bold) }
    source.startsWith("~~", index) -> InlineToken("~~", "~~") { _, _, _ -> SpanStyle(textDecoration = TextDecoration.LineThrough) }
    source.startsWith("==", index) -> InlineToken("==", "==") { _, highlight, _ -> SpanStyle(background = highlight) }
    source.startsWith("<u>", index, ignoreCase = true) -> InlineToken("<u>", "</u>") { _, _, _ -> SpanStyle(textDecoration = TextDecoration.Underline) }
    source.startsWith("*", index) -> InlineToken("*", "*") { _, _, _ -> SpanStyle(fontStyle = FontStyle.Italic) }
    source.startsWith("_", index) -> InlineToken("_", "_") { _, _, _ -> SpanStyle(fontStyle = FontStyle.Italic) }
    else -> null
}

private fun findUnescaped(source: String, target: String, start: Int): Int {
    var index = start
    while (index <= source.length - target.length) {
        if (source.startsWith(target, index) && (index == 0 || source[index - 1] != '\\')) return index
        index += 1
    }
    return -1
}

private fun String.trimSingleCodePadding(): String {
    return if (length >= 2 && startsWith(' ') && endsWith(' ')) substring(1, lastIndex) else this
}
