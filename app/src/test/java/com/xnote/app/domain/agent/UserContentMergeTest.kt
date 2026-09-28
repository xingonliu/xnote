package com.xnote.app.domain.agent

import com.xnote.app.domain.document.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

// -- Tests

class UserContentMergeTest {
    @Test fun overlappingUserTextWinsWhileDistantAgentEditSurvives() {
        val base = content("abc😀defghi")
        val merged = mergeUserContent(base, content("aAc😀defghZ"), content("aUc😀defghi"))
        assertEquals("aUc😀defghZ", (merged.document.blocks.single() as TextBlock).inlines.plainText())
        assertTrue(mergeAgentContent(base, content("aUc😀defghi"), content("aAc😀defghZ")) is AgentContentMerge.Conflict)
    }

    @Test fun userFormattingWinsOnlyConflictingProperty() {
        val base = content("body")
        val original = base.document.blocks.single() as TextBlock
        val remote = base.copy(document = NoteDocument(blocks = listOf(original.copy(alignment = TextAlignment.Center, quoted = true))))
        val user = base.copy(document = NoteDocument(blocks = listOf(original.copy(alignment = TextAlignment.Right))))
        val merged = mergeUserContent(base, remote, user).document.blocks.single() as TextBlock
        assertEquals(TextAlignment.Right, merged.alignment)
        assertTrue(merged.quoted)
    }

    @Test fun editedUserBlockSurvivesConcurrentDeletionWithOtherAgentBlocks() {
        val base = content("old").copy(document = NoteDocument(blocks = listOf(block("a", "old"), block("b", "tail"))))
        val remote = base.copy(document = NoteDocument(blocks = listOf(block("b", "new tail"), block("c", "new block"))))
        val user = base.copy(document = NoteDocument(blocks = listOf(block("a", "user"), block("b", "tail"))))
        val result = mergeUserContent(base, remote, user)
        assertEquals(listOf("a", "b", "c"), result.document.blocks.map { it.id })
        assertEquals(listOf("user", "new tail", "new block"), result.document.blocks.filterIsInstance<TextBlock>().map { it.inlines.plainText() })
        assertEquals(remote, mergeUserContent(base, remote, base))
    }

    @Test fun userDeletionWinsAgainstAgentEditAndKeepsInsertedMedia() {
        val base = content("old")
        val user = base.copy(document = NoteDocument(blocks = listOf(ImageBlock("image", "attachment"))))
        assertEquals(user, mergeUserContent(base, content("remote"), user))
    }

    @Test fun tableConflictsKeepUserCellAndMergeOtherCell() {
        fun table(a: String, b: String) = AgentEditableContent("", NoteDocument(blocks = listOf(TableBlock("t", listOf(
            TableRow(listOf(TableCell(listOf(InlineRun(a))), TableCell(listOf(InlineRun(b))))))))))
        assertEquals(table("用户", "远端"), mergeUserContent(table("甲", "乙"), table("模型", "远端"), table("用户", "乙")))
    }

    @Test fun concurrentBlockReordersAndDeletionsKeepValidIdsAndUserAdditions() {
        val random = Random(418)
        val original = (0..5).map { block(it.toString(), "base $it") }
        fun value(blocks: List<TextBlock>) = AgentEditableContent("", NoteDocument(blocks = blocks))
        repeat(500) {
            val remote = original.filter { random.nextBoolean() }.shuffled(random) + block("remote", "remote")
            val local = original.filter { random.nextBoolean() }.shuffled(random).map {
                if (random.nextBoolean()) it.copy(inlines = listOf(InlineRun("user ${it.id}"))) else it
            } + block("user", "user")
            val merged = mergeUserContent(value(original), value(remote), value(local))
            val ids = merged.document.blocks.map { it.id }
            assertEquals(ids.distinct(), ids)
            assertTrue("user" in ids)
            assertTrue(merged.document.blocks.none { it.id in original.map { it.id } && it.id !in local.map { it.id } })
            local.filter { it.inlines.plainText().startsWith("user") }.forEach { changed ->
                assertEquals(changed, merged.document.blocks.find { it.id == changed.id })
            }
        }
    }

    // -- Functions

    private fun block(id: String, text: String) = TextBlock(id, inlines = listOf(InlineRun(text)))
    private fun content(text: String) = AgentEditableContent("", NoteDocument(blocks = listOf(block("body", text))))
}
