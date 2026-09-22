package com.xnote.app

import android.content.Context
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.xnote.app.data.agent.*
import com.xnote.app.data.db.XNoteDatabase
import com.xnote.app.design.XNoteTheme
import com.xnote.app.domain.agent.*
import com.xnote.app.feature.agent.ModelSettingsScreen
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.File

// -- Tests

class ModelSettingsFlowTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = XNoteDatabase.createInMemory(context)
    private val store = ModelProfileStore(database, AndroidModelCredentialStore(context))
    private val client = object : ModelClient {
        override fun stream(profile: ModelProfile, apiKey: String, request: ModelRequest) = flowOf(ModelEvent.Text("OK"), ModelEvent.Finished(ModelFinish.Complete))
    }

    @After fun cleanup() { runBlocking { store.list().forEach { store.delete(it.id) } }; database.close() }

    @Test fun addEditTestAndDeleteModelProfile() {
        compose.setContent { XNoteTheme(reduceMotion = true) { ModelSettingsScreen(store, client) {} } }
        compose.onNodeWithTag("model-add").performClick()
        compose.onNodeWithTag("model-name").performScrollTo().performTextInput("测试配置")
        compose.onNodeWithTag("model-id").performScrollTo().performTextInput("test-model")
        compose.onNodeWithTag("model-key").performScrollTo().performTextInput("local-test-key")
        compose.onNodeWithTag("model-save").performScrollTo().performClick()
        compose.waitUntil(5000) { runBlocking { store.list().size == 1 } }
        compose.onNodeWithText("测试配置 · 默认").performScrollTo().assertIsDisplayed()
        screenshot("model-settings-phone")
        compose.onNodeWithText("测试连接与能力").performScrollTo().performClick()
        compose.waitUntil(5000) { runBlocking { store.list().single().capabilities.textStreaming } }
        compose.onNodeWithText("文字流式：已验证 · 工具：未验证").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("编辑").performScrollTo().performClick()
        compose.onNodeWithTag("model-name").performScrollTo().performTextReplacement("重命名配置")
        compose.onNodeWithTag("model-key").assertTextEquals("")
        compose.onNodeWithTag("model-save").performScrollTo().performClick()
        compose.waitUntil(5000) { runBlocking { store.list().single().name == "重命名配置" } }
        compose.onNodeWithText("删除").performScrollTo().performClick()
        compose.waitUntil(5000) { runBlocking { store.list().isEmpty() } }
        compose.onNodeWithText("尚未配置模型。添加配置后可在 Agent 中发送文字。").performScrollTo().assertIsDisplayed()
    }

    // -- Functions

    private fun screenshot(name: String) {
        val directory = File(context.getExternalFilesDir(null), "s11-screenshots").apply { mkdirs() }
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(directory, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
