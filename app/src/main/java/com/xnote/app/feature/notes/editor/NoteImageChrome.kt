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
import com.xnote.app.domain.document.*
import com.xnote.app.feature.creative.*

// -- Type Definitions

@Stable
class NoteImageUiState {
    var addRequested by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var creativePage by mutableStateOf<String?>(null)
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
        items = if (replaceId == null) sources + listOf(XNoteDropdownMenuItem(
            stringResource(R.string.rich_text_action_table), onClick = {
                sourceVisible = false
                session.applyAction(com.xnote.app.design.XNoteRichTextAction.Table)
            },
        ), XNoteDropdownMenuItem("贴纸", onClick = { sourceVisible = false; ui.creativePage = "stickers" }),
            XNoteDropdownMenuItem("画板", onClick = { sourceVisible = false; ui.creativePage = "drawing" })) else sources,
        backdrop = backdrop, anchor = if (replaceId == null) sourceAnchor else replacementAnchor,
        placement = XNotePopupPlacement.AboveStart,
    )
    val target = EditorSelection(targetBlock, targetStart, targetStart)
    if (ui.creativePage == "stickers") StickerLibraryScreen(library, onBack = { ui.creativePage = null }, onInsert = { entry ->
        session.attachMedia(StickerBlock(newNoteId(), entry.attachmentId, libraryEntryId = entry.id), target)
        session.flushSave()
        ui.creativePage = null
    })
    val drawingId = session.drawingBlockId
    if (ui.creativePage == "drawing" || drawingId != null) {
        val initial = session.document.block(drawingId.orEmpty()) as? DrawingBlock
        DrawingScreen(library, session.attachmentOwner, initial,
            onBack = { ui.creativePage = null; session.drawingBlockId = null }, onSave = { drawing ->
                session.attachMedia(drawing, target, drawingId)
                session.flushSave()
                ui.creativePage = null
                session.drawingBlockId = null
            })
    }
    val cutoutId = session.cutoutImageId
    var cutoutFile by remember(cutoutId) { mutableStateOf<java.io.File?>(null) }
    LaunchedEffect(cutoutId) {
        if (cutoutId != null) {
            val block = session.document.block(cutoutId) as? PlacedMediaBlock
            cutoutFile = block?.let { session.imageFile(it.attachmentId) }
            if (cutoutFile == null) { session.cutoutImageId = null; toast.showSnackbar("图片无法读取") }
        }
    }
    cutoutFile?.let { file ->
        CutoutScreen(file, library, session.attachmentOwner, onBack = { session.cutoutImageId = null }, onInsert = { attachment ->
            val original = session.document.block(cutoutId.orEmpty()) as? PlacedMediaBlock
            if (original != null) {
                session.attachMedia(StickerBlock(original.id, attachment.id, layout = original.layout, scale = original.scale,
                    rotationDegrees = original.rotationDegrees, offsetX = original.offsetX, offsetY = original.offsetY, zIndex = original.zIndex),
                    EditorSelection(original.id), original.id)
                session.flushSave()
            }
            session.cutoutImageId = null
        })
    }
}
