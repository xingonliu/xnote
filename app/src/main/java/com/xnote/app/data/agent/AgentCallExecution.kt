package com.xnote.app.data.agent

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

// -- Type Definitions

/** Only the executor installs this capability, after its durable call approval check. */
internal class AgentCallExecution(val runId: String, val callId: String, val permissionRevision: Long) :
    AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<AgentCallExecution>
}
