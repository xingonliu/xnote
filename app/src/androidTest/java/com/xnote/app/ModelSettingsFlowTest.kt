package com.xnote.app

import android.content.Context
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
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
        compose.onNodeWithTag("model-advanced").performScrollTo().performClick()
        compose.onNodeWithTag("model-name").performScrollTo().performTextInput("测试配置")
        compose.onNodeWithTag("model-id").performScrollTo().performTextInput("test-model")
        compose.onNodeWithTag("model-key").performScrollTo().performTextInput("local-test-key")
        compose.onNodeWithTag("model-save").performScrollTo().assertIsEnabled()
            .performTouchInput { click() }
        compose.waitUntil(5000) { runBlocking { store.list().size == 1 } }
        compose.onNodeWithText("测试配置 · 默认").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("model-name").assertDoesNotExist()
        screenshot("model-settings-phone")
        compose.onNodeWithText("测试配置 · 默认").performClick()
        compose.onNodeWithText("测试连接").performScrollTo().performClick()
        compose.waitUntil(5000) { runBlocking { store.list().single().capabilities.textStreaming } }
        compose.onNodeWithText("文字流式通过；工具验证未通过。", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("model-advanced").performScrollTo().performClick()
        compose.onNodeWithTag("model-name").performScrollTo().performTextReplacement("重命名配置")
        compose.onNodeWithTag("model-key").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithTag("model-save").performScrollTo().assertIsEnabled()
            .performTouchInput { click() }
        compose.waitUntil(5000) { runBlocking { store.list().single().name == "重命名配置" } }
        compose.onNodeWithText("重命名配置 · 默认").performScrollTo().performClick()
        compose.onNodeWithText("删除").performScrollTo().performClick()
        compose.waitUntil(5000) { runBlocking { store.list().isEmpty() } }
        compose.onNodeWithText("还没有模型配置").performScrollTo().assertIsDisplayed()
    }

    @Test fun presetAutofillsAndReopensWithoutDependingOnCatalog() {
        val entry = CatalogModel("test-latest", "最新测试模型", "2026-09-23", 128000, 4096, CatalogProvider("openai", "OpenAI", ModelProtocol.OpenAI, "https://api.openai.com/v1"))
        var loads = 0
        val catalog = ModelCatalog {
            loads++
            if (loads > 1) throw java.io.IOException("offline")
            listOf(entry)
        }
        compose.setContent { XNoteTheme(reduceMotion = true) { ModelSettingsScreen(store, client, catalog) {} } }
        compose.onNodeWithTag("model-add").performClick()
        compose.onNodeWithTag("model-picker").assertTextContains(entry.name)
        compose.onNodeWithTag("model-name").assertDoesNotExist()
        compose.onNodeWithTag("model-context").assertDoesNotExist()
        compose.onNodeWithTag("model-preset-url").assertDoesNotExist()
        compose.onNodeWithTag("model-url").assertDoesNotExist()
        compose.onNodeWithTag("model-key").performScrollTo().performTextInput("local-test-key")
        screenshot("model-preset-phone")
        compose.onNodeWithTag("model-save").performScrollTo().assertIsEnabled()
            .performTouchInput { click() }
        compose.waitUntil(5000) { runBlocking { store.list().size == 1 } }
        val saved = runBlocking { store.list().single() }
        org.junit.Assert.assertEquals(entry.provider.baseUrl, saved.baseUrl)
        org.junit.Assert.assertEquals(entry.id, saved.modelId)
        org.junit.Assert.assertTrue(saved.usesPreset)
        org.junit.Assert.assertEquals(128000, saved.contextTokens)
        compose.onNodeWithText("最新测试模型 · 默认").performScrollTo().performClick()
        compose.onNodeWithText("模型列表获取失败，请重试或使用自定义配置").assertIsDisplayed()
        compose.onNodeWithTag("model-key").performScrollTo().assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithTag("model-save").performScrollTo().assertIsEnabled()
            .performTouchInput { click() }
        compose.waitUntil(5000) { runBlocking { store.list().single().version == 2L } }
    }

    @Test fun providerAndModelDropdownsSelectNativeProtocolAndClearCredentials() {
        val claude = CatalogProvider("anthropic", "Anthropic", ModelProtocol.Anthropic, "https://api.anthropic.com/v1")
        val fresh = CatalogProvider("new-provider", "新厂商", ModelProtocol.OpenAI, "https://new.example/v1")
        val entries = listOf(
            CatalogModel("test-claude", "Claude Test", "2026-09-23", 128000, 4096, claude),
            CatalogModel("test-new", "New Test", "2026-09-23", 128000, 4096, fresh),
            CatalogModel("test-small", "Small Test", "2026-09-22", 64000, 2048, fresh),
        ) + (1..50).map { index -> CatalogModel("test-$index", "Model $index", "2026-09-23", 64000, 2048,
            CatalogProvider("provider-$index", "厂商 $index", ModelProtocol.OpenAI, "https://provider-$index.example/v1")) }
        compose.setContent { XNoteTheme(reduceMotion = true) { ModelSettingsScreen(store, client, ModelCatalog { entries }) {} } }
        compose.onNodeWithTag("model-add").performClick()
        compose.onNodeWithTag("model-provider").assertTextContains("Anthropic")
        compose.onNodeWithTag("model-key").performScrollTo().performTextInput("wrong-provider-key")
        compose.onNodeWithTag("model-provider").performScrollTo().performClick()
        compose.onNodeWithText("厂商 50").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("select-search").performTextInput("new-provider")
        screenshot("model-provider-dropdown")
        compose.onNodeWithText("新厂商").performClick()
        compose.onNodeWithTag("model-key").performScrollTo().assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithTag("model-picker").performScrollTo().performClick()
        compose.onNodeWithTag("select-search").performTextInput("small")
        compose.onNodeWithText("Small Test · test-small").performClick()
        compose.onNodeWithTag("model-picker").assertTextContains("Small Test")
        compose.onNodeWithTag("model-key").performScrollTo().performTextInput("new-provider-test-key")
        screenshot("model-official-provider-phone")
        compose.onNodeWithTag("model-save").performScrollTo().assertIsEnabled()
            .performTouchInput { click() }
        compose.waitUntil(5000) { runBlocking { store.list().isNotEmpty() } }
        val saved = runBlocking { store.list().single() }
        org.junit.Assert.assertEquals(fresh.baseUrl, saved.baseUrl)
        org.junit.Assert.assertEquals("new-provider", saved.providerId)
        org.junit.Assert.assertEquals(ModelProtocol.OpenAI, saved.protocol)
        org.junit.Assert.assertEquals("test-small", saved.modelId)
        org.junit.Assert.assertEquals(64000, saved.contextTokens)
    }

    @Test fun catalogFailureCanRetryAndCancelCustomWithoutSaving() {
        var attempts = 0
        val catalog = ModelCatalog { attempts++; throw java.io.IOException("untrusted response") }
        compose.setContent { XNoteTheme(reduceMotion = true) { ModelSettingsScreen(store, client, catalog) {} } }
        compose.onNodeWithTag("model-add").performClick()
        compose.onNodeWithText("模型列表获取失败，请重试或使用自定义配置").assertIsDisplayed()
        compose.onNodeWithText("重试").performClick()
        compose.waitUntil(5000) { attempts == 2 }
        compose.onNodeWithTag("model-custom").performClick()
        compose.onNodeWithTag("model-url").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("取消", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithTag("model-add").assertIsDisplayed()
        org.junit.Assert.assertTrue(runBlocking { store.list().isEmpty() })
    }

    @Test fun validationToastAndSaveFailureAllowRetryWithoutLosingInput() {
        var failWrite = true
        val credentials = object : ModelCredentialStore {
            private val secrets = mutableMapOf<String, String>()
            override fun read(reference: String) = secrets.getValue(reference)
            override fun write(reference: String, secret: String) {
                if (failWrite) throw ModelException(ModelError.MissingCredential)
                secrets[reference] = secret
            }
            override fun delete(reference: String) { secrets.remove(reference) }
        }
        val failingStore = ModelProfileStore(database, credentials)
        val entry = CatalogModel("test-model", "测试模型", "", 128000, 4096,
            CatalogProvider("openai", "OpenAI", ModelProtocol.OpenAI, "https://api.openai.com/v1"))
        compose.setContent { XNoteTheme { ModelSettingsScreen(failingStore, client, ModelCatalog { listOf(entry) }) {} } }
        compose.onNodeWithTag("model-add").performTouchInput { click() }
        compose.onNodeWithTag("model-save").performScrollTo().performTouchInput { click() }
        compose.onNodeWithText("请填写 API Key").assertIsDisplayed()
        compose.onNodeWithTag("model-key").performScrollTo().performTextInput("  local-test-key  ")
        compose.onNodeWithTag("model-save").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.waitUntil(5000) { compose.onAllNodesWithText(ModelError.MissingCredential.display).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(ModelError.MissingCredential.display).assertIsDisplayed()
        compose.onNodeWithTag("model-save").assertIsEnabled()
        screenshot("model-save-error-toast")
        compose.runOnIdle { failWrite = false }
        compose.onNodeWithTag("model-save").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.waitUntil(5000) { runBlocking { failingStore.list().size == 1 } }
        compose.onNodeWithText("配置已保存").assertIsDisplayed()
        val profile = runBlocking { failingStore.list().single() }
        org.junit.Assert.assertEquals("local-test-key", runBlocking { failingStore.credential(profile) })
    }

    @Test fun invalidAdvancedCapacityShowsToastAndKeepsForm() {
        val entry = CatalogModel("test-model", "测试模型", "", 128000, 4096,
            CatalogProvider("openai", "OpenAI", ModelProtocol.OpenAI, "https://api.openai.com/v1"))
        compose.setContent { XNoteTheme(reduceMotion = true) { ModelSettingsScreen(store, client, ModelCatalog { listOf(entry) }) {} } }
        compose.onNodeWithTag("model-add").performClick()
        compose.onNodeWithTag("model-key").performTextInput("local-test-key")
        compose.onNodeWithTag("model-advanced").performScrollTo().performClick()
        compose.onNodeWithTag("model-context").performScrollTo().performTextReplacement("100")
        compose.onNodeWithTag("model-save").performScrollTo().performTouchInput { click() }
        compose.onNodeWithText("上下文容量应为 4096–2000000").assertIsDisplayed()
        org.junit.Assert.assertTrue(runBlocking { store.list().isEmpty() })
        compose.onNodeWithTag("model-context").performScrollTo().assertTextEquals("100")
    }

    // -- Functions

    private fun screenshot(name: String) {
        val directory = File(context.getExternalFilesDir(null), "s11-screenshots").apply { mkdirs() }
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(directory, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
