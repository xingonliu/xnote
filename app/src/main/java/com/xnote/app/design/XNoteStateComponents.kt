package com.xnote.app.design

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xnote.app.R
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

// -- Functions

@Composable
fun XNoteLoadingState(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    message: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = XNoteSpacingLarge * 2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(XNoteSpacingMedium),
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = XNoteSpacingLarge, vertical = XNoteSpacingLarge * 2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(XNoteSpacingMedium),
    ) {
        if (iconRes != null) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(XNoteIconSizeHero),
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 420.dp),
            )
        }
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
                        horizontal = XNoteSpacingMedium + 4.dp,
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
                            modifier = Modifier.size(XNoteIconSizeSmall),
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = XNoteSpacingLarge, vertical = XNoteSpacingLarge * 2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(XNoteSpacingMedium),
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .background(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_keyline_stroke_bin),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(XNoteIconSizeHero),
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
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
                    modifier = Modifier.widthIn(max = 420.dp),
                )
            }
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
                    modifier = Modifier.padding(
                        horizontal = XNoteSpacingMedium + 4.dp,
                        vertical = 10.dp,
                    ),
                )
            }
        }
    }
}

@Composable
fun XNoteGroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = XNoteSmoothCornerShape(XNoteCardRadius)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f), shape)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                shape = shape,
            ),
        content = content,
    )
}

@Composable
fun XNoteInsetDivider(
    modifier: Modifier = Modifier,
    startIndent: Dp = XNoteSpacingMedium,
    endIndent: Dp = 0.dp,
) {
    HorizontalDivider(
        modifier = modifier.padding(start = startIndent, end = endIndent),
        thickness = 0.6.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f),
    )
}
