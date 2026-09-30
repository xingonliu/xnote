package com.xnote.app

import com.xnote.app.domain.agent.*
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

// -- Functions

suspend fun FlowCollector<ModelEvent>.emitAgentFinish(summary: String) {
    emit(ModelEvent.ToolCall(ModelToolCall("finish-${UUID.randomUUID()}", AgentFinishToolName, buildJsonObject { put("summary", summary) })))
    emit(ModelEvent.Finished(ModelFinish.ToolCalls))
}
