package com.xnote.app.feature.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xnote.app.R
import com.xnote.app.design.XNoteMinimumTouchTarget
import com.xnote.app.design.XNoteIconSizeMedium
import com.xnote.app.design.XNoteSpacingSmall
import com.xnote.app.design.liquidglass.LiquidButton
import com.xnote.app.feature.notes.editor.NoteEditorSession

// -- Composables

@Composable
internal fun MarkdownEditorToolbarBar(
    session: NoteEditorSession,
    backdrop: Backdrop,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(XNoteSpacingSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MarkdownHistoryButton(
            onClick = session::undo,
            enabled = session.canUndo,
            iconRes = R.drawable.ic_keyline_stroke_arrow_u_turn_left,
            contentDescription = stringResource(R.string.action_undo),
            backdrop = backdrop,
        )
        MarkdownHistoryButton(
            onClick = session::redo,
            enabled = session.canRedo,
            iconRes = R.drawable.ic_keyline_stroke_arrow_u_turn_right,
            contentDescription = stringResource(R.string.action_redo),
            backdrop = backdrop,
        )
        Spacer(modifier = Modifier.weight(1f))
        LiquidButton(
            onClick = onDone,
            backdrop = backdrop,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(XNoteMinimumTouchTarget)
                .testTag("xnote-markdown-done"),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_keyline_stroke_check),
                contentDescription = stringResource(R.string.editor_markdown_done),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(XNoteIconSizeMedium),
            )
        }
    }
}

@Composable
private fun MarkdownHistoryButton(
    onClick: () -> Unit,
    enabled: Boolean,
    iconRes: Int,
    contentDescription: String,
    backdrop: Backdrop,
) {
    LiquidButton(
        onClick = onClick,
        backdrop = backdrop,
        enabled = enabled,
        modifier = Modifier.size(XNoteMinimumTouchTarget),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(XNoteIconSizeMedium),
        )
    }
}
