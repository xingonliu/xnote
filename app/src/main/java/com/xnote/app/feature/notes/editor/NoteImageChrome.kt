package com.xnote.app.feature.notes.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import com.kyant.backdrop.Backdrop
import com.xnote.app.R
import com.xnote.app.data.files.cameraFile
import com.xnote.app.data.files.cameraUri
import com.xnote.app.data.files.importNoteImage
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.design.XNoteDropdownMenu
import com.xnote.app.design.XNoteDropdownMenuItem
import com.xnote.app.design.XNotePopupPlacement
import com.xnote.app.design.XNotePopupAnchor
import com.xnote.app.domain.document.EditorSelection
import com.xnote.app.domain.model.newNoteId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// -- Type Definitions

@Stable
class NoteImageUiState {
    var addRequested by mutableStateOf(false)
    var busy by mutableStateOf(false)
}

// -- Composables

@Composable
fun BoxScope.NoteImageChrome(
    session: NoteEditorSession,
    library: NoteLibrary,
    backdrop: Backdrop,
    toast: SnackbarHostState,
    ui: NoteImageUiState,
    sourceAnchor: XNotePopupAnchor,
) {
    val replacementAnchor = com.xnote.app.design.rememberXNotePopupAnchor()
    NoteImageControls(session, backdrop, replacementAnchor)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var sourceVisible by remember { mutableStateOf(false) }
    var replaceId by rememberSaveable(session.noteId) { mutableStateOf<String?>(null) }
    var targetBlock by rememberSaveable(session.noteId) { mutableStateOf("") }
    var targetStart by rememberSaveable(session.noteId) { mutableIntStateOf(0) }
    var cameraName by rememberSaveable(session.noteId) { mutableStateOf<String?>(null) }
    val failure = stringResource(R.string.image_import_failed)
    val unavailable = stringResource(R.string.image_source_unavailable)

    fun receive(uri: android.net.Uri?, temporaryName: String? = null) {
        if (uri == null) {
            temporaryName?.let { cameraFile(context, it).delete() }
            return
        }
        ui.busy = true
        val replacement = replaceId
        val target = EditorSelection(targetBlock, targetStart, targetStart)
        scope.launch {
            try {
                snapshotFlow { session.note != null || session.missing }.first { it }
                check(!session.missing)
                val attachment = importNoteImage(context, library, uri, session.attachmentOwner)
                session.attachImage(attachment.id, target, replacement)
                session.flushSave()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                toast.showSnackbar(failure)
            } finally {
                ui.busy = false
                temporaryName?.let { cameraFile(context, it).delete() }
            }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { receive(it) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val name = cameraName
        cameraName = null
        if (name != null) receive(if (success) cameraUri(context, name) else null, name)
    }
    fun openSources(replacement: String?) {
        if (ui.busy) return
        focus.clearFocus(force = true)
        keyboard?.hide()
        session.focusBlockId = null
        replaceId = replacement
        targetBlock = session.selection.blockId
        targetStart = session.selection.min
        sourceVisible = true
    }
    val sources = listOf(
        XNoteDropdownMenuItem(stringResource(R.string.image_camera), onClick = {
            sourceVisible = false
            try {
                val name = "${newNoteId()}.jpg"
                cameraName = name
                cameraFile(context, name).parentFile?.mkdirs()
                camera.launch(cameraUri(context, name))
            } catch (_: Exception) {
                cameraName?.let { cameraFile(context, it).delete() }
                cameraName = null
                scope.launch { toast.showSnackbar(unavailable) }
            }
        }),
        XNoteDropdownMenuItem(stringResource(R.string.image_gallery), onClick = {
            sourceVisible = false
            try {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            } catch (_: Exception) {
                scope.launch { toast.showSnackbar(unavailable) }
            }
        }),
    )
    LaunchedEffect(ui.addRequested) {
        if (ui.addRequested) {
            openSources(null)
            ui.addRequested = false
        }
    }
    LaunchedEffect(session.replaceImageId) {
        session.replaceImageId?.let { id ->
            openSources(id)
            session.replaceImageId = null
        }
    }
    XNoteDropdownMenu(
        expanded = sourceVisible, onDismissRequest = { sourceVisible = false },
        items = if (replaceId == null) sources + XNoteDropdownMenuItem(
            stringResource(R.string.rich_text_action_table), onClick = {
                sourceVisible = false
                session.applyAction(com.xnote.app.design.XNoteRichTextAction.Table)
            },
        ) else sources,
        backdrop = backdrop, anchor = if (replaceId == null) sourceAnchor else replacementAnchor,
        placement = XNotePopupPlacement.AboveStart,
    )
}
