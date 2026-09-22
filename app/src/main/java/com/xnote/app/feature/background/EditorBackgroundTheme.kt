package com.xnote.app.feature.background

import androidx.activity.compose.LocalActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.IntSize
import androidx.core.view.WindowCompat
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.design.XNoteTheme
import com.xnote.app.domain.model.AppSettings
import com.xnote.app.domain.model.BackgroundKey
import com.xnote.app.domain.model.backgroundImageUsesDarkTheme
import com.xnote.app.domain.model.defaultBackgroundKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// -- Functions

@Composable
fun EditorBackgroundTheme(
    background: BackgroundKey,
    library: NoteLibrary,
    settings: AppSettings,
    active: Boolean,
    updateSystemBars: Boolean = false,
    content: @Composable (ImageBitmap?, (IntSize) -> Unit) -> Unit,
) {
    val inheritedDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val image = rememberBackgroundImage(if (active) background else defaultBackgroundKey(), library)
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val onViewportSizeChanged: (IntSize) -> Unit = remember { { viewport = it } }
    var imageDark by remember(image) { mutableStateOf<Boolean?>(null) }
    val dark = if (active && settings.editorAutoThemeEnabled) imageDark ?: inheritedDark else inheritedDark
    val window = LocalActivity.current?.window
    val restoredDark by rememberUpdatedState(inheritedDark)

    LaunchedEffect(image, viewport, settings.editorAutoThemeEnabled) {
        imageDark = if (settings.editorAutoThemeEnabled && image != null && viewport.width > 0 && viewport.height > 0) {
            withContext(Dispatchers.Default) {
                backgroundImageUsesDarkTheme(sampleVisibleBackground(image.asAndroidBitmap(), viewport), BackgroundThemeSampleSize)
            }
        } else null
    }
    if (updateSystemBars && window != null) {
        SideEffect {
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
        DisposableEffect(window) {
            onDispose {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !restoredDark
                    isAppearanceLightNavigationBars = !restoredDark
                }
            }
        }
    }
    XNoteTheme(
        darkTheme = dark,
        reduceMotion = settings.reduceMotion,
        highContrast = settings.highContrast,
        readingLayout = settings.readingLayout,
    ) { content(image, onViewportSizeChanged) }
}
