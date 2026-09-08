package com.xnote.app.feature.reader

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import com.xnote.app.domain.document.*
import com.xnote.app.domain.model.Attachment
import com.xnote.app.domain.model.Note
import com.xnote.app.feature.notes.editor.toAnnotatedString
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// -- Type Definitions

sealed interface ReadingContent {
    data class TextLine(
        val layout: TextLayoutResult,
        val line: Int,
        val x: Float,
        val quoted: Boolean = false,
    ) : ReadingContent
    data class TableLine(val cells: List<TextLine?>, val columnWidth: Float, val first: Boolean, val last: Boolean) : ReadingContent
    data class Media(
        val attachmentId: String,
        val width: Float,
        val height: Float,
        val rotation: Float,
        val x: Float,
        val y: Float,
    ) : ReadingContent
}

// -- Functions

fun measureReadingUnits(
    notes: List<Note>,
    attachments: Map<String, Attachment>,
    measurer: TextMeasurer,
    typography: Typography,
    colors: ColorScheme,
    width: Int,
    pageHeight: Float,
    density: Float,
    untitled: String,
): List<ReadingUnit<ReadingContent>> = buildList<ReadingUnit<ReadingContent>> {
    val gap = 8f * density
    fun measure(text: AnnotatedString, style: TextStyle, availableWidth: Float) = measurer.measure(
        text, style.copy(color = colors.onBackground),
        constraints = Constraints.fixedWidth(availableWidth.toInt().coerceAtLeast(1)),
    )
    fun inline(runs: List<InlineRun>): AnnotatedString {
        val builder = AnnotatedString.Builder(runs.toAnnotatedString(colors.primary.copy(alpha = 0.28f), colors.primary))
        var offset = 0
        runs.forEach { run ->
            run.linkUrl?.let { builder.addStringAnnotation("URL", it, offset, offset + run.text.length) }
            offset += run.text.length
        }
        return builder.toAnnotatedString()
    }
    fun addText(note: Note, id: String, text: AnnotatedString, style: TextStyle, x: Float = 0f, quoted: Boolean = false) {
        val layout = measure(text, style, width - x)
        for (line in 0 until layout.lineCount) {
            add(ReadingUnit(note.id, id, layout.getLineStart(line),
                layout.getLineBottom(line) - layout.getLineTop(line) + if (line == layout.lineCount - 1) gap else 0f,
                ReadingContent.TextLine(layout, line, x, quoted)))
        }
    }
    for (note in notes) {
        addText(note, "title", AnnotatedString(note.title.ifBlank { untitled }), typography.headlineLarge)
        val labels = note.document.numberedLabels()
        for (block in note.document.blocks) {
            when (block) {
                is TextBlock -> {
                    val marker = when (block.listMarker) {
                        ListMarker.None -> ""
                        ListMarker.Bullet -> "• "
                        ListMarker.Dash -> "– "
                        ListMarker.Numbered -> "${labels[block.id] ?: 1}. "
                        ListMarker.Checklist -> if (block.checked) "☑ " else "☐ "
                    }
                    val style = when (block.paragraphStyle) {
                        ParagraphStyle.Body -> typography.bodyLarge
                        ParagraphStyle.Heading -> typography.headlineSmall
                        ParagraphStyle.Subheading -> typography.titleMedium
                        ParagraphStyle.Monospace -> typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)
                    }.copy(textAlign = when (block.alignment) {
                        TextAlignment.Left -> TextAlign.Start
                        TextAlignment.Center -> TextAlign.Center
                        TextAlignment.Right -> TextAlign.End
                    })
                    val indent = ((block.indent * 20 + if (block.quoted) 16 else 0) * density).coerceAtMost(width * 0.6f)
                    addText(note, block.id, AnnotatedString(marker) + inline(block.inlines), style, indent, block.quoted)
                }
                is TableBlock -> block.rows.forEachIndexed { rowIndex, row ->
                    val columnWidth = width.toFloat() / row.cells.size.coerceAtLeast(1)
                    val layouts = row.cells.map { measure(inline(it.inlines), typography.bodyMedium, columnWidth - gap * 2) }
                    val anchorLayout = layouts.maxByOrNull { it.lineCount }
                    val lineCount = anchorLayout?.lineCount ?: 1
                    for (line in 0 until lineCount) {
                        val cells = layouts.mapIndexed { column, layout ->
                            if (line < layout.lineCount) ReadingContent.TextLine(layout, line, column * columnWidth + gap) else null
                        }
                        val height = cells.filterNotNull().maxOfOrNull { it.layout.getLineBottom(line) - it.layout.getLineTop(line) } ?: gap
                        add(ReadingUnit(note.id, "${block.id}:row:$rowIndex", anchorLayout?.getLineStart(line) ?: 0, height + (if (line == 0) gap else 0f) + (if (line == lineCount - 1) gap else 0f),
                            ReadingContent.TableLine(cells, columnWidth, line == 0, line == lineCount - 1)))
                    }
                }
                is ImageBlock, is StickerBlock, is DrawingBlock -> {
                    val image = when (block) {
                        is ImageBlock -> block
                        is StickerBlock -> ImageBlock(block.id, block.attachmentId, scale = block.scale,
                            rotationDegrees = block.rotationDegrees, offsetX = block.offsetX, offsetY = block.offsetY)
                        is DrawingBlock -> ImageBlock(block.id, block.attachmentId)
                    }
                    val attachment = attachments[image.attachmentId]
                    val ratio = if (block is DrawingBlock) block.height / block.width
                        else (attachment?.heightPx ?: 240).toFloat() / (attachment?.widthPx ?: 320).coerceAtLeast(1)
                    val radians = Math.toRadians(image.rotationDegrees.toDouble())
                    val cosine = abs(cos(radians)).toFloat()
                    val sine = abs(sin(radians)).toFloat()
                    val imageWidth = minOf(width * 0.7f * image.scale, width / (cosine + ratio * sine))
                    val imageHeight = imageWidth * ratio
                    val boundsWidth = imageWidth * cosine + imageHeight * sine
                    val boundsHeight = imageWidth * sine + imageHeight * cosine + abs(image.offsetY) * density * 2
                    val fit = minOf(1f, (pageHeight - gap).coerceAtLeast(1f) / boundsHeight)
                    val space = ((width - boundsWidth) / 2).coerceAtLeast(0f)
                    add(ReadingUnit(note.id, block.id, 0, boundsHeight * fit + gap,
                        ReadingContent.Media(image.attachmentId, imageWidth * fit, imageHeight * fit,
                            image.rotationDegrees, (image.offsetX * density).coerceIn(-space, space) * fit,
                            image.offsetY * density * fit)))
                }
            }
        }
    }
}.map { it.copy(height = kotlin.math.ceil(it.height)) }
