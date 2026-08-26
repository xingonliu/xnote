package com.xnote.app.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.xnote.app.R

// -- Composables

@Composable
fun XNotePageScaffold(
    backdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
    overlay: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .layerBackdrop(backdrop),
            content = content,
        )
        overlay()
    }
}

@Composable
fun XNoteHeader(
    title: String,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = XNoteSpacingMedium)
            .height(XNoteHeaderHeight),
    ) {
        if (onBack != null) {
            XNoteLiquidGlassButton(
                onClick = onBack,
                backdrop = backdrop,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(XNoteMinimumTouchTarget),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lucide_arrow_left),
                    contentDescription = stringResource(R.string.action_back),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            Spacer(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(XNoteMinimumTouchTarget),
            )
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 64.dp),
        )

        if (onSearch != null) {
            XNoteLiquidGlassButton(
                onClick = onSearch,
                backdrop = backdrop,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(XNoteMinimumTouchTarget),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lucide_search),
                    contentDescription = stringResource(R.string.action_search),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            Spacer(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(XNoteMinimumTouchTarget),
            )
        }
    }
}
