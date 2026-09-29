package com.xnote.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import com.xnote.app.data.agent.*
import com.xnote.app.data.db.*
import com.xnote.app.data.files.AttachmentFileStore
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.domain.agent.*
import com.xnote.app.domain.document.*
import com.xnote.app.domain.model.SystemEpochClock
import com.xnote.app.feature.notes.editor.NoteEditorSession
import com.xnote.app.design.XNoteRichTextAction
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.File

// -- Tests

@OptIn(ExperimentalCoroutinesApi::class)
class EditorAgentSaveTest {
    // -- State and Variables

    private val editorScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = XNoteDatabase.createInMemory(context)
    private val library = NoteLibrary(db, AttachmentFileStore(File(context.cacheDir, "editor-agent-save")), SystemEpochClock)

    // -- Functions

    @After fun close() { editorScope.cancel(); db.close() }

    @Test fun staleEditorSaveKeepsDistantAgentTextAndRejectKeepsUserInput() = runTest {
        val note = prepare()
        val session = NoteEditorSession(library, note.id, editorScope)
        session.load()
        session.onPlainTextChange(EditorSelection("body", 4, 4), "abcdef", "abcUdef", false)
        applyAgent(note, "abcdeZ")
        session.flushSave()
        assertEquals("abcUdeZ", text(library.getNote(note.id)!!.document))
        assertEquals("abcUdeZ", text(session.document))
        assertEquals(listOf(AgentChangeOrigin.Agent, AgentChangeOrigin.User), db.agent().noteChanges(note.id).map { it.origin })
        assertTrue(AgentReviewStore(db).reject(note.id) is AgentReviewResult.Applied)
        session.refreshFromStorage()
        assertEquals("abcUdef", text(session.document))
    }

    @Test fun overlappingUserTypingWinsAndUndoDoesNotEraseUnrelatedAgentEdit() = runTest {
        val note = prepare()
        val session = NoteEditorSession(library, note.id, editorScope)
        session.load()
        session.onPlainTextChange(EditorSelection("body", 2, 2), "abcdef", "aUcdef", false)
        applyAgent(note, "aAcdeZ")
        session.flushSave()
        assertEquals("aUcdeZ", text(session.document))
        session.undo()
        session.flushSave()
        assertEquals("aAcdeZ", text(session.document))
        session.redo()
        session.flushSave()
        assertEquals("aUcdeZ", text(session.document))
    }

    @Test fun externalRefreshRebasesDirtyContentCaretAndHistoryBeforeNextSave() = runTest {
        val note = prepare()
        val session = NoteEditorSession(library, note.id, editorScope)
        session.load()
        session.onPlainTextChange(EditorSelection("body", 4, 4), "abcdef", "abcUdef", false)
        applyAgent(note, "😀abcdef")
        session.refreshFromStorage()
        assertEquals("😀abcUdef", text(session.document))
        assertEquals(6, session.selection.end)
        session.flushSave()
        session.undo()
        session.flushSave()
        assertEquals("😀abcdef", text(session.document))
        assertEquals(session.document, library.getNote(note.id)!!.document)
    }

    @Test fun cleanEditorReceivesAgentBodyWithoutCreatingUserChange() = runTest {
        val note = prepare()
        val session = NoteEditorSession(library, note.id, editorScope)
        session.load()
        applyAgent(note, "Agent正文")
        session.refreshFromStorage()
        session.flushSave()
        assertEquals("Agent正文", text(session.document))
        assertFalse(session.canUndo)
        assertEquals(1, db.agent().noteChanges(note.id).size)
    }

    @Test fun imeCompositionWaitsForRefreshButFlushPersistsVisibleUserText() = runTest {
        val note = prepare()
        val session = NoteEditorSession(library, note.id, editorScope)
        session.load()
        session.onPlainTextChange(EditorSelection("body", 4, 4), "abcdef", "abc中def", true)
        applyAgent(note, "abcdeZ")
        session.refreshFromStorage()
        assertEquals("abc中def", text(session.document))
        session.flushSave()
        assertEquals("abc中deZ", text(library.getNote(note.id)!!.document))
    }

    @Test fun pendingUserInputAfterAgentTrashIsSavedWithoutRestoringNote() = runTest {
        val note = prepare()
        val session = NoteEditorSession(library, note.id, editorScope)
        session.load()
        session.updateTitle("用户尚未落盘的标题")
        AgentReviewStore(db).applyTrash("run", "trash", note.id, db.notes().get(note.id)!!.agentVersion())
        session.flushSave()
        val saved = library.getNote(note.id)!!
        assertTrue(saved.isTrashed)
        assertEquals("用户尚未落盘的标题", saved.title)
        AgentReviewStore(db).reject(note.id)
        assertEquals(saved.title, library.getNote(note.id)!!.title)
        assertFalse(library.getNote(note.id)!!.isTrashed)
    }

    @Test fun typingWhileDatabaseCommitWaitsIsNotReplacedBySaveAcknowledgement() = runTest {
        val note = prepare()
        val session = NoteEditorSession(library, note.id, editorScope)
        session.load()
        session.updateTitle("第一次输入")
        val locked = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val writer = launch(Dispatchers.IO) { db.useWriterConnection { it.immediateTransaction {
            locked.complete(Unit)
            release.await()
        } } }
        locked.await()
        val saving = async(start = CoroutineStart.UNDISPATCHED) { session.flushSave() }
        session.updateTitle("保存过程中继续输入")
        release.complete(Unit)
        writer.join()
        saving.await()
        assertEquals("保存过程中继续输入", session.title)
        session.flushSave()
        assertEquals(session.title, library.getNote(note.id)!!.title)
    }

    @Test fun chosenTypingStyleSurvivesImeAcknowledgementSaveAndRemoteRefresh() = runTest {
        val note = prepare()
        val session = NoteEditorSession(library, note.id, editorScope)
        session.load()
        session.select(EditorSelection("body", 6, 6))
        session.applyAction(XNoteRichTextAction.Bold)
        session.onPlainTextChange(EditorSelection("body", 6, 6), "abcdef", "abcdef", false)
        session.flushSave()
        assertTrue(session.typingMarks.bold)
        applyAgent(note, "Zabcdef")
        session.refreshFromStorage()
        assertTrue(session.typingMarks.bold)
        session.onPlainTextChange(EditorSelection("body", 11, 11), "Zabcdef", "Zabcdefbold", false)
        session.flushSave()
        val runs = (session.document.blocks.single() as TextBlock).inlines
        assertEquals("Zabcdef", runs.first().text)
        assertEquals("bold", runs.last().text)
        assertTrue(runs.last().bold)
    }

    private suspend fun prepare(): com.xnote.app.domain.model.Note {
        db.agent().saveSegment(AgentSegmentEntity("segment", 1))
        db.agent().saveRun(AgentRunEntity("run", "segment", "user", "profile", 1, AgentRunStatus.Running, 1, 1))
        AgentPermissionStore(db).saveFromUser(AgentPermission(AgentPermissionMode.FullAccess))
        val note = library.createNote(null)
        return library.saveNote(requireNotNull(library.getNote(note.id)).copy(title = "标题", document = document("abcdef")))
    }

    private suspend fun applyAgent(note: com.xnote.app.domain.model.Note, body: String) {
        val result = AgentReviewStore(db).applyEdit("run", "edit", db.notes().get(note.id)!!.editBase(), AgentEditableContent(note.title, document(body)))
        assertTrue(result is AgentReviewResult.Applied)
    }

    private fun document(text: String) = NoteDocument(blocks = listOf(TextBlock("body", inlines = listOf(InlineRun(text)))))
    private fun text(document: NoteDocument) = document.blocks.filterIsInstance<TextBlock>().single().inlines.plainText()
}
