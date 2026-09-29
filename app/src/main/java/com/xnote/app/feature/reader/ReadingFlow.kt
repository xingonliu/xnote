package com.xnote.app.feature.reader

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import com.xnote.app.domain.document.*
import com.xnote.app.domain.model.Attachment
import com.xnote.app.feature.notes.editor.toAnnotatedString
import kotlin.math.ceil

// -- Functions

fun ReadingContent.mediaAttachmentIds(): List<String> = when (this) {
    is ReadingContent.Media -> listOf(attachmentId)
    is ReadingContent.Flow -> placements.flatMap { it.content.mediaAttachmentIds() }
    else -> emptyList()
}

fun measureReadingFlow(
    noteId: String, group: NoteFlowGroup, attachments: Map<String, Attachment>, measurer: TextMeasurer,
    typography: Typography, colors: ColorScheme, width: Int, pageHeight: Float, firstPageRemaining: Float,
    density: Float, labels: Map<String, Int>, lineHeightScale: Float,
): List<ReadingUnit<ReadingContent>> {
    val result = mutableListOf<ReadingUnit<ReadingContent>>()
    val gap = 8f * density
    val placements = mutableListOf<ReadingPlacement>()
    val exclusions = mutableListOf<Pair<Float, Float>>()
    var y = 0f
    var limit = firstPageRemaining.coerceAtLeast(1f)
    var anchorId = group.blocks.first().id
    var anchorOffset = 0
    fun bottom() = maxOf(y, placements.maxOfOrNull { it.top + it.height } ?: 0f)
    fun flush() {
        if (placements.isNotEmpty()) result += ReadingUnit(noteId, anchorId, anchorOffset, ceil(bottom()).coerceAtLeast(1f), ReadingContent.Flow(placements.toList()))
        placements.clear()
        exclusions.clear()
        y = 0f
        limit = pageHeight
    }
    for (block in group.blocks) {
        when (block) {
            is PlacedMediaBlock -> {
                val attachment = attachments[block.attachmentId]
                val ratio = (attachment?.heightPx ?: 240).toFloat() / (attachment?.widthPx ?: 320).coerceAtLeast(1)
                val original = block.geometry(width / density, ratio)
                val fit = minOf(1f, width / (original.boundsWidth * density), (pageHeight - gap) / (original.boundsHeight * density))
                val geometry = block.withPlacement(scale = block.scale * fit, offsetX = block.offsetX * fit, offsetY = block.offsetY * fit).geometry(width / density, ratio)
                val height = geometry.boundsHeight * density
                var top = (y + block.offsetY * density * fit).coerceAtLeast(0f)
                if (top + height + gap > limit) { flush(); top = (block.offsetY * density * fit).coerceIn(0f, (pageHeight - height - gap).coerceAtLeast(0f)) }
                if (placements.isEmpty()) { anchorId = block.id; anchorOffset = 0 }
                val halfWidth = geometry.boundsWidth * density / 2f
                val centerX = (geometry.centerX * density).coerceIn(halfWidth, maxOf(halfWidth, width - halfWidth))
                placements += ReadingPlacement(ReadingContent.Media(block.attachmentId, geometry.width * density, geometry.height * density,
                    block.rotationDegrees, centerX - width / 2f, 0f), top, height, 1 + block.zIndex)
                if (block.layout == MediaLayout.Wrap) exclusions += (centerX + halfWidth + gap) to (top + height + gap)
            }
            is TextBlock -> {
                val prefix = when (block.listMarker) {
                    ListMarker.None -> ""
                    ListMarker.Bullet -> "• "
                    ListMarker.Dash -> "– "
                    ListMarker.Numbered -> "${labels[block.id] ?: 1}. "
                    ListMarker.Checklist -> if (block.checked) "☑ " else "☐ "
                }
                val builder = AnnotatedString.Builder(prefix)
                builder.append(block.inlines.toAnnotatedString(colors.primary.copy(alpha = 0.28f), colors.primary))
                var linkOffset = prefix.length
                block.inlines.forEach { run ->
                    run.linkUrl?.let { builder.addStringAnnotation("URL", it, linkOffset, linkOffset + run.text.length) }
                    linkOffset += run.text.length
                }
                val text = builder.toAnnotatedString()
                val style = when (block.paragraphStyle) {
                    ParagraphStyle.Body -> typography.bodyLarge
                    ParagraphStyle.Heading -> typography.headlineSmall
                    ParagraphStyle.Subheading -> typography.titleMedium
                    ParagraphStyle.Monospace -> typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)
                }.let { it.copy(color = colors.onBackground, lineHeight = it.lineHeight * lineHeightScale, textAlign = when (block.alignment) {
                    TextAlignment.Left -> TextAlign.Start
                    TextAlignment.Center -> TextAlign.Center
                    TextAlignment.Right -> TextAlign.End
                }) }
                val indent = ((block.indent * 20 + if (block.quoted) 16 else 0) * density).coerceAtMost(width * 0.6f)
                var offset = 0
                do {
                    val active = exclusions.filter { it.second > y }
                    var left = maxOf(indent, active.maxOfOrNull { it.first } ?: 0f)
                    if (width - left < 80f * density) { y = maxOf(y, active.maxOfOrNull { it.second } ?: y); left = indent }
                    val layout = measurer.measure(text.subSequence(offset, text.length), style,
                        constraints = Constraints.fixedWidth((width - left).toInt().coerceAtLeast(1)), maxLines = 1)
                    val height = ceil(layout.getLineBottom(0) - layout.getLineTop(0))
                    if (y + height > limit && placements.isNotEmpty()) { flush(); continue }
                    if (y + height > limit) limit = pageHeight
                    if (placements.isEmpty()) { anchorId = block.id; anchorOffset = offset }
                    placements += ReadingPlacement(ReadingContent.TextLine(layout, 0, left, block.quoted), y, height)
                    y += height
                    val end = layout.getLineEnd(0).coerceAtLeast(1)
                    offset = (offset + end).coerceAtMost(text.length)
                } while (offset < text.length)
                y = minOf(limit, y + gap)
            }
            else -> error("Flow groups only contain placed media and text")
        }
    }
    flush()
    return result
}
