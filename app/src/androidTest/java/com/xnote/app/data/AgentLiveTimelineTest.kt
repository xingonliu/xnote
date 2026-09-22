package com.xnote.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.xnote.app.data.agent.*
import com.xnote.app.data.db.XNoteDatabase
import com.xnote.app.domain.agent.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

// -- Tests

class AgentLiveTimelineTest {
    @Test fun configuredServicePersistsRealAndroidConversation() = runBlocking {
        val enabled = InstrumentationRegistry.getArguments().getString("xnoteLive") == "true"
        assumeTrue("仅显式开启时运行真实服务测试", enabled)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.noBackupFilesDir, "xnote-live-config")
        val values = file.readLines().take(3).associate { it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim() }
        file.delete()
        val db = XNoteDatabase.createInMemory(context)
        val profiles = ModelProfileStore(db, AndroidModelCredentialStore(context))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            profiles.save(ModelProfile("live", name = "真实文字验收", protocol = ModelProtocol.OpenAI, baseUrl = values.getValue("baseurl"),
                modelId = values.getValue("model"), outputTokens = 1024, isDefault = true), values.entries.single { it.key.endsWith("api_key") }.value.trim('"', '\''))
            val timeline = AgentTimeline(db, profiles, HttpModelClient(), scope)
            timeline.send("请用中文回复：连接成功。")
            val runs = withTimeout(200000) { timeline.runs.first { it.singleOrNull()?.status in setOf(AgentRunStatus.Complete, AgentRunStatus.Failed, AgentRunStatus.PausedBudget) } }
            assertEquals("真实服务未正常完成：${runs.single().errorCode}", AgentRunStatus.Complete, runs.single().status)
            val messages = db.agent().messages()
            assertEquals(2, messages.size)
            assertEquals(AgentMessageStatus.Complete, messages.last().status)
            assertTrue(messages.last().text.isNotBlank())
        } finally {
            scope.coroutineContext[Job]?.cancelAndJoin()
            db.agent().unfinishedRuns().forEach { db.agent().saveRun(it.copy(status = AgentRunStatus.Cancelled)) }
            profiles.delete("live")
            db.close()
            file.delete()
        }
    }
}
