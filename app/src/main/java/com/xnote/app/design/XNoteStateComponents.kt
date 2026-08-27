package com.xnote.app.design

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xnote.app.design.liquidglass.LiquidButton

// -- Type Definitions

sealed interface XNotePageState {
    data object Content : XNotePageState

    data class Loading(
        val message: String? = null,
    ) : XNotePageState

    data class Error(
        val title: String,
        val description: String? = null,
        val actionLabel: String? = null,
    ) : XNotePageState
}

// -- Composables

@Composable
fun XNoteLoadingState(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    message: String? = null,
) {
    XNoteStatePanel(
        backdrop = backdrop,
        modifier = modifier,
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 3.dp,
            modifier = Modifier.size(36.dp),
        )
        if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun XNoteEmptyState(
    title: String,
    description: String,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    @DrawableRes iconRes: Int? = null,
    actionLabel: String? = null,
    @DrawableRes actionIconRes: Int? = null,
    actionEnabled: Boolean = true,
    onAction: (() -> Unit)? = null,
) {
    XNoteStatePanel(
        backdrop = backdrop,
        modifier = modifier,
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            LiquidButton(
                onClick = onAction,
                backdrop = backdrop,
                enabled = actionEnabled,
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
                    if (actionIconRes != null) {
                        Icon(
                            painter = painterResource(actionIconRes),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
fun XNoteErrorState(
    title: String,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    description: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    XNoteStatePanel(
        backdrop = backdrop,
        modifier = modifier,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            LiquidButton(
                onClick = onAction,
                backdrop = backdrop,
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.16f),
                surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.20f),
                modifier = Modifier.padding(top = XNoteSpacingSmall),
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = XNoteSpacingMedium),
                )
            }
        }
    }
}

@Composable
private fun XNoteStatePanel(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    XNoteLiquidGlassPanel(
        backdrop = backdrop,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(XNoteSpacingLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(XNoteSpacingSmall),
            content = content,
        )
    }
}
