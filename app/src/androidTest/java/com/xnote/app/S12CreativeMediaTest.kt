package com.xnote.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.xnote.app.data.db.XNoteDatabase
import com.xnote.app.data.files.*
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.domain.document.*
import com.xnote.app.domain.model.*
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

// -- Type Definitions

class S12CreativeMediaTest {
    // -- Functions

    @Test fun versionSixteenMigrationKeepsExistingNotebook() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val name = "s12-migration-${System.nanoTime()}.db"
        val path = context.getDatabasePath(name)
        path.parentFile?.mkdirs()
        val testContext = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context
        val schema = org.json.JSONObject(testContext.assets.open("com.xnote.app.data.db.XNoteDatabase/16.json")
            .bufferedReader().use { it.readText() }).getJSONObject("database")
        try {
            androidx.sqlite.driver.bundled.BundledSQLiteDriver().open(path.absolutePath).use { connection ->
                val entities = schema.getJSONArray("entities")
                for (index in 0 until entities.length()) {
                    val entity = entities.getJSONObject(index)
                    val table = entity.getString("tableName")
                    connection.prepare(entity.getString("createSql").replace("\${TABLE_NAME}", table)).use { it.step() }
                    val indices = entity.optJSONArray("indices")
                    if (indices != null) for (i in 0 until indices.length()) {
                        connection.prepare(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", table)).use { it.step() }
                    }
                }
                val setup = schema.getJSONArray("setupQueries")
                for (index in 0 until setup.length()) connection.prepare(setup.getString(index)).use { it.step() }
                connection.prepare("INSERT INTO notebooks (id,name,sortIndex,createdAtEpochMs,updatedAtEpochMs,color,icon) VALUES ('book','保留笔记本',0,100,100,'blue','book')").use { it.step() }
                connection.prepare("PRAGMA user_version = 16").use { it.step() }
            }
            val database = XNoteDatabase.create(context, name)
            try {
                assertEquals("保留笔记本", database.notebooks().get("book")?.name)
                assertTrue(database.stickers().attachmentIds().isEmpty())
            } finally { database.close() }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun drawingEraseAndMaskCorrectionPreserveTransparentPixels() {
        val line = DrawingStroke(listOf(DrawingPoint(.2f, .5f), DrawingPoint(.8f, .5f)), width = 30f)
        val erase = DrawingStroke(listOf(DrawingPoint(.5f, .2f), DrawingPoint(.5f, .8f)), width = 40f, erase = true)
        val drawing = renderDrawing(listOf(line, erase))
        try {
            assertEquals(0, Color.alpha(drawing.getPixel(512, 384)))
            assertEquals(255, Color.alpha(drawing.getPixel(300, 384)))
            assertEquals(0, Color.alpha(drawing.getPixel(10, 10)))
        } finally { drawing.recycle() }
        val source = Bitmap.createBitmap(30, 30, Bitmap.Config.ARGB_8888).apply { eraseColor(0x80ff0000.toInt()) }
        val mask = IntArray(900)
        paintMask(mask, 30, 30, 2f, 15f, 27f, 15f, 3f, 255)
        paintMask(mask, 30, 30, 15f, 15f, 15f, 15f, 2f, 0)
        val result = applyCutoutMask(source, mask)
        try {
            assertEquals(128, Color.alpha(result.getPixel(5, 15)))
            assertEquals(0, Color.alpha(result.getPixel(15, 15)))
            assertEquals(0, Color.alpha(result.getPixel(5, 5)))
        } finally { source.recycle(); result.recycle() }
    }

    @Test fun libraryDeletionReopenTrashAndRevisionKeepReferencedAttachment() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val name = "s12-library-${System.nanoTime()}.db"
        val root = File(context.cacheDir, "s12-library-${System.nanoTime()}")
        var database = XNoteDatabase.create(context, name)
        var library = NoteLibrary(database, AttachmentFileStore(root), SystemEpochClock)
        try {
            val bitmap = renderDrawing(listOf(DrawingStroke(listOf(DrawingPoint(.5f, .5f)), width = 30f)))
            val attachment = try { saveMediaBitmap(context, library, bitmap, AttachmentKind.Sticker, "test") } finally { bitmap.recycle() }
            val sticker = library.saveSticker(attachment.id, "羊驼")
            val note = library.saveNote(library.createNote(null).copy(document = NoteDocument(blocks = listOf(StickerBlock("s", attachment.id, sticker.id)))))
            library.releaseSessionAttachments("test")
            database.close()
            database = XNoteDatabase.create(context, name)
            library = NoteLibrary(database, AttachmentFileStore(root), SystemEpochClock)
            assertEquals(listOf(attachment.id), database.stickers().attachmentIds())
            library.renameSticker(sticker.id, "透明羊驼")
            library.deleteSticker(sticker.id)
            assertTrue(database.stickers().attachmentIds().isEmpty())
            assertTrue(library.attachmentFile(attachment).isFile)
            library.trashNotes(listOf(note.id))
            library.purgeExpiredTrash()
            assertNotNull(library.getAttachment(attachment.id))
            library.restoreNotes(listOf(note.id))
            assertEquals(note.document, library.getNote(note.id)!!.document)
            library.saveRevision(note.id, RevisionReason.AgentPolish)
            library.saveNote(library.getNote(note.id)!!.copy(document = emptyNoteDocument()))
            library.purgeExpiredTrash()
            assertNotNull(library.getAttachment(attachment.id))
            assertEquals(note.document, library.getNoteRevisions(note.id).single().document)
            library.permanentlyDeleteNotes(listOf(note.id))
            assertNull(library.getAttachment(attachment.id))
        } finally { database.close(); context.deleteDatabase(name); root.deleteRecursively() }
    }

    @Test fun productionEngineUsesPinnedModelAndReturnsEditableMask() = runBlocking {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val directory = File(context.filesDir, "cutout-models").apply { mkdirs() }
        val download = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("s12Download") == "true"
        if (download) File(directory, "u2netp.onnx").delete()
        else instrumentation.context.assets.open("u2netp.onnx").use { input -> File(directory, "u2netp.onnx").outputStream().use(input::copyTo) }
        val source = instrumentation.context.assets.open("sample.jpg").use { android.graphics.BitmapFactory.decodeStream(it) }!!
        val stages = mutableListOf<String>()
        try {
            val mask = CutoutEngine(context).process(source) { stages += it }
            assertEquals(source.width * source.height, mask.size)
            assertTrue(mask.any { it < 10 })
            assertTrue(mask.any { it > 245 })
            assertEquals(listOf("正在加载抠图模型", "正在识别主体"), stages.takeLast(2))
            if (download) assertTrue(stages.any { it.startsWith("下载抠图模型") })
        } finally { source.recycle() }
    }
}
