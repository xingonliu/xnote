package com.xnote.app.feature.notes.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xnote.app.R
import com.xnote.app.data.files.cameraFile
import com.xnote.app.data.files.cameraUri
import com.xnote.app.data.files.importNoteImage
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.design.XNoteDrawer
import com.xnote.app.design.XNoteDrawerPlacement
import com.xnote.app.design.XNoteDropdownMenu
import com.xnote.app.design.XNoteDropdownMenuItem
import com.xnote.app.design.XNotePopupPlacement
import com.xnote.app.design.XNotePopup
import com.xnote.app.design.liquidglass.LiquidButton
import com.xnote.app.design.rememberXNotePopupAnchor
import com.xnote.app.design.xNotePopupAnchor
import com.xnote.app.domain.document.EditorSelection
import com.xnote.app.domain.document.ImageAction
import com.xnote.app.domain.document.ImageBlock
import com.xnote.app.domain.document.block
import com.xnote.app.domain.model.newNoteId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// -- Composables

@Composable
fun BoxScope.NoteImageChrome(
    session: NoteEditorSession,
    library: NoteLibrary,
    backdrop: Backdrop,
    isTablet: Boolean,
    toast: SnackbarHostState,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val sourceAnchor = rememberXNotePopupAnchor()
    var sourceVisible by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
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
        busy = true
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
                busy = false
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
        if (busy) return
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
    LiquidButton(
        onClick = { openSources(null) }, backdrop = backdrop,
        enabled = !busy && session.note != null,
        modifier = modifier.xNotePopupAnchor(sourceAnchor).testTag("xnote-add-image"),
    ) {
        Text(stringResource(if (busy) R.string.image_importing else R.string.image_add), color = MaterialTheme.colorScheme.onSurface)
    }
    val selectedId = session.imageMenuId
    val selectedImage = selectedId?.let { session.document.block(it) as? ImageBlock }
    val operations = buildList {
        if (selectedImage != null) {
            add(XNoteDropdownMenuItem(stringResource(R.string.image_gesture), onClick = {}))
            ImageAction.entries.forEach { action ->
                add(XNoteDropdownMenuItem(
                    stringResource(action.labelRes()),
                    onClick = { session.editImage(selectedImage.id, action) },
                    destructive = action == ImageAction.Delete,
                ))
            }
            add(XNoteDropdownMenuItem(stringResource(R.string.image_replace), onClick = { openSources(selectedImage.id) }))
        }
    }
    if (isTablet) {
        XNotePopup(
            visible = selectedImage != null, onDismissRequest = { session.imageMenuId = null },
            backdrop = backdrop,
            anchor = selectedId?.let(session::imageAnchor),
        ) {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                operations.forEach { item -> ImageMenuRow(item) { session.imageMenuId = null } }
            }
        }
        XNoteDropdownMenu(
            expanded = sourceVisible, onDismissRequest = { sourceVisible = false }, items = sources,
            backdrop = backdrop, anchor = if (replaceId != null) session.imageAnchor(replaceId!!) else sourceAnchor,
            placement = XNotePopupPlacement.AboveStart,
        )
    } else {
        XNoteDrawer(
            visible = selectedImage != null, onDismissRequest = { session.imageMenuId = null },
            title = stringResource(R.string.image_edit), backdrop = backdrop, placement = XNoteDrawerPlacement.Bottom,
        ) {
            operations.forEach { item -> ImageMenuRow(item) { session.imageMenuId = null } }
        }
        XNoteDrawer(
            visible = sourceVisible, onDismissRequest = { sourceVisible = false },
            title = stringResource(R.string.image_add), backdrop = backdrop, placement = XNoteDrawerPlacement.Bottom,
        ) {
            sources.forEach { item -> ImageMenuRow(item) { sourceVisible = false } }
        }
    }
}

@Composable
private fun ImageMenuRow(item: XNoteDropdownMenuItem, dismiss: () -> Unit) {
    Text(
        item.label, style = MaterialTheme.typography.bodyLarge,
        color = if (item.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { item.onClick(); dismiss() }.padding(12.dp),
    )
}

// -- Functions

private fun ImageAction.labelRes(): Int = when (this) {
    ImageAction.Smaller -> R.string.image_smaller
    ImageAction.Larger -> R.string.image_larger
    ImageAction.RotateLeft -> R.string.image_rotate_left
    ImageAction.RotateRight -> R.string.image_rotate_right
    ImageAction.MoveUp -> R.string.image_move_up
    ImageAction.MoveDown -> R.string.image_move_down
    ImageAction.Backward -> R.string.image_backward
    ImageAction.Forward -> R.string.image_forward
    ImageAction.Reset -> R.string.image_reset
    ImageAction.Duplicate -> R.string.image_duplicate
    ImageAction.Delete -> R.string.image_delete
}
