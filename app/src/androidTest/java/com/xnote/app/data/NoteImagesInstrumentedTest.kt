package com.xnote.app.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.xnote.app.data.db.XNoteDatabase
import com.xnote.app.data.files.AttachmentFileStore
import com.xnote.app.data.files.cameraFile
import com.xnote.app.data.files.cameraUri
import com.xnote.app.data.files.decodeNoteImage
import com.xnote.app.data.files.importNoteImage
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.domain.document.ImageAction
import com.xnote.app.domain.document.ImageBlock
import com.xnote.app.domain.document.EditorSelection
import com.xnote.app.domain.model.SystemEpochClock
import com.xnote.app.feature.notes.editor.NoteEditorSession
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.File

// -- Tests

class NoteImagesInstrumentedTest {
    @Test
    fun imageImportReplacementUndoCleanupAndDatabaseReopen() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "images-${System.nanoTime()}.db"
        val root = File(context.cacheDir, "images-${System.nanoTime()}").apply { mkdirs() }
        var database = XNoteDatabase.create(context, name)
        var library = NoteLibrary(database, AttachmentFileStore(root), SystemEpochClock)
        try {
            val source = File(root, "source.png")
            val bitmap = Bitmap.createBitmap(3000, 1000, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.BLUE)
            source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            val note = library.createNote(null)
            val session = NoteEditorSession(library, note.id, this)
            session.load()
            val attachment = importNoteImage(context, library, Uri.fromFile(source), session.attachmentOwner)
            assertEquals(2560, attachment.widthPx)
            val target = session.selection
            session.attachImage(attachment.id, target, null)
            val image = session.document.blocks.filterIsInstance<ImageBlock>().single()
            session.editImage(image.id, ImageAction.RotateRight)
            session.editImage(image.id, ImageAction.Duplicate)
            val duplicateId = session.selection.blockId
            session.transformImage(duplicateId, 0.5f, 25f, 12f, -8f)
            session.finishImageGesture()
            session.flushSave()
            val replacement = importNoteImage(context, library, Uri.fromFile(source), session.attachmentOwner)
            session.attachImage(replacement.id, EditorSelection(duplicateId), duplicateId)
            session.flushSave()
            assertEquals(0.5f, session.document.blocks.filterIsInstance<ImageBlock>().last().scale)
            session.editImage(image.id, ImageAction.Delete)
            session.flushSave()
            library.purgeExpiredTrash()
            assertTrue(library.attachmentFile(attachment).exists())
            session.undo()
            session.undo()
            session.flushSave()
            assertEquals(2, session.document.blocks.filterIsInstance<ImageBlock>().size)
            assertTrue(session.document.blocks.filterIsInstance<ImageBlock>().all { it.attachmentId == attachment.id })
            session.releaseAttachments()
            library.purgeExpiredTrash()
            assertNull(library.getAttachment(replacement.id))
            val saved = session.document
            database.close()
            database = XNoteDatabase.create(context, name)
            library = NoteLibrary(database, AttachmentFileStore(root), SystemEpochClock)
            assertEquals(saved, library.getNote(note.id)?.document)
            val decoded = decodeNoteImage(library.attachmentFile(attachment))
            assertTrue(decoded.width <= 1280)
            decoded.recycle()
            library.trashNotes(listOf(note.id))
            library.restoreNotes(listOf(note.id))
            assertTrue(library.attachmentFile(attachment).exists())
            library.trashNotes(listOf(note.id))
            library.emptyTrash()
            assertFalse(library.attachmentFile(attachment).exists())
        } finally {
            database.close()
            context.deleteDatabase(name)
            root.deleteRecursively()
        }
    }

    @Test
    fun cameraProviderCanWriteAndCorruptImageDoesNotCreateAttachment() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "test-${System.nanoTime()}.jpg"
        val file = cameraFile(context, name)
        file.parentFile?.mkdirs()
        val database = XNoteDatabase.createInMemory(context)
        val root = File(context.cacheDir, "image-invalid-${System.nanoTime()}")
        val library = NoteLibrary(database, AttachmentFileStore(root), SystemEpochClock)
        try {
            val uri = cameraUri(context, name)
            assertEquals("content", uri.scheme)
            context.contentResolver.openOutputStream(uri)!!.use { it.write(byteArrayOf(1, 2, 3)) }
            assertTrue(file.exists())
            var rejected = false
            try { importNoteImage(context, library, uri, "test") } catch (_: Exception) { rejected = true }
            assertTrue(rejected)
            assertTrue(database.attachments().getAll().isEmpty())
        } finally {
            database.close()
            file.delete()
            root.deleteRecursively()
        }
    }
}
