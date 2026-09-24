package com.xnote.app.domain.agent

// -- Constants

object AgentRunLimits {
    const val MaxRequests = 16
    const val MaxQueuedMessages = 50
    const val MaxRunDurationMs = 10 * 60 * 1000L
    const val MaxNetworkRetries = 2
    const val RetryDelayMs = 1000L
}
