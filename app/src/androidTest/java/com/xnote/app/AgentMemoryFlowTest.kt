package com.xnote.app

import android.content.Context
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.xnote.app.data.agent.*
import com.xnote.app.data.db.*
import com.xnote.app.design.XNoteTheme
import com.xnote.app.domain.agent.*
import com.xnote.app.feature.agent.AgentMemoryScreen
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

// -- Tests

class AgentMemoryFlowTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = XNoteDatabase.createInMemory(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val profiles = ModelProfileStore(database, AndroidModelCredentialStore(context))
    private val client = object : ModelClient {
        override fun stream(profile: ModelProfile, apiKey: String, request: ModelRequest) = flow<ModelEvent> { error("Memory management must not call the model") }
    }
    private val timeline = AgentTimeline(database, profiles, client, scope)

    @After fun cleanup() = runBlocking { scope.coroutineContext[Job]?.cancelAndJoin(); database.close() }

    @Test fun automaticSwitchCorrectionAndForgettingArePersistent() {
        runBlocking {
            timeline.awaitReady()
            database.profileMemory().saveFact(AgentProfileFactEntity("fact", "user.response.detail", "请简短回答", "Stated", 2, null,
                "用户要求简短回答", 0, "active", null, 1))
        }
        compose.setContent { XNoteTheme { AgentMemoryScreen(timeline, {}) } }
        compose.onNodeWithTag("automatic-memory").assertIsOn().performClick()
        compose.waitUntil(5000) { runBlocking { database.profileMemory().settings()?.automatic == false } }
        compose.onNodeWithText("请简短回答").assertExists()
        screenshot("profile-memory")
        compose.onNodeWithText("更正", useUnmergedTree = true).performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("请列出关键步骤")
        compose.onNodeWithText("保存", useUnmergedTree = true).performClick()
        compose.waitUntil(5000) { runBlocking { timeline.profileMemory.active().singleOrNull()?.value == "请列出关键步骤" } }
        compose.onNodeWithText("删除并停止记住", useUnmergedTree = true).performClick()
        compose.waitUntil(5000) { runBlocking { timeline.profileMemory.active().isEmpty() } }
        runBlocking { assertNotNull(database.profileMemory().forgotten("user.response.detail")) }
    }

    // -- Functions

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val directory = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
