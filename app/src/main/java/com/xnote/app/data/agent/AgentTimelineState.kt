package com.xnote.app.data.agent

import kotlinx.coroutines.flow.MutableStateFlow

// -- Type Definitions

data class AgentTimelineState(val ready: Boolean = false, val running: Boolean = false, val notice: String? = null)

// -- Functions

internal fun consumeAgentNotice(state: MutableStateFlow<AgentTimelineState>, notice: String): Boolean {
    while (true) {
        val current = state.value
        if (current.notice != notice) return false
        if (state.compareAndSet(current, current.copy(notice = null))) return true
    }
}
