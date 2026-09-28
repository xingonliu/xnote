package com.xnote.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.test.core.app.ApplicationProvider
import com.xnote.app.data.agent.*
import com.xnote.app.data.db.*
import com.xnote.app.data.files.saveAgentFile
import com.xnote.app.data.files.agentFileShareIntent
import com.xnote.app.domain.agent.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

// -- Tests

class AgentFileStoreTest {
    @Test fun saveAndSharePreserveUtf8BytesThroughAndroidContentProviders() = runBlocking {
        fixture { _, _, files, _ ->
            val context = ApplicationProvider.getApplicationContext<Context>()
            val expected = "# 文件验收\n中文与 emoji 😀".toByteArray()
            files.importBytes("文件.md", "text/markdown", expected)
            val card = files.cards.first().single()
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "xnote-test-${System.nanoTime()}.md")
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/markdown")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Download/")
            }
            val target = requireNotNull(context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
            try {
                saveAgentFile(context, files, card, target)
                assertArrayEquals(expected, context.contentResolver.openInputStream(target)!!.use { it.readBytes() })
                val share = agentFileShareIntent(context, files, card)
                assertEquals("text/markdown", share.type)
                assertTrue(share.flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
                val uri = share.getParcelableExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri::class.java)!!
                assertArrayEquals(expected, context.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
                assertEquals(uri, share.clipData!!.getItemAt(0).uri)
            } finally { context.contentResolver.delete(target, null, null) }
        }
    }

    @Test fun verifiedImagesAndNativePdfAreSentAsBinaryAndQueueKeepsFiles() = runBlocking {
        fixture { db, profiles, files, scope ->
            profiles.recordCapabilities(profiles.active(), ModelCapabilities(images = true, pdf = true))
            val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
            val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            bitmap.recycle()
            files.importBytes("image.png", "image/png", bytes)
            files.importBytes("scan.pdf", "application/pdf", pdf(null))
            var requests = 0
            val timeline = AgentTimeline(db, profiles, client { request ->
                val binaries = request.messages.flatMap { it.files }
                if (requests++ == 0) {
                    assertEquals(setOf("image/png", "application/pdf"), binaries.map { it.mimeType }.toSet())
                    assertTrue(binaries.all { java.util.Base64.getDecoder().decode(it.base64).isNotEmpty() })
                } else {
                    assertTrue(binaries.isEmpty())
                    assertTrue(request.messages.any { it.text.contains("image.png") })
                }
                emit(ModelEvent.Text("已读取图片和PDF")); emit(ModelEvent.Finished(ModelFinish.Complete))
            }, scope, fileStore = files)
            timeline.enqueue("")
            assertFalse(files.hasDraft())
            files.collectGarbage()
            assertEquals(2, db.agentFiles().all().size)
            timeline.resumeQueue()
            withTimeout(5000) { timeline.state.first { it.ready && !it.running } }
            assertTrue(db.agent().unfinishedRuns().isEmpty())
            profiles.recordCapabilities(profiles.active(), ModelCapabilities(textStreaming = true))
            timeline.send("继续文字对话")
            withTimeout(5000) { timeline.state.first { it.ready && !it.running } }
            assertEquals(2, requests)
            assertTrue(db.agent().unfinishedRuns().isEmpty())
            timeline.clearChat(); assertTrue(db.agentFiles().all().isEmpty())
        }
    }

    @Test fun utf8MarkdownSendsWithoutCreatingNotesAndClearReclaimsAttachments() = runBlocking {
        fixture { db, profiles, files, scope ->
            val id = files.importBytes("行程.md", "text/markdown", "# 高铁计划\n周六出发".toByteArray())
            val timeline = AgentTimeline(db, profiles, client { request ->
                assertTrue(request.messages.any { it.text.contains("# 高铁计划") })
                emit(ModelEvent.Text("收到行程")); emit(ModelEvent.Finished(ModelFinish.Complete))
            }, scope, fileStore = files)
            timeline.send("")
            withTimeout(5000) { timeline.state.first { it.ready && !it.running } }
            assertFalse(files.hasDraft()); assertTrue(db.notes().getAll().isEmpty())
            assertTrue(files.file(id).exists())
            assertTrue(db.agent().unfinishedRuns().isEmpty())
            timeline.newTopic()
            val historical = db.agent().messages().first { it.role == AgentMessageRole.User }
            assertTrue(agentMessageMemoryText(db, historical).contains("高铁"))
            assertTrue(db.memory().searchMessages("\"高 铁\"", listOf(historical.id)).contains(historical.id))
            timeline.clearChat()
            assertFalse(files.file(id).exists()); assertNull(db.attachments().get(id))
        }
    }

    @Test fun unsupportedImageRetainsDraftAndEncodedImageHasBoundedDimensions() = runBlocking {
        fixture { db, profiles, files, scope ->
            val image = Bitmap.createBitmap(3000, 1200, Bitmap.Config.ARGB_8888)
            val bytes = ByteArrayOutputStream().also { image.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            image.recycle()
            val id = files.importBytes("image.png", "image/png", bytes)
            val timeline = AgentTimeline(db, profiles, client { error("Unverified image must not be dispatched") }, scope, fileStore = files)
            try { timeline.send("请描述图片"); fail("expected capability rejection") } catch (_: IllegalArgumentException) { }
            assertEquals("请描述图片", timeline.draft.value); assertTrue(files.hasDraft())
            assertEquals(2048, db.attachments().get(id)!!.widthPx)
            assertTrue(db.agent().messages().isEmpty())
            files.removeDraft(id); assertFalse(files.file(id).exists())
        }
    }

    @Test fun pdfTextFallbackAndScannedPdfCapabilityAreCheckedBeforeSending() = runBlocking {
        fixture { db, profiles, files, scope ->
            val textId = files.importBytes("text.pdf", "application/pdf", pdf("XNOTE_7391"))
            assertTrue(db.agentFiles().get(textId)!!.text.contains("XNOTE_7391"))
            val timeline = AgentTimeline(db, profiles, client { request ->
                assertTrue(request.messages.any { it.text.contains("XNOTE_7391") && it.files.isEmpty() })
                emit(ModelEvent.Text("已提取")); emit(ModelEvent.Finished(ModelFinish.Complete))
            }, scope, fileStore = files)
            timeline.send("读取PDF")
            withTimeout(5000) { timeline.state.first { it.ready && !it.running } }
            val scan = files.importBytes("scan.pdf", "application/pdf", pdf(null))
            assertFalse(db.agentFiles().get(scan)!!.textComplete)
            try { timeline.send("读取扫描件"); fail("must require native PDF") } catch (_: IllegalArgumentException) { }
            assertTrue(files.hasDraft()); assertEquals("读取扫描件", timeline.draft.value)
        }
    }

    @Test fun invalidEncodingCorruptFilesAndCountLimitPreserveExistingDraft() = runBlocking {
        fixture { db, _, files, _ ->
            val first = files.importBytes("valid.txt", "text/plain", "有效草稿".toByteArray())
            for ((name, mime, bytes) in listOf(Triple("bad.txt", "text/plain", byteArrayOf(0xc3.toByte(), 0x28)),
                Triple("bad.pdf", "application/pdf", "%PDF-broken".toByteArray()), Triple("bad.png", "image/png", byteArrayOf(1, 2)))) {
                try { files.importBytes(name, mime, bytes); fail("invalid file accepted") } catch (_: IllegalArgumentException) { }
            }
            assertEquals(listOf(first), db.agentFiles().ids("draft", "1"))
            repeat(3) { files.importBytes("$it.txt", "text/plain", "ok".toByteArray()) }
            try { files.importBytes("fifth.txt", "text/plain", "ok".toByteArray()); fail("too many files") } catch (_: IllegalArgumentException) { }
            assertEquals(4, db.agentFiles().ids("draft", "1").size)
        }
    }

    @Test fun outputFileIsPersistedExactlyOnceAndNeverBecomesANote() = runBlocking {
        fixture { db, profiles, files, scope ->
            profiles.recordCapabilities(profiles.active(), ModelCapabilities(true, true, 1))
            val call = ModelToolCall("file", "output_file", Json.encodeToJsonElement(AgentOutputFileArguments("计划.md", "# 安排\n周六出发")).jsonObject)
            var calls = 0
            val timeline = AgentTimeline(db, profiles, client {
                if (++calls == 1) { emit(ModelEvent.ToolCall(call)); emit(ModelEvent.Finished(ModelFinish.ToolCalls)) }
                else {
                    val run = db.agent().unfinishedRuns().single()
                    val cached = AgentNoteStore(db, files).executeTool(run.id, call) as AgentToolResult.Finished
                    assertTrue(cached.result.content.contains("saved"))
                    emit(ModelEvent.Text("文件已生成")); emit(ModelEvent.Finished(ModelFinish.Complete))
                }
            }, scope, fileStore = files)
            timeline.send("生成Markdown文件")
            withTimeout(5000) { timeline.state.first { it.ready && !it.running } }
            val output = db.agentFiles().all().single()
            assertEquals("output", output.origin); assertEquals("# 安排\n周六出发", files.file(output.attachmentId).readText())
            assertEquals(2, calls); assertTrue(db.notes().getAll().isEmpty())
            val owner = files.cards.first().single().ownerId
            timeline.deleteMessage(owner)
            assertFalse(files.file(output.attachmentId).exists())
        }
    }

    // -- Functions

    private fun pdf(text: String?): ByteArray {
        val document = PdfDocument()
        return try {
        val page = document.startPage(PdfDocument.PageInfo.Builder(300, 180, 1).create())
        text?.let { page.canvas.drawText(it, 20f, 80f, Paint().apply { textSize = 20f }) }
        document.finishPage(page)
        ByteArrayOutputStream().also(document::writeTo).toByteArray()
        } finally { document.close() }
    }
    private fun client(events: suspend FlowCollector<ModelEvent>.(ModelRequest) -> Unit) = object : ModelClient {
        override fun stream(profile: ModelProfile, apiKey: String, request: ModelRequest): Flow<ModelEvent> = flow { events(request) }
    }
    private suspend fun fixture(block: suspend (XNoteDatabase, ModelProfileStore, AgentFileStore, CoroutineScope) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = XNoteDatabase.createInMemory(context)
        val directory = File(context.cacheDir, "agent-files-test-${System.nanoTime()}").apply { mkdirs() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val profiles = ModelProfileStore(db, AndroidModelCredentialStore(context))
        try {
            profiles.save(ModelProfile("profile", name = "测试", protocol = ModelProtocol.OpenAI, modelId = "test", isDefault = true), "test-key")
            block(db, profiles, AgentFileStore(db, context, directory), scope)
        } finally {
            scope.coroutineContext[Job]?.cancelAndJoin()
            db.agent().unfinishedRuns().forEach { db.agent().saveRun(it.copy(status = AgentRunStatus.Cancelled)) }
            profiles.delete("profile"); db.close(); directory.deleteRecursively()
        }
    }
}
