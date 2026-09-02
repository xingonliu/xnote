package com.xnote.app.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import org.junit.Assert.assertEquals
import org.junit.Test

// -- Tests

class XNotePopupPositionTest {
    private val noInsets = XNotePopupSafeInsets(0, 0, 0, 0)

    @Test
    fun placesPopupBelowAndEndAlignedToAnchor() {
        val offset = calculatePopupOffset(
            hostWidth = 1_000,
            hostHeight = 1_000,
            popupWidth = 240,
            popupHeight = 200,
            anchorBoundsInRoot = Rect(500f, 300f, 620f, 360f),
            hostOriginInRoot = Offset.Zero,
            placement = XNotePopupPlacement.BelowEnd,
            safeInsets = noInsets,
            popupGap = 8,
            edgePadding = 8,
        )

        assertEquals(IntOffset(380, 368), offset)
    }

    @Test
    fun flipsPopupAboveWhenThereIsNotEnoughSpaceBelow() {
        val offset = calculatePopupOffset(
            hostWidth = 1_000,
            hostHeight = 700,
            popupWidth = 240,
            popupHeight = 240,
            anchorBoundsInRoot = Rect(500f, 580f, 620f, 640f),
            hostOriginInRoot = Offset.Zero,
            placement = XNotePopupPlacement.BelowEnd,
            safeInsets = noInsets,
            popupGap = 8,
            edgePadding = 8,
        )

        assertEquals(IntOffset(380, 332), offset)
    }

    @Test
    fun clampsPopupInsideSafeDrawingBounds() {
        val offset = calculatePopupOffset(
            hostWidth = 400,
            hostHeight = 800,
            popupWidth = 240,
            popupHeight = 200,
            anchorBoundsInRoot = Rect(350f, 40f, 398f, 88f),
            hostOriginInRoot = Offset.Zero,
            placement = XNotePopupPlacement.AboveStart,
            safeInsets = XNotePopupSafeInsets(16, 32, 16, 48),
            popupGap = 8,
            edgePadding = 8,
        )

        assertEquals(IntOffset(136, 96), offset)
    }
}
