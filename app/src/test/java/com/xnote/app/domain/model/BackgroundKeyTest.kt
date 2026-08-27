package com.xnote.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// -- Tests

class BackgroundKeyTest {
    @Test
    fun keysRoundTripThroughStorageEncoding() {
        val builtin = BackgroundKey.Builtin(RuledBuiltinBackgroundId)
        val image = BackgroundKey.UserImage("attachment-1")

        assertEquals(builtin, parseBackgroundKey(builtin.encode()))
        assertEquals(image, parseBackgroundKey(image.encode()))
        assertNull(parseBackgroundKey("unsupported"))
    }

    @Test
    fun noteOverrideWinsAndNullContinuesToFollowTheDefault() {
        val default = BackgroundKey.Builtin(GridBuiltinBackgroundId)
        val override = BackgroundKey.Builtin(CreamBuiltinBackgroundId)

        assertEquals(
            default,
            resolveBackgroundKey(null, default.encode()),
        )
        assertEquals(
            override,
            resolveBackgroundKey(override.encode(), default.encode()),
        )
    }

    @Test
    fun unsupportedBuiltInFallsBackToTheCurrentDefault() {
        val default = BackgroundKey.Builtin(GridBuiltinBackgroundId)

        assertEquals(
            default,
            resolveBackgroundKey("builtin:removed", default.encode()),
        )
    }
}
