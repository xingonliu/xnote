package com.xnote.app.feature.background

import android.graphics.Bitmap
import androidx.activity.compose.LocalActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.core.graphics.scale
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
    content: @Composable (ImageBitmap?) -> Unit,
) {
    val inheritedDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val image = rememberBackgroundImage(if (active) background else defaultBackgroundKey(), library)
    var imageDark by remember(image) { mutableStateOf<Boolean?>(null) }
    val dark = if (active && settings.editorAutoThemeEnabled) imageDark ?: inheritedDark else inheritedDark
    val window = LocalActivity.current?.window
    val restoredDark by rememberUpdatedState(inheritedDark)

    LaunchedEffect(image, settings.editorAutoThemeEnabled) {
        imageDark = if (settings.editorAutoThemeEnabled && image != null) {
            withContext(Dispatchers.Default) {
                val source = image.asAndroidBitmap()
                val scaled = source.scale(minOf(64, source.width), minOf(64, source.height))
                // ImageDecoder may return a GPU bitmap; pixel access requires software storage.
                val sample = if (scaled.config == Bitmap.Config.HARDWARE) scaled.copy(Bitmap.Config.ARGB_8888, false) else scaled
                try {
                    val pixels = IntArray(sample.width * sample.height)
                    sample.getPixels(pixels, 0, sample.width, 0, 0, sample.width, sample.height)
                    backgroundImageUsesDarkTheme(pixels)
                } finally {
                    if (sample !== scaled) sample.recycle()
                    if (scaled !== source) scaled.recycle()
                }
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
    ) { content(image) }
}
