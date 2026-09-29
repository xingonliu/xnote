package com.xnote.app

import android.content.ClipboardManager
import android.content.Context
import android.view.View
import android.view.inputmethod.BaseInputConnection
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.xnote.app.data.db.XNoteDatabase
import com.xnote.app.data.files.AttachmentFileStore
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.design.XNoteTheme
import com.xnote.app.domain.document.*
import com.xnote.app.domain.model.SystemEpochClock
import com.xnote.app.feature.notes.editor.NoteEditorScreen
import com.xnote.app.feature.notes.editor.NoteEditorSession
import com.xnote.app.feature.notes.editor.DocumentSelectionController
import com.xnote.app.feature.notes.editor.DocumentSelectionInputConnection
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

// -- Tests

class DocumentSelectionFlowTest {
    // -- State and Variables

    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = XNoteDatabase.createInMemory(context)
    private val library = NoteLibrary(db, AttachmentFileStore(File(context.cacheDir, "selection-flow")), SystemEpochClock)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var session: NoteEditorSession

    // -- Functions

    @After fun close() = runBlocking {
        scope.coroutineContext[Job]?.cancelAndJoin()
        db.close()
    }

    private fun open() {
        val note = runBlocking {
            val created = library.createNote(null)
            library.saveNote(created.copy(title = "选区测试", document = NoteDocument(blocks = listOf(
                TextBlock("first", inlines = listOf(InlineRun("第一段文字"))),
                TextBlock("middle", inlines = listOf(InlineRun("第二段文字"))),
                TextBlock("last", inlines = listOf(InlineRun("第三段文字"))),
            ))))
        }
        session = NoteEditorSession(library, note.id, scope)
        compose.setContent {
            XNoteTheme(reduceMotion = true) {
                NoteEditorScreen(session, rememberLayerBackdrop(), PaddingValues(24.dp), rememberScrollState())
            }
        }
        compose.waitUntil(5000) { session.note != null }
        compose.onNodeWithTag("xnote-editor-body").performTextInputSelection(TextRange(0, 2))
        compose.onNodeWithTag("xnote-selection-menu").assertExists()
    }

    @Test fun selectAllCopiesEveryParagraph() {
        open()
        compose.onNodeWithTag("xnote-selection-all").performClick()
        compose.runOnIdle { assertTrue(session.selection.isCrossField) }
        compose.onNodeWithTag("xnote-selection-copy").performClick()
        compose.runOnIdle {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            assertEquals("第一段文字\n第二段文字\n第三段文字", clipboard.primaryClip!!.getItemAt(0).text.toString())
            assertTrue(session.selection.isCollapsed)
        }
    }

    @Test fun typingReplacesAllAndUndoRestoresDocumentSelection() {
        open()
        compose.onNodeWithTag("xnote-selection-all").performClick()
        compose.onNodeWithTag("xnote-editor-body").performTextInput("替换")
        compose.runOnIdle {
            assertEquals("替换", (session.document.blocks.single() as TextBlock).inlines.plainText())
            session.undo()
        }
        compose.runOnIdle {
            assertEquals(3, session.document.blocks.size)
            assertTrue(session.selection.isCrossField)
        }
        compose.onNodeWithTag("xnote-selection-all").assertExists()
    }

    @Test fun draggingEndHandleCrossesParagraphBoundary() {
        open()
        val target = compose.onNodeWithTag("xnote-editor-text-last").fetchSemanticsNode().boundsInRoot
        val handle = compose.onNodeWithTag("xnote-selection-end").fetchSemanticsNode().boundsInRoot
        val delta = Offset(target.right - 2f, target.center.y) - handle.center
        compose.onNodeWithTag("xnote-selection-end").performTouchInput {
            down(center)
            moveBy(delta, delayMillis = 300)
            up()
        }
        compose.runOnIdle {
            assertEquals("last", session.selection.endBlockId)
            assertEquals("第一段文字\n第二段文字\n第三段文字", session.document.selectedText(session.selection))
        }
    }

    @Test fun cutAllLeavesEditableBody() {
        open()
        compose.onNodeWithTag("xnote-selection-all").performClick()
        compose.onNodeWithTag("xnote-selection-cut").performClick()
        compose.onNodeWithTag("xnote-editor-body").performTextInput("重新输入")
        compose.runOnIdle {
            assertEquals("重新输入", (session.document.blocks.single() as TextBlock).inlines.plainText())
        }
    }

    @Test fun imeBackspaceDeletesDocumentRangeEvenWithAnEmptyLocalField() {
        open()
        compose.runOnIdle {
            session.select(EditorSelection("first", 0, 5))
            session.replaceSelection("")
            val controller = DocumentSelectionController(session, context)
            controller.selectAll()
            val connection = DocumentSelectionInputConnection(BaseInputConnection(View(context), false), controller)
            assertTrue(connection.deleteSurroundingTextInCodePoints(1, 0))
            assertEquals("", (session.document.blocks.single() as TextBlock).inlines.plainText())
            assertTrue(session.selection.isCollapsed)
        }
    }
}
