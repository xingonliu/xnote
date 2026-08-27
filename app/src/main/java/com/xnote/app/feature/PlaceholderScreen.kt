package com.xnote.app.feature

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kyant.backdrop.Backdrop
import com.xnote.app.design.XNoteEmptyState
import com.xnote.app.design.XNoteMaximumContentWidth

// -- Composables

@Composable
fun PlaceholderScreen(
    @StringRes titleRes: Int,
    @StringRes descriptionRes: Int,
    @DrawableRes iconRes: Int,
    backdrop: Backdrop,
    contentPadding: PaddingValues,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item {
            Box(
                modifier = Modifier.fillParentMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                XNoteEmptyState(
                    title = stringResource(titleRes),
                    description = stringResource(descriptionRes),
                    iconRes = iconRes,
                    backdrop = backdrop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = XNoteMaximumContentWidth),
                )
            }
        }
    }
}
