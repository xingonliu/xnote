package com.xnote.app.feature.notes.editor

import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import com.xnote.app.domain.document.selectedText

// -- Type Definitions

internal class DocumentSelectionInputConnection(
    connection: InputConnection,
    private val controller: DocumentSelectionController,
) : InputConnectionWrapper(connection, false) {
    // -- Functions

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        if (deleteSelection(beforeLength > 0 || afterLength > 0)) return true
        return super.deleteSurroundingText(beforeLength, afterLength)
    }

    override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
        if (deleteSelection(beforeLength > 0 || afterLength > 0)) return true
        return super.deleteSurroundingTextInCodePoints(beforeLength, afterLength)
    }

    override fun sendKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN &&
            deleteSelection(event.keyCode == KeyEvent.KEYCODE_DEL || event.keyCode == KeyEvent.KEYCODE_FORWARD_DEL)) return true
        return super.sendKeyEvent(event)
    }

    override fun getSelectedText(flags: Int): CharSequence? = if (controller.active)
        controller.session.document.selectedText(controller.selection) else super.getSelectedText(flags)

    override fun performContextMenuAction(id: Int): Boolean {
        if (id == android.R.id.selectAll) { controller.selectAll(); return true }
        if (controller.active) when (id) {
            android.R.id.copy -> { controller.copy(); return true }
            android.R.id.cut -> { controller.copy(true); return true }
            android.R.id.paste -> { controller.paste(); return true }
        }
        return super.performContextMenuAction(id)
    }

    private fun deleteSelection(requested: Boolean): Boolean {
        if (!requested || !controller.active) return false
        controller.session.replaceSelection("")
        return true
    }
}

// -- Functions

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun DocumentSelectionInput(controller: DocumentSelectionController?, content: @Composable () -> Unit) {
    val interceptor = remember(controller) {
        PlatformTextInputInterceptor { request, next ->
            if (controller == null) next.startInputMethod(request)
            next.startInputMethod(object : PlatformTextInputMethodRequest {
                override fun createInputConnection(outAttributes: EditorInfo): InputConnection =
                    DocumentSelectionInputConnection(request.createInputConnection(outAttributes), controller)
            })
        }
    }
    InterceptPlatformTextInput(interceptor, content)
}
