package com.xnote.app

import android.content.Context
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.xnote.app.data.agent.*
import com.xnote.app.data.db.*
import com.xnote.app.data.files.AttachmentFileStore
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.design.XNoteTheme
import com.xnote.app.domain.agent.*
import com.xnote.app.domain.model.SystemEpochClock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.File

// -- Tests

class AgentFileFlowTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = XNoteDatabase.createInMemory(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val profiles = ModelProfileStore(database, AndroidModelCredentialStore(context))
    private val directory = File(context.cacheDir, "agent-file-ui-${System.nanoTime()}").apply { mkdirs() }
    private val files = AgentFileStore(database, context, directory)
    private val client = object : ModelClient {
        override fun stream(profile: ModelProfile, apiKey: String, request: ModelRequest) = flow {
            emitAgentFinish("已读取你附加的 Markdown 行程。")
        }
    }
    private val timeline = AgentTimeline(database, profiles, client, scope, fileStore = files)
    private val library = NoteLibrary(database, AttachmentFileStore(directory), SystemEpochClock)

    @After fun cleanup() = runBlocking {
        scope.coroutineContext[Job]?.cancelAndJoin()
        profiles.list().forEach { profiles.delete(it.id) }
        database.close(); directory.deleteRecursively(); Unit
    }

    @Test fun fileDraftPreviewSendAndMessagePreviewHaveSaveAndShareActions() {
        val id = runBlocking {
            timeline.awaitReady()
            profiles.save(ModelProfile("profile", name = "测试", protocol = ModelProtocol.OpenAI, modelId = "test", isDefault = true), "test-key")
            files.importBytes("周末行程.md", "text/markdown", "# 周末行程\n周六乘坐高铁，周日返程。".toByteArray())
        }
        compose.setContent { XNoteTheme(reduceMotion = true) { XNoteApp(library, modelProfiles = profiles, modelClient = client, agentTimeline = timeline) } }
        compose.onNodeWithContentDescription("Agent").performClick()
        compose.onNodeWithTag("agent-file-$id").performClick()
        compose.onNodeWithTag("agent-file-preview-text").assertTextContains("# 周末行程", substring = true)
        compose.onNodeWithText("保存文件", useUnmergedTree = true).assertIsEnabled()
        compose.onNodeWithText("分享文件", useUnmergedTree = true).assertIsEnabled()
        screenshot("agent-file-preview")
        compose.onNodeWithText("关闭", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("agent-send").assertIsEnabled().performClick()
        compose.waitUntil(5000) { runBlocking { database.agent().messages().any { it.role == AgentMessageRole.Assistant && it.status == AgentMessageStatus.Complete } } }
        compose.onNodeWithTag("agent-timeline").performScrollToNode(hasTestTag("agent-file-$id"))
        compose.onNodeWithTag("agent-file-$id").performClick()
        compose.onNodeWithTag("agent-file-preview-text").assertTextContains("周六乘坐高铁", substring = true)
    }

    // -- Functions

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val directory = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
