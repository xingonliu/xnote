package com.xnote.app.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.xnote.app.data.db.XNoteDatabase
import com.xnote.app.data.files.AttachmentFileStore
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.domain.document.ImageBlock
import com.xnote.app.domain.document.InlineRun
import com.xnote.app.domain.document.NoteDocument
import com.xnote.app.domain.document.TextBlock
import com.xnote.app.domain.model.AttachmentKind
import com.xnote.app.domain.model.EpochClock
import com.xnote.app.domain.model.NoteKind
import com.xnote.app.feature.notes.editor.NoteEditorSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

// -- Tests

@RunWith(AndroidJUnit4::class)
class NoteLibraryInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = MutableEpochClock(1_000L)
    private val database = XNoteDatabase.createInMemory(context)
    private val filesRoot = File(context.cacheDir, "xnote-library-test-${System.nanoTime()}")
    private val library = NoteLibrary(
        database = database,
        files = AttachmentFileStore(filesRoot),
        clock = clock,
    )

    @After
    fun tearDown() {
        database.close()
        filesRoot.deleteRecursively()
    }

    @Test
    fun createNotePersistsAcrossReads() = runTest {
        val notebook = library.createNotebook("工作")
        val created = library.createRichNote(notebook.id)
        val saved = library.saveNote(
            created.copy(
                title = "会议记录",
                document = NoteDocument(
                    blocks = listOf(
                        TextBlock(id = "t1", inlines = listOf(InlineRun("今天讨论进度"))),
                    ),
                ),
            ),
        )
        val loaded = library.getNote(saved.id)
        assertNotNull(loaded)
        assertEquals("会议记录", loaded?.title)
        assertEquals(notebook.id, loaded?.notebookId)
        assertEquals(NoteKind.Rich, loaded?.kind)
        assertEquals("今天讨论进度", loaded?.summary)
        assertEquals(6, loaded?.visibleCharacterCount)
    }

    @Test
    fun searchHitsConsecutiveChineseSubstring() = runTest {
        val created = library.createRichNote(null)
        library.saveNote(
            created.copy(
                title = "我的笔记本",
                document = NoteDocument(
                    blocks = listOf(
                        TextBlock(id = "t1", inlines = listOf(InlineRun("正文"))),
                    ),
                ),
            ),
        )
        val hits = library.searchNoteIds("笔记本")
        assertEquals(listOf(created.id), hits)
    }

    @Test
    fun deleteNotebookMovesNotesToTrashAndRestoreUnfilesThem() = runTest {
        val notebook = library.createNotebook("临时本")
        val note = library.createRichNote(notebook.id)
        library.deleteNotebook(notebook.id)
        assertNull(library.getNotebook(notebook.id))
        val trashed = library.getNote(note.id)
        assertTrue(trashed?.isTrashed == true)
        assertNull(trashed?.notebookId)
        assertEquals("临时本", trashed?.originalNotebookName)
        assertTrue(library.searchNoteIds("临时").isEmpty())

        library.restoreNotes(listOf(note.id))
        val restored = library.getNote(note.id)
        assertFalse(restored?.isTrashed == true)
        assertNull(restored?.notebookId)
        assertNull(restored?.originalNotebookName)
    }

    @Test
    fun restoreKeepsNotebookWhenItStillExists() = runTest {
        val notebook = library.createNotebook("保留本")
        val note = library.createRichNote(notebook.id)
        library.trashNotes(listOf(note.id))
        library.restoreNotes(listOf(note.id))
        assertEquals(notebook.id, library.getNote(note.id)?.notebookId)
    }

    @Test
    fun purgeExpiredTrashDeletesUnreferencedAttachments() = runTest {
        val created = library.createRichNote(null)
        val attachment = library.putAttachment(
            kind = AttachmentKind.Image,
            mimeType = "image/png",
            extension = "png",
            bytes = byteArrayOf(1, 2, 3, 4),
        )
        library.saveNote(
            created.copy(
                document = NoteDocument(
                    blocks = listOf(
                        ImageBlock(id = "img", attachmentId = attachment.id),
                    ),
                ),
            ),
        )
        library.trashNotes(listOf(created.id))
        assertTrue(library.attachmentFile(attachment).exists())

        clock.nowMs = 1_000L + TimeUnit.DAYS.toMillis(30)
        library.purgeExpiredTrash()
        assertNull(library.getNote(created.id))
        assertNull(library.getAttachment(attachment.id))
        assertFalse(library.attachmentFile(attachment).exists())
    }

    @Test
    fun notePersistsAfterTheDatabaseIsClosedAndReopened() = runTest {
        val databaseName = "xnote-cold-start-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        val firstDatabase = XNoteDatabase.create(context, databaseName)
        val firstLibrary = NoteLibrary(
            database = firstDatabase,
            files = AttachmentFileStore(filesRoot),
            clock = clock,
        )
        val created = firstLibrary.createRichNote(null)
        firstLibrary.saveNote(created.copy(title = "冷启动仍存在"))
        firstDatabase.close()

        val reopenedDatabase = XNoteDatabase.create(context, databaseName)
        try {
            val reopenedLibrary = NoteLibrary(
                database = reopenedDatabase,
                files = AttachmentFileStore(filesRoot),
                clock = clock,
            )
            assertEquals("冷启动仍存在", reopenedLibrary.getNote(created.id)?.title)
        } finally {
            reopenedDatabase.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun editorMoveRemainsStableAfterAnotherAutomaticSave() = runTest {
        val source = library.createNotebook("来源本")
        val destination = library.createNotebook("目标本")
        val note = library.createRichNote(source.id)
        val session = NoteEditorSession(
            library = library,
            noteId = note.id,
            scope = this,
        )

        session.load()
        session.moveToNotebook(destination.id)
        session.updateTitle("移动后继续编辑")
        session.flushSave()

        val saved = library.getNote(note.id)
        assertEquals(destination.id, saved?.notebookId)
        assertEquals("移动后继续编辑", saved?.title)
    }

    @Test
    fun reorderNotesPersistsManualOrder() = runTest {
        val notebook = library.createNotebook("排序本")
        val first = library.createRichNote(notebook.id)
        clock.nowMs = 2_000L
        val second = library.createRichNote(notebook.id)
        library.reorderNotes(listOf(second.id, first.id))
        val ordered = library.observeNotesInNotebook(notebook.id, com.xnote.app.domain.model.NoteListSort.Manual).first()
        assertEquals(listOf(second.id, first.id), ordered.map { it.id })
    }

    @Test
    fun emptyNotebookDeleteLeavesNoTrashEntries() = runTest {
        val notebook = library.createNotebook("空本")
        library.deleteNotebook(notebook.id)
        assertTrue(library.observeTrashedNotes().first().isEmpty())
    }
}

// -- Fixtures

private class MutableEpochClock(var nowMs: Long) : EpochClock {
    override fun nowMs(): Long = nowMs
}
