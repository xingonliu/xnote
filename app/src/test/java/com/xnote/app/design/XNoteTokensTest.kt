package com.xnote.app.design

import org.junit.Assert.assertEquals
import org.junit.Test

// -- Tests

class XNoteTokensTest {
    @Test
    fun buttonSizeMatchesIosRegularControl() {
        assertEquals(44.0f, XNoteButtonSize.value)
        assertEquals(XNoteMinimumTouchTarget, XNoteButtonSize)
        assertEquals(12.0f, XNoteButtonHorizontalPadding.value)
    }

    @Test
    fun overlayScrimFadeUsesThreeHundredMilliseconds() {
        assertEquals(300, XNoteOverlayScrimDurationMillis)
    }
}
