package com.xnote.app.domain.model

// -- Type Definitions

fun interface EpochClock {
    fun nowMs(): Long
}

object SystemEpochClock : EpochClock {
    override fun nowMs(): Long = System.currentTimeMillis()
}
