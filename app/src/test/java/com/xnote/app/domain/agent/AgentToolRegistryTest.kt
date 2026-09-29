package com.xnote.app.domain.agent

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

// -- Type Definitions

class AgentToolRegistryTest {
    // -- Functions

    @Test fun providerPermissionIsRecheckedBeforeDispatch() = runTest {
        var allowed = true
        var executions = 0
        val definition = ModelTool("service_lookup", "服务查询", buildJsonObject { put("type", "object") })
        val provider = AgentToolProvider { listOf(AgentToolRegistration(definition, { allowed }, { _, call ->
            executions++
            AgentToolResult.Finished(ModelToolResult(call.id, call.name, "{}"), emptyList())
        }, auditReason = "独立服务权限")) }
        val registry = AgentToolRegistry(listOf(provider))
        val context = AgentToolContext(AgentAccessContext(AgentPermission(AgentPermissionMode.FullAccess), "run", "segment"))
        assertEquals(listOf(definition), registry.definitions(context))
        allowed = false
        assertTrue(registry.execute(context, ModelToolCall("call", definition.name, buildJsonObject {})) is AgentToolResult.PermissionRequired)
        assertEquals(0, executions)
        assertTrue(registry.definitions(context).isEmpty())
    }

    @Test fun duplicateNamesCannotOverrideAnExistingTool() {
        val provider = AgentToolProvider { listOf(AgentToolRegistration(ModelTool("read", "读取", buildJsonObject {}), { true },
            { _, call -> AgentToolResult.PermissionRequired(call.id) }, auditReason = "读取授权")) }
        assertThrows(IllegalArgumentException::class.java) { AgentToolRegistry(listOf(provider, provider)) }
    }
}
