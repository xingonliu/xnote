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
import com.xnote.app.domain.model.BackgroundKey
import com.xnote.app.domain.model.GridBuiltinBackgroundId
import com.xnote.app.domain.model.EpochClock
import com.xnote.app.domain.document.EditorSelection
import com.xnote.app.domain.document.ParagraphStyle
import com.xnote.app.domain.document.plainText
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
import java.io.ByteArrayInputStream
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
    fun appearanceAndOrderPersistAndRenamePreservesAppearance() = runTest {
        val first = library.createNotebook("第一本")
        val second = library.createNotebook("第二本")
        library.setNotebookAppearance(first.id, "purple", "star")
        library.renameNotebook(first.id, "新名字")
        library.reorderNotebooks(listOf(second.id, first.id))
        val restored = library.observeNotebooks().first()
        assertEquals(listOf(second.id, first.id), restored.map { it.id })
        assertEquals("purple", restored.last().color)
        assertEquals("star", restored.last().icon)
        assertEquals("新名字", restored.last().name)
    }

    @Test
    fun deletingNotebookCanKeepActiveNotesAndSearchWhilePreservingExistingTrash() = runTest {
        val notebook = library.createNotebook("工作")
        val active = library.saveNote(library.createNote(notebook.id).copy(title = "保留的设计"))
        val trashed = library.createNote(notebook.id)
        library.trashNotes(listOf(trashed.id))
        val deletedAt = library.getNote(trashed.id)?.deletedAtEpochMs
        library.deleteNotebook(notebook.id, moveNotesToUnfiled = true)
        assertNull(library.getNotebook(notebook.id))
        assertNull(library.getNote(active.id)?.notebookId)
        assertNull(library.getNote(active.id)?.deletedAtEpochMs)
        assertEquals(listOf(active.id), library.searchNoteIds("设计"))
        assertEquals(deletedAt, library.getNote(trashed.id)?.deletedAtEpochMs)
        assertEquals("工作", library.getNote(trashed.id)?.originalNotebookName)
        library.restoreNotes(listOf(trashed.id))
        assertNull(library.getNote(trashed.id)?.notebookId)
    }

    @Test
    fun createNotePersistsAcrossReads() = runTest {
        val notebook = library.createNotebook("工作")
        val created = library.createNote(notebook.id)
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
        assertEquals("今天讨论进度", loaded?.summary)
        assertEquals(6, loaded?.visibleCharacterCount)
    }

    @Test
    fun searchHitsConsecutiveChineseSubstring() = runTest {
        val created = library.createNote(null)
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
    fun searchReturnsOriginalSnippetFiltersNotebookAndExcludesTrash() = runTest {
        val includedNotebook = library.createNotebook("工作")
        val otherNotebook = library.createNotebook("生活")
        val included = library.createNote(includedNotebook.id)
        library.saveNote(
            included.copy(
                title = "会议安排",
                document = NoteDocument(
                    blocks = listOf(
                        TextBlock(id = "included", inlines = listOf(InlineRun("我的笔记本记录了发布计划"))),
                    ),
                ),
            ),
        )
        val filtered = library.createNote(otherNotebook.id)
        library.saveNote(filtered.copy(title = "另一本笔记本"))
        val trashed = library.createNote(includedNotebook.id)
        library.saveNote(trashed.copy(title = "回收站笔记本"))
        library.trashNotes(listOf(trashed.id))

        val results = library.searchNotes("笔记本", includedNotebook.id)

        assertEquals(listOf(included.id), results.map { it.note.id })
        assertTrue(results.single().matchedText.contains("我的笔记本"))
    }

    @Test
    fun deleteNotebookMovesNotesToTrashAndRestoreUnfilesThem() = runTest {
        val notebook = library.createNotebook("临时本")
        val note = library.createNote(notebook.id)
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
        val note = library.createNote(notebook.id)
        library.trashNotes(listOf(note.id))
        library.restoreNotes(listOf(note.id))
        assertEquals(notebook.id, library.getNote(note.id)?.notebookId)
    }

    @Test
    fun purgeExpiredTrashDeletesUnreferencedAttachments() = runTest {
        val created = library.createNote(null)
        val attachment = library.putAttachment(
            kind = AttachmentKind.Image,
            mimeType = "image/png",
            extension = "png",
            input = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)),
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
        val created = firstLibrary.createNote(null)
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
        val note = library.createNote(source.id)
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
    fun markdownShortcutFormatsHeadingAndUndoRestoresCharactersThenPersists() = runTest {
        val created = library.createNote(null)
        val session = NoteEditorSession(library, created.id, this)
        session.load()
        val blockId = session.document.blocks.first().id

        session.onPlainTextChange(EditorSelection(blockId), "", "#", composing = false)
        session.onPlainTextChange(EditorSelection(blockId), "#", "# ", composing = false)
        val heading = session.document.blocks.first() as TextBlock
        assertEquals(ParagraphStyle.Heading, heading.paragraphStyle)
        assertEquals("", heading.inlines.plainText())

        session.undo()
        assertEquals("# ", (session.document.blocks.first() as TextBlock).inlines.plainText())
        assertEquals(ParagraphStyle.Body, (session.document.blocks.first() as TextBlock).paragraphStyle)

        session.redo()
        session.flushSave()
        val saved = library.getNote(created.id)
        val savedBlock = saved?.document?.blocks?.first() as TextBlock
        assertEquals(ParagraphStyle.Heading, savedBlock.paragraphStyle)
        assertEquals("", savedBlock.inlines.plainText())
    }

    @Test
    fun renamingNotebookKeepsAssignedNotes() = runTest {
        val notebook = library.createNotebook("原名称")
        val note = library.createNote(notebook.id)

        library.renameNotebook(notebook.id, "新名称")

        assertEquals(notebook.id, library.getNote(note.id)?.notebookId)
    }

    @Test
    fun composingAndDisabledShortcutsKeepOriginalCharacters() = runTest {
        val created = library.createNote(null)
        val session = NoteEditorSession(library, created.id, this)
        session.load()
        val blockId = session.document.blocks.first().id

        session.onPlainTextChange(EditorSelection(blockId), "", "# ", composing = true)
        assertEquals("# ", (session.document.blocks.first() as TextBlock).inlines.plainText())
        assertEquals(ParagraphStyle.Body, (session.document.blocks.first() as TextBlock).paragraphStyle)

        session.markdownShortcutsEnabled = false
        session.onPlainTextChange(EditorSelection(blockId), "# ", "# 标题", composing = false)
        session.flushSave()
        val saved = library.getNote(created.id)?.document?.blocks?.first() as TextBlock
        assertEquals("# 标题", saved.inlines.plainText())
        assertEquals(ParagraphStyle.Body, saved.paragraphStyle)
    }

    @Test
    fun reorderNotesPersistsManualOrder() = runTest {
        val notebook = library.createNotebook("排序本")
        val first = library.createNote(notebook.id)
        clock.nowMs = 2_000L
        val second = library.createNote(notebook.id)
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

    @Test
    fun noteBackgroundPersistsAcrossMovesThenCanReturnToInheritance() = runTest {
        val source = library.createNotebook("来源")
        val destination = library.createNotebook("目标")
        val note = library.createNote(source.id)
        val background = BackgroundKey(GridBuiltinBackgroundId)

        library.setNoteBackground(note.id, background)
        library.moveNotes(listOf(note.id), destination.id)

        assertEquals(background, library.getNote(note.id)?.backgroundKey)
        library.setNoteBackground(note.id, null)
        assertNull(library.getNote(note.id)?.backgroundKey)
    }

}

// -- Fixtures

private class MutableEpochClock(var nowMs: Long) : EpochClock {
    override fun nowMs(): Long = nowMs
}
