package com.xnote.app.feature.notes

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.xnote.app.design.liquidglass.LiquidSlider
import kotlin.math.roundToInt
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import com.xnote.app.design.liquidglass.LiquidButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import com.xnote.app.domain.model.defaultAppSettings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.kyant.backdrop.Backdrop
import com.xnote.app.R
import com.xnote.app.data.files.importNoteImage
import com.xnote.app.data.settings.AppSettingsRepository
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.design.XNoteDrawer
import com.xnote.app.design.XNoteDrawerPlacement
import com.xnote.app.domain.model.BackgroundKey
import com.xnote.app.feature.background.XNoteBackgroundPicker
import com.xnote.app.feature.background.rememberBackgroundImage
import com.xnote.app.feature.notes.editor.NoteEditorSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// -- Functions

@Composable
internal fun NoteBackgroundChrome(
    settings: AppSettingsRepository,
    session: NoteEditorSession,
    library: NoteLibrary,
    background: BackgroundKey,
    visible: Boolean,
    onDismiss: () -> Unit,
    backdrop: Backdrop,
    placement: XNoteDrawerPlacement,
    toast: SnackbarHostState,
) {
    val preferences by settings.settings.collectAsState(defaultAppSettings())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val failure = stringResource(R.string.background_save_failed)
    val unavailable = stringResource(R.string.image_source_unavailable)
    val image = rememberBackgroundImage(background, library)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && !busy) {
            busy = true
            scope.launch {
                try {
                    snapshotFlow { session.note != null || session.missing }.first { it }
                    check(!session.missing)
                    val attachment = importNoteImage(context, library, uri, session.attachmentOwner)
                    session.setBackground(BackgroundKey.Image(attachment.id,
                        (session.backgroundKey as? BackgroundKey.Image)?.maskOpacity ?: 72))
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    toast.showSnackbar(failure)
                } finally {
                    busy = false
                }
            }
        }
    }
    XNoteDrawer(
        visible = visible,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.background_picker_title),
        backdrop = backdrop,
        placement = placement,
    ) {
        Row(
            Modifier.fillMaxWidth().testTag("xnote-editor-auto-theme").toggleable(
                value = preferences.editorAutoThemeEnabled,
                role = Role.Switch,
                onValueChange = { enabled ->
                    scope.launch {
                        try { settings.setEditorAutoThemeEnabled(enabled) }
                        catch (error: CancellationException) { throw error }
                        catch (_: Exception) { toast.showSnackbar(failure) }
                    }
                },
            ).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 16.dp)) {
                Text(stringResource(R.string.editor_auto_theme_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.editor_auto_theme_summary), style = MaterialTheme.typography.bodyMedium)
            }
            Switch(checked = preferences.editorAutoThemeEnabled, onCheckedChange = null)
        }
        XNoteBackgroundPicker(
            selectedKey = session.backgroundKey,
            previewBackground = background,
            backgroundImage = image,
            scopeDescription = stringResource(R.string.background_scope_current_note),
            onSelect = { key ->
                if (!busy) {
                    busy = true
                    scope.launch {
                        try {
                            session.setBackground(key)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            toast.showSnackbar(failure)
                        } finally {
                            busy = false
                        }
                    }
                }
            },
            imageAction = {
                val imageBackground = session.backgroundKey as? BackgroundKey.Image
                if (imageBackground != null) {
                    var sliderValue by remember(imageBackground.attachmentId) {
                        mutableFloatStateOf(imageBackground.maskOpacity.toFloat())
                    }
                    Column {
                        Text(stringResource(R.string.background_mask_opacity, imageBackground.maskOpacity))
                        LiquidSlider(
                            value = { sliderValue },
                            onValueChange = {
                                sliderValue = it
                                session.setBackgroundMaskOpacity(it.roundToInt().coerceIn(0, 100))
                            },
                            valueRange = 0f..100f,
                            visibilityThreshold = 0.1f,
                            backdrop = backdrop,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)
                                .height(48.dp).testTag("xnote-background-opacity"),
                        )
                    }
                }
                LiquidButton(
                    onClick = {
                        try {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        } catch (_: Exception) {
                            scope.launch { toast.showSnackbar(unavailable) }
                        }
                    },
                    backdrop = backdrop,
                    enabled = !busy && session.note != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(when {
                        busy -> R.string.background_saving
                        session.backgroundKey is BackgroundKey.Image -> R.string.background_replace_image
                        else -> R.string.background_choose_image
                    }))
                }
            },
            enabled = !busy && session.note != null,
            allowDefaultInheritance = true,
        )
    }
}
