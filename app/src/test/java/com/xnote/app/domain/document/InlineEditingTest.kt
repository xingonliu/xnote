package com.xnote.app.domain.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// -- Tests

class InlineEditingTest {
    @Test
    fun coalesceMergesAdjacentRunsWithTheSameStyle() {
        val merged = listOf(
            InlineRun("你", bold = true),
            InlineRun("好", bold = true),
            InlineRun("世界"),
        ).coalesce()
        assertEquals(2, merged.size)
        assertEquals("你好", merged[0].text)
        assertTrue(merged[0].bold)
        assertEquals("世界", merged[1].text)
        assertFalse(merged[1].bold)
    }

    @Test
    fun replaceRangeInsertsStyledTextAndKeepsNeighbors() {
        val original = listOf(InlineRun("你好世界"))
        val updated = original.replaceRange(2, 2, InlineRun("，", bold = true))
        assertEquals("你好，世界", updated.plainText())
        assertEquals(3, updated.size)
        assertTrue(updated[1].bold)
        assertEquals("，", updated[1].text)
    }

    @Test
    fun mapRangeTogglesBoldOnTheSelectionOnly() {
        val original = listOf(InlineRun("abcdef"))
        val updated = original.mapRange(2, 4) { it.copy(bold = true) }
        assertEquals("ab", updated[0].text)
        assertFalse(updated[0].bold)
        assertEquals("cd", updated[1].text)
        assertTrue(updated[1].bold)
        assertEquals("ef", updated[2].text)
        assertFalse(updated[2].bold)
    }

    @Test
    fun marksAtUsesTheCharacterBeforeTheCaret() {
        val inlines = listOf(
            InlineRun("aa", bold = true),
            InlineRun("bb", italic = true),
        )
        assertTrue(inlines.marksAt(0).bold)
        assertTrue(inlines.marksAt(2).bold)
        assertTrue(inlines.marksAt(3).italic)
        assertTrue(inlines.marksAt(4).italic)
    }

    @Test
    fun findTextReplacementDetectsMiddleEdit() {
        val (start, end, inserted) = findTextReplacement("你好世界", "你好，世界")
        assertEquals(2, start)
        assertEquals(2, end)
        assertEquals("，", inserted)
    }

    @Test
    fun rangeHasMarkRequiresEveryRunInTheSelection() {
        val inlines = listOf(
            InlineRun("aa", bold = true),
            InlineRun("bb"),
        )
        assertTrue(inlines.rangeHasMark(0, 2, InlineMark.Bold))
        assertFalse(inlines.rangeHasMark(0, 4, InlineMark.Bold))
    }
}
