package com.xnote.app

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextRange
import androidx.test.core.app.ApplicationProvider
import com.xnote.app.data.agent.*
import com.xnote.app.data.db.*
import com.xnote.app.data.files.AttachmentFileStore
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.design.XNoteTheme
import com.xnote.app.domain.agent.*
import com.xnote.app.domain.document.*
import com.xnote.app.domain.model.SystemEpochClock
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

// -- Tests

class EditorAgentFlowTest {
    // -- State and Variables

    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = XNoteDatabase.createInMemory(context)
    private val library = NoteLibrary(db, AttachmentFileStore(File(context.cacheDir, "editor-agent-flow")), SystemEpochClock)

    // -- Functions

    @After fun close() = db.close()

    @Test fun openEditorRefreshesAgentBodyAndContinuesTypingWithoutStaleOverwrite() {
        val note = runBlocking {
            db.agent().saveSegment(AgentSegmentEntity("segment", 1))
            db.agent().saveRun(AgentRunEntity("run", "segment", "user", "profile", 1, AgentRunStatus.Running, 1, 1))
            AgentPermissionStore(db).saveFromUser(AgentPermission(AgentPermissionLevel.Edit, AgentScope.All))
            val created = library.createNote(null)
            library.saveNote(requireNotNull(library.getNote(created.id)).copy(title = "并行编辑验收", document = document("原始正文")))
        }
        compose.setContent { XNoteTheme(reduceMotion = true) { XNoteApp(library) } }
        compose.onNodeWithTag("xnote-collection-unfiled").performClick()
        compose.onNodeWithText("并行编辑验收").performClick()
        compose.onNodeWithTag("xnote-editor-body").assertTextContains("原始正文")
        runBlocking {
            val current = db.notes().get(note.id)!!
            assertTrue(AgentReviewStore(db).applyEdit("run", "write", current.editBase(), AgentEditableContent(note.title, document("Agent 整理后的正文"))) is AgentReviewResult.Applied)
        }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Agent 整理后的正文").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("xnote-editor-body").performTextInputSelection(TextRange("Agent 整理后的正文".length))
        compose.onNodeWithTag("xnote-editor-body").performTextInput("；用户继续输入")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.waitUntil(5000) { runBlocking { library.getNote(note.id)!!.document.blocks.filterIsInstance<TextBlock>().single().inlines.plainText() == "Agent 整理后的正文；用户继续输入" } }
        compose.onNodeWithText("并行编辑验收").performClick()
        compose.onNodeWithTag("xnote-editor-body").assertTextContains("Agent 整理后的正文；用户继续输入")
        compose.onNodeWithContentDescription("返回").performClick()
    }

    private fun document(text: String) = NoteDocument(blocks = listOf(TextBlock("body", inlines = listOf(InlineRun(text)))))
}
