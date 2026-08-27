package com.xnote.app.domain.rules

import java.util.concurrent.TimeUnit

// -- Constants

object RecycleBinPolicy {
    const val RetentionDays = 30

    private val RetentionMillis: Long = TimeUnit.DAYS.toMillis(RetentionDays.toLong())

    fun expireAt(deletedAtEpochMs: Long): Long = deletedAtEpochMs + RetentionMillis

    fun isExpired(deletedAtEpochMs: Long, nowMs: Long): Boolean = nowMs >= expireAt(deletedAtEpochMs)

    fun remainingDays(deletedAtEpochMs: Long, nowMs: Long): Int {
        val remainingMillis = expireAt(deletedAtEpochMs) - nowMs
        if (remainingMillis <= 0L) return 0
        return (remainingMillis / TimeUnit.DAYS.toMillis(1)).toInt()
            .coerceAtLeast(0)
    }
}
