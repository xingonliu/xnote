package com.xnote.app.design

import org.junit.Assert.assertEquals
import org.junit.Test

// -- Tests

class XNoteTokensTest {
    @Test
    fun buttonSizeUsesCompactControlGeometry() {
        assertEquals(40.0f, XNoteButtonSize.value)
        assertEquals(XNoteMinimumTouchTarget, XNoteButtonSize)
        assertEquals(8.0f, XNoteButtonHorizontalPadding.value)
        assertEquals(15.0f, XNoteHeaderTopPadding.value)
        assertEquals(55.0f, XNoteHeaderHeight.value)
    }

    @Test
    fun overlayScrimFadeUsesThreeHundredMilliseconds() {
        assertEquals(300, XNoteOverlayScrimDurationMillis)
    }
}
