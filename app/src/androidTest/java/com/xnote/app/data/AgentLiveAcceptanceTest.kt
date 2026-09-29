package com.xnote.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.xnote.app.data.agent.*
import com.xnote.app.data.db.*
import com.xnote.app.domain.agent.*
import com.xnote.app.domain.document.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

// -- Tests

class AgentLiveAcceptanceTest {
    @Test fun openAIRealServiceVerifiesFilesSummariesMemoryAndOutput() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = File(context.getExternalFilesDir(null), "xnote-live.env")
        assumeTrue("真实服务验收需要显式传入 live=1 和临时配置文件", InstrumentationRegistry.getArguments().getString("live") == "1" && config.isFile)
        val values = config.readLines().take(3).associate { it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim() }
        val database = XNoteDatabase.createInMemory(context)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val profiles = ModelProfileStore(database, AndroidModelCredentialStore(context))
        val directory = File(context.cacheDir, "agent-live-${System.nanoTime()}").apply { mkdirs() }
        val files = AgentFileStore(database, context, directory)
        val client = HttpModelClient()
        try {
            val profile = profiles.save(ModelProfile("live-acceptance", name = "OpenAI 真实验收", protocol = ModelProtocol.OpenAI,
                baseUrl = values.getValue("baseurl"), modelId = values.getValue("model"), isDefault = true,
                contextTokens = 65536, outputTokens = 8192),
                values.entries.single { it.key.endsWith("api_key") }.value.trim('"', '\''))
            val connection = ModelCapabilityTest(client, profiles).test(profile)
            assertTrue(connection.detail, connection.capabilities.textStreaming)
            println("LIVE: text streaming and tool round trip passed")
            val media = ModelAttachmentCapabilityTest(client, profiles).test(profiles.active())
            println("LIVE: image verified=${media.capabilities.images}; native PDF verified=${media.capabilities.pdf}")
            assertNotNull(media.capabilities.attachmentsTestedAtEpochMs)
            val timeline = AgentTimeline(database, profiles, client, scope, fileStore = files)
            timeline.awaitReady()
            files.importBytes("验收行程.md", "text/markdown", "# 验收行程\n2026年10月3日乘高铁去杭州，10月4日返程。".toByteArray())
            timeline.send("请读取附加文件，并调用 output_file 生成名为 行程.md 的 Markdown 文件，保留日期、目的地和交通方式。完成后简短告知。")
            awaitComplete(timeline, database)
            val output = database.agentFiles().all().single { it.origin == "output" }
            assertTrue(output.text.contains("杭州")); assertTrue(output.text.contains("高铁"))
            println("LIVE: Markdown input and output_file passed")
            timeline.newTopic()
            val episodes = AgentEpisodeStore(database)
            episodes.process(profiles, client)
            assertTrue("真实片段摘要必须成功发布", database.memory().episodes().isNotEmpty())
            println("LIVE: strict structured episode summary passed")
            AgentPermissionStore(database).saveFromUser(AgentPermission(AgentPermissionMode.FullAccess))
            val body = "项目验收计划：周一核对需求，周二运行回归，周三整理验收证据。负责人按记录逐项核对。".repeat(50)
            database.notes().upsert(NoteEntity("live-note", null, "项目验收计划", NoteDocument(blocks = listOf(TextBlock("body", inlines = listOf(InlineRun(body))))).encodeToJson(), null, 0, 0, 0, body, 1, 1, null, null))
            var clock = System.currentTimeMillis()
            val notes = AgentNoteMemoryStore(database) { clock }
            notes.sweep(profiles); clock += AgentNoteMemoryLimits.StableMs
            notes.process(profiles, client)
            assertEquals("complete", database.noteMemory().get("live-note")?.status)
            println("LIVE: strict structured note summary passed")
            timeline.send("请必须调用 memory_search 搜索关键词 高铁，再根据返回的 episodeId 调用 memory_read 读取原文，核对我之前的行程，简短回答出发日期和目的地。")
            awaitComplete(timeline, database)
            val lastRun = database.agent().messages().last { it.role == AgentMessageRole.User }.runId!!
            val events = database.agent().toolEvents(lastRun)
            println("LIVE: history tools=" + events.joinToString { it.name + ":" + it.status })
            assertTrue(events.any { it.name == "memory_search" && it.status == AgentToolStatus.Committed })
            assertTrue(events.any { it.name == "memory_read" && it.status == AgentToolStatus.Committed })
            assertTrue(database.agent().messages().last { it.role == AgentMessageRole.Assistant }.text.contains("杭州"))
            println("LIVE: memory search/read tool round trip passed")
        } finally {
            scope.coroutineContext[Job]?.cancelAndJoin()
            database.agent().unfinishedRuns().forEach { database.agent().saveRun(it.copy(status = AgentRunStatus.Cancelled)) }
            profiles.list().forEach { profiles.delete(it.id) }
            database.close(); directory.deleteRecursively(); config.delete()
        }
    }

    // -- Functions

    private suspend fun awaitComplete(timeline: AgentTimeline, database: XNoteDatabase) {
        withTimeout(240_000) { timeline.state.first { it.ready && !it.running } }
        val runId = database.agent().messages().last { it.role == AgentMessageRole.User }.runId!!
        val run = database.agent().run(runId)!!
        println("LIVE: run=${run.status}; inputTokens=${run.inputTokens}; outputTokens=${run.outputTokens}; requests=" +
            database.agent().messages().count { it.runId == runId && it.role == AgentMessageRole.Event && it.text == "模型请求" })
        assertEquals("真实运行必须正常结束：${database.agent().run(runId)?.errorCode}", AgentRunStatus.Complete, database.agent().run(runId)?.status)
    }
}
