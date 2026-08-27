package com.xnote.app.domain.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

// -- Tests

class RecycleBinPolicyTest {
    @Test
    fun remainingDaysIsThirtyWhenJustDeleted() {
        val deletedAt = 1_000_000L
        val remaining = RecycleBinPolicy.remainingDays(deletedAt, deletedAt)
        assertEquals(30, remaining)
        assertFalse(RecycleBinPolicy.isExpired(deletedAt, deletedAt))
    }

    @Test
    fun remainingDaysDropsAfterFullDaysElapse() {
        val deletedAt = 1_000_000L
        val now = deletedAt + TimeUnit.DAYS.toMillis(10)
        assertEquals(20, RecycleBinPolicy.remainingDays(deletedAt, now))
    }

    @Test
    fun expiredAfterThirtyDays() {
        val deletedAt = 1_000_000L
        val now = RecycleBinPolicy.expireAt(deletedAt)
        assertEquals(0, RecycleBinPolicy.remainingDays(deletedAt, now))
        assertTrue(RecycleBinPolicy.isExpired(deletedAt, now))
    }
}
