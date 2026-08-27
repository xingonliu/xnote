package com.xnote.app.feature.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.kyant.backdrop.Backdrop
import com.xnote.app.R
import com.xnote.app.design.XNoteEmptyState
import com.xnote.app.design.XNoteSpacingMedium

// -- Composables

@Composable
fun NotesHomeScreen(
    backdrop: Backdrop,
    contentPadding: PaddingValues,
    listState: LazyListState,
    onCreateNote: () -> Unit,
    createEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(XNoteSpacingMedium),
    ) {
        item {
            Text(
                text = stringResource(R.string.notes_scope_all),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
        }

        item {
            XNoteEmptyState(
                title = stringResource(R.string.notes_empty_title),
                description = stringResource(R.string.notes_empty_description),
                iconRes = R.drawable.ic_lucide_notebook_pen,
                actionLabel = stringResource(R.string.action_create_note),
                actionIconRes = R.drawable.ic_lucide_plus,
                actionEnabled = createEnabled,
                onAction = onCreateNote,
                backdrop = backdrop,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
