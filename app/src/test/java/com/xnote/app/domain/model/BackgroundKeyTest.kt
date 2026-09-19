package com.xnote.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// -- Tests

class BackgroundKeyTest {
    @Test
    fun keysRoundTripThroughStorageEncoding() {
        val builtin = BackgroundKey.Builtin(RuledBuiltinBackgroundId)

        assertEquals(builtin, parseBackgroundKey(builtin.encode()))
        val image = BackgroundKey.Image("photo-123")
        assertEquals(image, parseBackgroundKey(image.encode()))
        assertEquals(image, resolveBackgroundKey(image, builtin))
        assertNull(parseBackgroundKey("image:"))
        assertNull(parseBackgroundKey("image:   "))
        assertNull(parseBackgroundKey("unsupported"))
    }

    @Test
    fun imageMaskOpacityRoundTripsAndRejectsInvalidValues() {
        listOf(0, 37, 100).forEach { opacity ->
            val key = BackgroundKey.Image("photo-123", opacity)
            assertEquals(key, parseBackgroundKey(key.encode()))
        }
        listOf("image:-1:photo", "image:101:photo", "image:bad:photo", "image:50:").forEach {
            assertNull(parseBackgroundKey(it))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun imageMaskOpacityCannotExceedOneHundred() {
        BackgroundKey.Image("photo-123", 101)
    }

    @Test
    fun noteOverrideWinsAndNullContinuesToFollowTheDefault() {
        val default = BackgroundKey.Builtin(GridBuiltinBackgroundId)
        val override = BackgroundKey.Builtin(CreamBuiltinBackgroundId)

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
        val default = BackgroundKey.Builtin(GridBuiltinBackgroundId)

        assertEquals(
            default,
            resolveBackgroundKey(parseBackgroundKey("builtin:removed"), default),
        )
    }
}
