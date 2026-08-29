package com.xnote.app.feature.background

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kyant.backdrop.Backdrop
import com.xnote.app.R
import com.xnote.app.data.background.NoteBackgroundResolution
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.data.settings.AppSettingsRepository
import com.xnote.app.design.XNoteMaximumContentWidth
import com.xnote.app.design.XNoteMinimumTouchTarget
import com.xnote.app.design.XNoteSpacingLarge
import com.xnote.app.design.XNoteSpacingMedium
import com.xnote.app.design.liquidglass.LiquidButton
import com.xnote.app.domain.model.BackgroundKey
import com.xnote.app.domain.model.DefaultBuiltinBackgroundId
import com.xnote.app.domain.model.encode
import com.xnote.app.domain.model.parseBackgroundKey
import kotlinx.coroutines.launch

// -- Functions

@Composable
fun DefaultBackgroundScreen(
    defaultBackgroundKey: String,
    resolution: NoteBackgroundResolution,
    library: NoteLibrary,
    settings: AppSettingsRepository,
    backdrop: Backdrop,
    toastHostState: SnackbarHostState,
    contentPadding: PaddingValues,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val importController = rememberUserBackgroundImportController(
        library = library,
        toastHostState = toastHostState,
        onImported = { key -> settings.setDefaultBackgroundKey(key.encode()) },
    )
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(contentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = XNoteMaximumContentWidth)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(XNoteSpacingLarge),
        ) {
            XNoteBackgroundPicker(
                selectedKey = parseBackgroundKey(defaultBackgroundKey),
                resolvedBackground = resolution.background,
                scopeDescription = stringResource(R.string.background_scope_default),
                backdrop = backdrop,
                onSelect = { selected ->
                    val key = selected ?: BackgroundKey.Builtin(DefaultBuiltinBackgroundId)
                    scope.launch { settings.setDefaultBackgroundKey(key.encode()) }
                },
                onImport = importController.launch,
                isImporting = importController.isImporting,
            )
            LiquidButton(
                onClick = {
                    scope.launch {
                        settings.setDefaultBackgroundKey(
                            BackgroundKey.Builtin(DefaultBuiltinBackgroundId).encode(),
                        )
                    }
                },
                backdrop = backdrop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(XNoteMinimumTouchTarget),
                height = XNoteMinimumTouchTarget,
                contentPadding = PaddingValues(horizontal = XNoteSpacingMedium),
            ) {
                Text(
                    text = stringResource(R.string.background_restore_initial),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
