package com.xnote.app.domain.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// -- Tests

class SearchTextTest {
    @Test
    fun findsEveryCaseInsensitivePhraseMatch() {
        assertEquals(
            listOf(0..3, 5..8),
            searchMatchRanges("Plan plan", "plan"),
        )
    }

    @Test
    fun fallsBackToIndividualTermsWhenSpacingDiffers() {
        assertEquals(
            listOf(0..4, 7..11),
            searchMatchRanges("hello, world", "hello world"),
        )
    }

    @Test
    fun snippetKeepsTheMatchAndMarksTrimmedEdges() {
        val snippet = searchSnippet("前".repeat(80) + "笔记本" + "后".repeat(80), "笔记本", 40)
        assertTrue(snippet.startsWith("…"))
        assertTrue(snippet.endsWith("…"))
        assertTrue(snippet.contains("笔记本"))
    }
}
