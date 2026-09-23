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
        compose.setContent { XNoteTheme(reduceMotion = true) { ModelSettingsScreen(store, client, ModelCatalog { emptyList() }) {} } }
        compose.onNodeWithTag("model-add").performClick()
        compose.onNodeWithTag("model-add").assertDoesNotExist()
        compose.onNodeWithTag("model-custom").performClick()
        compose.onNodeWithTag("model-name").performScrollTo().performTextInput("测试配置")
        compose.onNodeWithTag("model-id").performScrollTo().performTextInput("test-model")
        compose.onNodeWithTag("model-key").performScrollTo().performTextInput("local-test-key")
        compose.onNodeWithTag("model-save").performScrollTo().performClick()
        compose.waitUntil(5000) { runBlocking { store.list().size == 1 } }
        compose.onNodeWithText("测试配置 · 默认").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("model-name").assertDoesNotExist()
        screenshot("model-settings-phone")
        compose.onNodeWithText("测试配置 · 默认").performClick()
        compose.onNodeWithText("测试连接与能力").performScrollTo().performClick()
        compose.waitUntil(5000) { runBlocking { store.list().single().capabilities.textStreaming } }
        compose.onNodeWithText("文字流式：已验证 · 工具：未验证").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("model-name").performScrollTo().performTextReplacement("重命名配置")
        compose.onNodeWithTag("model-key").assertTextEquals("")
        compose.onNodeWithTag("model-save").performScrollTo().performClick()
        compose.waitUntil(5000) { runBlocking { store.list().single().name == "重命名配置" } }
        compose.onNodeWithText("重命名配置 · 默认").performScrollTo().performClick()
        compose.onNodeWithText("删除").performScrollTo().performClick()
        compose.waitUntil(5000) { runBlocking { store.list().isEmpty() } }
        compose.onNodeWithText("尚未配置模型。添加配置后可在 Agent 中发送文字。").performScrollTo().assertIsDisplayed()
    }

    @Test fun presetAutofillsAndReopensWithoutDependingOnCatalog() {
        val entry = CatalogModel("openai/test-latest", "最新测试模型", 100, 128000, 4096)
        var loads = 0
        val catalog = ModelCatalog {
            loads++
            if (loads > 1) throw java.io.IOException("offline")
            listOf(entry)
        }
        compose.setContent { XNoteTheme(reduceMotion = true) { ModelSettingsScreen(store, client, catalog) {} } }
        compose.onNodeWithTag("model-add").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("model-selected").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("model-selected").assertTextEquals(entry.id)
        compose.onNodeWithTag("model-preset-url").performScrollTo().assertTextEquals(OpenRouterBaseUrl)
        compose.onNodeWithTag("model-url").assertDoesNotExist()
        compose.onNodeWithTag("model-key").performScrollTo().performTextInput("local-test-key")
        screenshot("model-preset-phone")
        compose.onNodeWithTag("model-save").performScrollTo().performClick()
        compose.waitUntil(5000) { runBlocking { store.list().size == 1 } }
        val saved = runBlocking { store.list().single() }
        org.junit.Assert.assertEquals(OpenRouterBaseUrl, saved.baseUrl)
        org.junit.Assert.assertEquals(entry.id, saved.modelId)
        org.junit.Assert.assertTrue(saved.usesPreset)
        org.junit.Assert.assertEquals(128000, saved.contextTokens)
        compose.onNodeWithText("最新测试模型 · 默认").performScrollTo().performClick()
        compose.onNodeWithText("模型目录加载失败，请重试或使用自定义配置。").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("model-key").performScrollTo().assertTextEquals("")
        compose.onNodeWithTag("model-save").performScrollTo().performClick()
        compose.waitUntil(5000) { runBlocking { store.list().single().version == 2L } }
    }

    @Test fun catalogFailureCanRetryAndCancelCustomWithoutSaving() {
        var attempts = 0
        val catalog = ModelCatalog { attempts++; throw java.io.IOException("untrusted response") }
        compose.setContent { XNoteTheme(reduceMotion = true) { ModelSettingsScreen(store, client, catalog) {} } }
        compose.onNodeWithTag("model-add").performClick()
        compose.onNodeWithText("模型目录加载失败，请重试或使用自定义配置。").assertIsDisplayed()
        compose.onNodeWithText("刷新模型目录").performClick()
        compose.waitUntil(5000) { attempts == 2 }
        compose.onNodeWithTag("model-custom").performClick()
        compose.onNodeWithTag("model-url").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("取消", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithTag("model-add").assertIsDisplayed()
        org.junit.Assert.assertTrue(runBlocking { store.list().isEmpty() })
    }

    // -- Functions

    private fun screenshot(name: String) {
        val directory = File(context.getExternalFilesDir(null), "s11-screenshots").apply { mkdirs() }
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(directory, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
