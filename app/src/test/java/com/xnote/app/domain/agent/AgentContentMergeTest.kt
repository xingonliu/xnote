package com.xnote.app.domain.agent

import com.xnote.app.domain.document.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

// -- Tests

class AgentContentMergeTest {
    @Test fun multipleSeparatedEditsAndUnicodeMergeAndReverseWithoutLosingUserText() {
        val base = content("甲😀乙丙丁戊己庚")
        val agent = content("A😀乙丙丁戊己Z")
        val user = content("甲😀乙用户输入丁戊己庚")
        val merged = merge(base, user, agent)
        assertEquals("A😀乙用户输入丁戊己Z", text(merged))
        assertEquals(user, merge(agent, merged, base))
    }

    @Test fun titleParagraphPropertiesAndSeparateStylesMergeIndependently() {
        val base = content("abcdef")
        val user = base.copy(title = "用户标题", document = NoteDocument(blocks = listOf(TextBlock("body", alignment = TextAlignment.Right,
            inlines = listOf(InlineRun("abc"), InlineRun("def", bold = true))))))
        val agent = base.copy(document = NoteDocument(blocks = listOf(TextBlock("body", listMarker = ListMarker.Checklist,
            inlines = listOf(InlineRun("abc", italic = true), InlineRun("def"))))))
        val merged = merge(base, user, agent)
        assertEquals("用户标题", merged.title)
        val block = merged.document.blocks.single() as TextBlock
        assertEquals(TextAlignment.Right, block.alignment)
        assertEquals(ListMarker.Checklist, block.listMarker)
        assertTrue(block.inlines.first().italic)
        assertTrue(block.inlines.last().bold)
    }

    @Test fun overlapAndDeleteVersusUserEditRejectEntireContent() {
        val base = content("abc")
        assertTrue(mergeAgentContent(base, content("aUc"), content("aAc").copy(title = "Agent标题")) is AgentContentMerge.Conflict)
        val extra = TextBlock("other", inlines = listOf(InlineRun("其它段落")))
        val original = base.copy(document = NoteDocument(blocks = base.document.blocks + extra))
        val user = content("用户正文").copy(document = NoteDocument(blocks = content("用户正文").document.blocks + extra))
        val deleted = original.copy(document = NoteDocument(blocks = listOf(extra)))
        assertTrue(mergeAgentContent(original, user, deleted) is AgentContentMerge.Conflict)
    }

    @Test fun insertedUserBlockAndUserMediaTransformSurviveAgentChanges() {
        val image = ImageBlock("image", "asset")
        val base = content("正文").copy(document = NoteDocument(blocks = content("正文").document.blocks + image))
        val userBlock = TextBlock("user", inlines = listOf(InlineRun("新增内容")))
        val user = base.copy(document = NoteDocument(blocks = base.document.blocks.take(1) + userBlock + image.copy(scale = 2f)))
        val agent = base.copy(document = NoteDocument(blocks = content("改写正文").document.blocks + image))
        val merged = merge(base, user, agent)
        assertEquals(listOf("body", "user", "image"), merged.document.blocks.map { it.id })
        assertEquals(image.copy(scale = 2f), merged.document.blocks.last())
        assertEquals(user, merge(agent, merged, base))
    }

    @Test fun tableCellEditsMergeButAmbiguousConcurrentStructureDoesNot() {
        val table = TableBlock("table", listOf(TableRow(listOf(TableCell(listOf(InlineRun("甲"))), TableCell(listOf(InlineRun("乙")))))))
        fun withTable(value: TableBlock) = AgentEditableContent("", NoteDocument(blocks = listOf(value)))
        val user = table.copy(rows = listOf(TableRow(listOf(TableCell(listOf(InlineRun("用户"))), table.rows[0].cells[1]))))
        val agent = table.copy(rows = listOf(TableRow(listOf(table.rows[0].cells[0], TableCell(listOf(InlineRun("Agent")))))))
        val merged = merge(withTable(table), withTable(user), withTable(agent)).document.blocks.single() as TableBlock
        assertEquals(listOf("用户", "Agent"), merged.rows[0].cells.map { it.inlines.plainText() })
        assertTrue(mergeAgentContent(withTable(table), withTable(user), withTable(table.copy(rows = table.rows + table.rows))) is AgentContentMerge.Conflict)
    }

    @Test fun sequenceDiffReconstructsTargetsAcrossRepeatedCharactersAndEmptyRanges() {
        val random = Random(712)
        repeat(1000) {
            val before = List(random.nextInt(30)) { random.nextInt(5) }
            val after = List(random.nextInt(30)) { random.nextInt(5) }
            val edits = requireNotNull(agentSequenceEdits(before, after))
            val result = before.toMutableList()
            edits.asReversed().forEach { edit ->
                result.subList(edit.start, edit.end).clear()
                result.addAll(edit.start, edit.replacement)
            }
            assertEquals(after, result)
        }
    }

    @Test fun distantSmallEditsInLongTextDoNotRequireQuadraticSpace() {
        val body = "中".repeat(10_000)
        assertEquals("A${body}用户${body}Z", text(merge(content("始${body}间${body}终"), content("始${body}用户${body}终"), content("A${body}间${body}Z"))))
    }

    // -- Functions

    private fun content(text: String) = AgentEditableContent("", NoteDocument(blocks = listOf(TextBlock("body", inlines = listOf(InlineRun(text))))))
    private fun text(value: AgentEditableContent) = (value.document.blocks.single() as TextBlock).inlines.plainText()
    private fun merge(base: AgentEditableContent, current: AgentEditableContent, proposed: AgentEditableContent) =
        (mergeAgentContent(base, current, proposed) as AgentContentMerge.Merged).content
}
