package com.xnote.app.feature.recycle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xnote.app.R
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.design.XNoteDialog
import com.xnote.app.design.XNoteDialogAction
import com.xnote.app.design.XNoteDropdownMenu
import com.xnote.app.design.XNoteDropdownMenuItem
import com.xnote.app.design.XNoteHeader
import com.xnote.app.design.XNoteHeaderAction
import com.xnote.app.design.XNoteMinimumTouchTarget
import com.xnote.app.design.XNoteSpacingMedium
import com.xnote.app.design.XNoteSpacingSmall
import com.xnote.app.design.liquidglass.LiquidButton
import com.xnote.app.domain.model.Note
import kotlinx.coroutines.launch

// -- Constants

val XNoteRecycleSelectionHeight = 64.dp

// -- Composables

@Composable
fun BoxScope.RecycleBinChrome(
    notes: List<Note>,
    ui: RecycleBinUiState,
    library: NoteLibrary,
    backdrop: Backdrop,
    isTablet: Boolean,
    toastHostState: SnackbarHostState,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val restoredMessage = stringResource(R.string.recycle_bin_restored)
    XNoteHeader(
        title = stringResource(R.string.recycle_bin_title),
        backdrop = backdrop,
        onBack = onBack,
        actions = if (notes.isEmpty()) {
            emptyList()
        } else {
            listOf(
                XNoteHeaderAction(
                    iconRes = if (ui.selectionMode) {
                        R.drawable.ic_lucide_check
                    } else {
                        R.drawable.ic_lucide_square_check
                    },
                    contentDescription = stringResource(
                        if (ui.selectionMode) {
                            R.string.notes_cancel_selection
                        } else {
                            R.string.recycle_bin_select
                        },
                    ),
                    onClick = {
                        if (ui.selectionMode) {
                            ui.finishSelection()
                        } else {
                            ui.selectionMode = true
                        }
                    },
                ),
                XNoteHeaderAction(
                    iconRes = R.drawable.ic_lucide_ellipsis,
                    contentDescription = stringResource(R.string.action_more),
                    onClick = { ui.moreVisible = true },
                ),
            )
        },
        horizontalPadding = if (isTablet) 24.dp else XNoteSpacingMedium,
        modifier = Modifier.align(Alignment.TopCenter),
    )

    if (ui.selectionMode) {
        RecycleSelectionBar(
            count = ui.selectedIds.size,
            restoreEnabled = ui.selectedIds.isNotEmpty(),
            onRestore = {
                val ids = ui.selectedIds
                scope.launch {
                    library.restoreNotes(ids)
                    ui.finishSelection()
                    toastHostState.showSnackbar(restoredMessage)
                }
            },
            onPermanentlyDelete = {
                ui.pendingPermanentDeleteIds = ui.selectedIds
            },
            onCancel = ui::finishSelection,
            backdrop = backdrop,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(
                    horizontal = if (isTablet) 24.dp else XNoteSpacingMedium,
                    vertical = XNoteSpacingSmall,
                ),
        )
    }

    XNoteDropdownMenu(
        expanded = ui.moreVisible,
        onDismissRequest = { ui.moreVisible = false },
        items = listOf(
            XNoteDropdownMenuItem(
                label = stringResource(R.string.recycle_bin_empty_action),
                enabled = notes.isNotEmpty(),
                destructive = true,
                onClick = { ui.emptyTrashConfirmVisible = true },
            ),
        ),
        backdrop = backdrop,
        alignment = Alignment.TopEnd,
        modifier = Modifier.padding(top = 72.dp, end = XNoteSpacingMedium),
    )

    XNoteDialog(
        visible = ui.pendingPermanentDeleteIds.isNotEmpty(),
        onDismissRequest = { ui.pendingPermanentDeleteIds = emptySet() },
        title = stringResource(R.string.recycle_bin_delete_permanently),
        backdrop = backdrop,
        confirmAction = XNoteDialogAction(
            label = stringResource(R.string.action_delete),
            destructive = true,
            onClick = {
                val ids = ui.pendingPermanentDeleteIds
                scope.launch {
                    library.permanentlyDeleteNotes(ids)
                    ui.pendingPermanentDeleteIds = emptySet()
                    ui.finishSelection()
                }
            },
        ),
        dismissAction = XNoteDialogAction(
            label = stringResource(R.string.action_cancel),
            onClick = { ui.pendingPermanentDeleteIds = emptySet() },
        ),
    ) {
        Text(
            text = stringResource(R.string.recycle_bin_delete_permanently_message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }

    XNoteDialog(
        visible = ui.emptyTrashConfirmVisible,
        onDismissRequest = { ui.emptyTrashConfirmVisible = false },
        title = stringResource(R.string.recycle_bin_empty_action),
        backdrop = backdrop,
        confirmAction = XNoteDialogAction(
            label = stringResource(R.string.recycle_bin_empty_confirm),
            destructive = true,
            onClick = {
                scope.launch {
                    library.emptyTrash()
                    ui.emptyTrashConfirmVisible = false
                    ui.finishSelection()
                }
            },
        ),
        dismissAction = XNoteDialogAction(
            label = stringResource(R.string.action_cancel),
            onClick = { ui.emptyTrashConfirmVisible = false },
        ),
    ) {
        Text(
            text = stringResource(R.string.recycle_bin_empty_message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun RecycleSelectionBar(
    count: Int,
    restoreEnabled: Boolean,
    onRestore: () -> Unit,
    onPermanentlyDelete: () -> Unit,
    onCancel: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(XNoteSpacingSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.notes_selection_count, count),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        LiquidButton(
            onClick = onRestore,
            backdrop = backdrop,
            enabled = restoreEnabled,
            height = XNoteMinimumTouchTarget,
            contentPadding = PaddingValues(horizontal = XNoteSpacingMedium),
        ) {
            Text(
                text = stringResource(R.string.recycle_bin_restore),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        LiquidButton(
            onClick = onPermanentlyDelete,
            backdrop = backdrop,
            enabled = restoreEnabled,
            height = XNoteMinimumTouchTarget,
            contentPadding = PaddingValues(horizontal = XNoteSpacingMedium),
        ) {
            Text(
                text = stringResource(R.string.recycle_bin_delete_permanently),
                color = MaterialTheme.colorScheme.error,
            )
        }
        LiquidButton(
            onClick = onCancel,
            backdrop = backdrop,
            height = XNoteMinimumTouchTarget,
            contentPadding = PaddingValues(horizontal = XNoteSpacingMedium),
        ) {
            Text(
                text = stringResource(R.string.action_cancel),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
