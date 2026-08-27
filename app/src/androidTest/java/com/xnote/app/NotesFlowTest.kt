package com.xnote.app

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.xnote.app.data.db.XNoteDatabase
import com.xnote.app.data.files.AttachmentFileStore
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.design.XNoteTheme
import com.xnote.app.domain.model.SystemEpochClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

// -- Tests

class NotesFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database = XNoteDatabase.createInMemory(context)
    private val filesRoot = File(context.cacheDir, "xnote-s3-ui-${System.nanoTime()}")
    private val library = NoteLibrary(
        database = database,
        files = AttachmentFileStore(filesRoot),
        clock = SystemEpochClock,
    )

    @After
    fun tearDown() {
        database.close()
        filesRoot.deleteRecursively()
    }

    @Test
    fun createNotePersistsTitleAndBodyAfterReturningHome() {
        composeRule.setContent {
            XNoteTheme(reduceMotion = true) {
                XNoteApp(noteLibrary = library)
            }
        }

        composeRule.onNodeWithTag("xnote-create-note").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("xnote-editor-title").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("xnote-editor-title").performTextInput("会议记录")
        composeRule.onNodeWithTag("xnote-editor-body").performTextInput("今天讨论进度")
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("会议记录").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("会议记录").assertIsDisplayed()
        composeRule.onNodeWithText("今天讨论进度", substring = true).assertIsDisplayed()
    }

    @Test
    fun deletingANotebookRemovesItsNotesFromTheHomeList() = runTest {
        val notebook = library.createNotebook("临时本")
        val note = library.createRichNote(notebook.id)
        library.saveNote(note.copy(title = "应进入回收站"))

        composeRule.setContent {
            XNoteTheme(reduceMotion = true) {
                XNoteApp(noteLibrary = library)
            }
        }

        composeRule.onNodeWithTag("xnote-notebook-picker").performClick()
        composeRule.onNodeWithContentDescription("打开笔记本").performClick()
        composeRule.onNodeWithContentDescription("更多").performClick()
        composeRule.onNodeWithText("删除笔记本").performClick()
        composeRule.onNodeWithText("删除").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("应进入回收站").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithText("全部笔记").assertIsDisplayed()
        val trashed = library.getNote(note.id)
        assertTrue(trashed?.isTrashed == true)
        assertNull(trashed?.notebookId)
        assertEquals("临时本", trashed?.originalNotebookName)
        assertTrue(library.observeTrashedNotes().first().any { it.id == note.id })
    }
}
