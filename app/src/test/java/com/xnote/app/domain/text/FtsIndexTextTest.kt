package com.xnote.app.domain.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// -- Tests

class FtsIndexTextTest {
    @Test
    fun splitsConsecutiveChineseCharacters() {
        assertEquals("我 的 笔 记 本", FtsIndexText.prepare("我的笔记本"))
    }

    @Test
    fun matchQueryUsesPhraseOfSplitCharacters() {
        assertEquals("\"笔 记 本\"", FtsIndexText.matchQuery("笔记本"))
    }

    @Test
    fun blankQueryIsRejected() {
        assertNull(FtsIndexText.matchQuery("   "))
    }
}
