package com.xnote.app

import android.content.Context
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.xnote.app.data.agent.*
import com.xnote.app.data.db.XNoteDatabase
import com.xnote.app.data.files.AttachmentFileStore
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.design.XNoteTheme
import com.xnote.app.domain.agent.*
import com.xnote.app.domain.model.SystemEpochClock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.File

// -- Tests

class AgentFlowTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = XNoteDatabase.createInMemory(context)
    private val profiles = ModelProfileStore(database, AndroidModelCredentialStore(context))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = object : ModelClient {
        override fun stream(profile: ModelProfile, apiKey: String, request: ModelRequest) = flow {
            emit(ModelEvent.Text("这是一条逐步保存的回复。"))
            delay(100)
            emit(ModelEvent.Text("你可以继续发送消息，也可以开始新话题。"))
            emit(ModelEvent.Usage(12, 24))
            emit(ModelEvent.Finished(ModelFinish.Complete))
        }
    }
    private val timeline = AgentTimeline(database, profiles, client, scope)
    private val library = NoteLibrary(database, AttachmentFileStore(File(context.cacheDir, "agent-flow")), SystemEpochClock)

    @After fun cleanup() = runBlocking {
        scope.coroutineContext[Job]?.cancelAndJoin()
        profiles.list().forEach { profiles.delete(it.id) }
        database.close()
    }

    @Test fun sendNavigateBackAndStartNewTopic() {
        runBlocking { profiles.save(ModelProfile("test", name = "测试", protocol = ModelProtocol.OpenAI, modelId = "test", isDefault = true), "test-key") }
        compose.setContent { XNoteTheme(reduceMotion = true) { XNoteApp(library, modelProfiles = profiles, modelClient = client, agentTimeline = timeline) } }
        // Match MainActivity's resize policy; the generic Compose host otherwise pans the window.
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).forEach {
                    if (it is androidx.activity.ComponentActivity) it.enableEdgeToEdge()
                    it.window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                }
        }
        compose.onNodeWithText("Agent").performClick()
        compose.waitUntil(5000) { timeline.state.value.ready }
        compose.onNodeWithTag("agent-input").performTextInput("帮我整理今天的想法")
        screenshot("agent-before-send")
        compose.onNodeWithTag("agent-send").assertIsEnabled().performClick()
        try {
            compose.waitUntil(5000) { runBlocking { database.agent().messages().lastOrNull()?.status == AgentMessageStatus.Complete } }
        } catch (error: Throwable) {
            screenshot("agent-send-failure")
            throw AssertionError("状态=${timeline.state.value}; 消息=${runBlocking { database.agent().messages().map { it.status } }}", error)
        }
        compose.onNodeWithText("已完成").assertExists()
        compose.onNodeWithTag("agent-input").assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5000) { compose.onAllNodesWithText("我的").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("我的").performClick()
        compose.onNodeWithText("Agent").performClick()
        compose.onNodeWithText("帮我整理今天的想法").assertExists()
        screenshot("agent-timeline")
        compose.onNodeWithTag("agent-new-topic").performClick()
        compose.waitUntil(5000) { runBlocking { database.agent().messages().any { it.role == AgentMessageRole.Event } } }
        compose.onNodeWithText("帮我整理今天的想法").assertExists()
    }

    // -- Functions

    private fun screenshot(name: String) {
        val directory = File(context.getExternalFilesDir(null), "s11-screenshots").apply { mkdirs() }
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(directory, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
