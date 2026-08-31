package com.xnote.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// -- Tests

class BackgroundKeyTest {
    @Test
    fun keysRoundTripThroughStorageEncoding() {
        val builtin = BackgroundKey(RuledBuiltinBackgroundId)

        assertEquals(builtin, parseBackgroundKey(builtin.encode()))
        assertNull(parseBackgroundKey("attachment:removed"))
        assertNull(parseBackgroundKey("unsupported"))
    }

    @Test
    fun noteOverrideWinsAndNullContinuesToFollowTheDefault() {
        val default = BackgroundKey(GridBuiltinBackgroundId)
        val override = BackgroundKey(CreamBuiltinBackgroundId)

        assertEquals(
            default,
            resolveBackgroundKey(null, default),
        )
        assertEquals(
            override,
            resolveBackgroundKey(override, default),
        )
    }

    @Test
    fun unsupportedBuiltInFallsBackToTheCurrentDefault() {
        val default = BackgroundKey(GridBuiltinBackgroundId)

        assertEquals(
            default,
            resolveBackgroundKey(parseBackgroundKey("builtin:removed"), default),
        )
    }
}
