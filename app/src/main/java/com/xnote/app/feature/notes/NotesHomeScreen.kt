package com.xnote.app.feature.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xnote.app.R
import com.xnote.app.design.XNoteLiquidGlassPanel
import com.xnote.app.design.XNoteSpacingLarge
import com.xnote.app.design.XNoteSpacingMedium
import com.xnote.app.design.XNoteSpacingSmall
import com.xnote.app.design.liquidglass.LiquidButton

// -- Composables

@Composable
fun NotesHomeScreen(
    backdrop: Backdrop,
    contentPadding: PaddingValues,
    onCreateNote: () -> Unit,
    createEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
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
            XNoteLiquidGlassPanel(
                backdrop = backdrop,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(XNoteSpacingLarge),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(XNoteSpacingSmall),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lucide_notebook_pen),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp),
                    )
                    Text(
                        text = stringResource(R.string.notes_empty_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(R.string.notes_empty_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    LiquidButton(
                        onClick = onCreateNote,
                        backdrop = backdrop,
                        enabled = createEnabled,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = XNoteSpacingSmall),
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = XNoteSpacingMedium,
                                vertical = 10.dp,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(XNoteSpacingSmall),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_lucide_plus),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = stringResource(R.string.action_create_note),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }
        }
    }
}
