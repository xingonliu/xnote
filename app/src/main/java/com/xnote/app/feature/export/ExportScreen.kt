package com.xnote.app.feature.export

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.xnote.app.R
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.design.*
import com.xnote.app.design.liquidglass.LiquidButton
import com.xnote.app.domain.document.attachmentIds
import com.xnote.app.domain.model.*
import com.xnote.app.feature.reader.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

// -- Functions

@Composable
fun ExportScreen(noteId: String, library: NoteLibrary, defaultBackground: BackgroundKey, onBack: () -> Unit) {
    // Freeze all visual inputs for the lifetime of this export, including theme and font scale.
    val initialDensity = LocalDensity.current
    val currentReadingLayout = LocalReadingLayout.current
    val readingLayout = remember { currentReadingLayout }
    val currentColors = MaterialTheme.colorScheme
    val currentTypography = MaterialTheme.typography
    val density = remember { initialDensity }
    val colors = remember { currentColors }
    val typography = remember { currentTypography }
    val defaultSnapshot = remember { defaultBackground }
    val context = LocalContext.current
    val backdrop = rememberLayerBackdrop()
    val toast = rememberXNoteToastHostState()
    val scope = rememberCoroutineScope()
    val owner = remember { "export-${UUID.randomUUID()}" }
    var note by remember { mutableStateOf<Note?>(null) }
    var attachments by remember { mutableStateOf<Map<String, Attachment>?>(null) }
    var directory by remember { mutableStateOf<File?>(null) }
    var failure by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var completed by remember { mutableIntStateOf(0) }
    var selected by remember { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var controlsHeight by remember { mutableIntStateOf(0) }
    val shareLabel = stringResource(R.string.export_share)
    val errorMessage = stringResource(R.string.export_failed)
    val untitled = stringResource(R.string.notes_untitled)
    DisposableEffect(library, owner) {
        onDispose { library.releaseSessionAttachments(owner) }
    }
    LaunchedEffect(noteId, attempt) {
        failure = false
        completed = 0
        selected = 0
        saved = false
        directory = null
        attachments = null
        try {
            val snapshot = note ?: library.getNote(noteId)?.takeUnless { it.isTrashed } ?: error("Missing note")
            note = snapshot
            val ids = snapshot.document.attachmentIds()
            library.retainSessionAttachments(owner, ids)
            val metadata = ids.mapNotNull { id -> library.getAttachment(id)?.let { id to it } }.toMap()
            directory = withContext(Dispatchers.IO) { createExportDirectory(context) }
            attachments = metadata
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            failure = true
        }
    }
    MaterialTheme(colorScheme = colors, typography = typography) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val insets = WindowInsets.safeDrawing.asPaddingValues()
            val horizontal = if (maxWidth >= 600.dp) 24.dp else XNoteSpacingMedium
            val contentWidth = minOf(readingLayout.widthDp.dp, (maxWidth - horizontal * 2).coerceAtLeast(1.dp))
            val margin = 16.dp
            val availableHeight = (maxHeight - insets.calculateTopPadding() - insets.calculateBottomPadding() - XNoteHeaderHeight - 180.dp).coerceAtLeast(120.dp)
            // Keep the reader's logical text width while bounding each raster to 2048 pixels per side.
            val pageWidth = remember { contentWidth + margin * 2 }
            val pageHeight = remember { availableHeight }
            val rasterDensity = remember {
                Density(minOf(density.density, 2048f / maxOf(pageWidth.value, pageHeight.value)), density.fontScale)
            }
            CompositionLocalProvider(LocalDensity provides rasterDensity) {
                val measurer = rememberTextMeasurer(cacheSize = 128)
                val pages = remember(note, attachments, attempt) {
                    if (note == null || attachments == null) emptyList() else {
                        val width = with(rasterDensity) { (pageWidth - margin * 2).roundToPx() }.coerceAtLeast(1)
                        val height = with(rasterDensity) { (pageHeight - margin * 2).toPx() }.coerceAtLeast(1f)
                        paginateReadingUnits(measureReadingUnits(listOf(note!!), attachments.orEmpty(), measurer,
                            typography, colors, width, height, rasterDensity.density, untitled, readingLayout.lineHeightScale), height)
                    }
                }
                val ready = pages.isNotEmpty() && completed == pages.size && !failure
                val latestReady by rememberUpdatedState(ready)
                DisposableEffect(directory) {
                    val currentDirectory = directory
                    onDispose { if (!latestReady) currentDirectory?.deleteRecursively() }
                }
                val savedMessage = stringResource(R.string.export_saved, pages.size)
                val files = remember(directory, pages.size) {
                    directory?.let { dir -> pages.indices.map { File(dir, "page-${(it + 1).toString().padStart(4, '0')}.png") } }.orEmpty()
                }
                CompositionLocalProvider(LocalDensity provides initialDensity) {
                    XNotePageScaffold(
                        backdrop = backdrop,
                        toastHostState = toast,
                        scrollEdges = setOf(XNoteScrollEdge.Top, XNoteScrollEdge.Bottom),
                        alwaysVisibleScrollEdges = setOf(XNoteScrollEdge.Top, XNoteScrollEdge.Bottom),
                        content = {
                            Box(Modifier.fillMaxSize().padding(top = insets.calculateTopPadding() + XNoteHeaderHeight + 16.dp,
                                bottom = if (controlsHeight == 0) insets.calculateBottomPadding() + 156.dp
                                else with(initialDensity) { controlsHeight.toDp() } + 16.dp), contentAlignment = Alignment.Center) {
                                when {
                                    failure -> XNoteErrorState(errorMessage, backdrop, actionLabel = stringResource(R.string.export_retry),
                                        onAction = { attempt++ })
                                    ready -> ExportPreview(files[selected], Modifier.fillMaxSize().padding(horizontal = horizontal), { failure = true })
                                    else -> {
                                        if (pages.isNotEmpty() && files.isNotEmpty()) {
                                            key(attempt, completed) {
                                                CompositionLocalProvider(LocalDensity provides rasterDensity) {
                                                    ExportPageCapture(pages[completed], library,
                                                        resolveBackgroundKey(note?.backgroundKey, defaultSnapshot), pageWidth,
                                                        if (pages.size == 1) with(rasterDensity) {
                                                            pages.first().units.sumOf { it.height.toDouble() }.toFloat().toDp() + margin * 2
                                                        } else pageHeight,
                                                        margin, files[completed], { completed++ }, { failure = true })
                                                }
                                            }
                                        }
                                        XNoteLoadingState(backdrop = backdrop)
                                    }
                                }
                            }
                        },
                        overlay = {
                            XNoteHeader(stringResource(R.string.export_title), backdrop, onBack = onBack,
                                actions = listOf(XNoteHeaderAction(R.drawable.ic_keyline_stroke_share,
                                    stringResource(R.string.export_share), {
                                        try {
                                            context.startActivity(Intent.createChooser(exportShareIntent(context, files), shareLabel))
                                        } catch (_: Exception) { scope.launch { toast.showSnackbar(errorMessage) } }
                                    }, enabled = ready && !saving)), modifier = Modifier.align(Alignment.TopCenter))
                            Column(Modifier.align(Alignment.BottomCenter).onSizeChanged { controlsHeight = it.height }.navigationBarsPadding().padding(horizontal = horizontal, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(stringResource(R.string.export_background_hint), style = typography.labelMedium, color = colors.onSurface)
                                Text(if (ready) stringResource(R.string.export_page, selected + 1, pages.size)
                                    else stringResource(R.string.export_progress, minOf(completed + 1, pages.size), pages.size),
                                    modifier = Modifier.testTag("xnote-export-progress"), style = typography.labelMedium, color = colors.onSurface)
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    LiquidButton({ selected-- }, backdrop, enabled = ready && selected > 0,
                                        modifier = Modifier.testTag("xnote-export-previous")) { Text(stringResource(R.string.reader_previous)) }
                                    LiquidButton({ selected++ }, backdrop, enabled = ready && selected < pages.lastIndex,
                                        modifier = Modifier.testTag("xnote-export-next")) { Text(stringResource(R.string.reader_next)) }
                                }
                                LiquidButton(onClick = {
                                    saving = true
                                    scope.launch {
                                        try {
                                            saveExportToGallery(context, files)
                                            saved = true
                                            toast.showSnackbar(savedMessage)
                                        } catch (error: CancellationException) { throw error }
                                        catch (_: Exception) { toast.showSnackbar(errorMessage) }
                                        finally { saving = false }
                                    }
                                }, backdrop = backdrop, enabled = ready && !saving && !saved,
                                    modifier = Modifier.testTag("xnote-export-save")) {
                                    Text(stringResource(if (saving) R.string.export_working else R.string.export_save))
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportPreview(file: File, modifier: Modifier, onError: () -> Unit) {
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, file) {
        try {
            value = withContext(Dispatchers.IO) { checkNotNull(android.graphics.BitmapFactory.decodeFile(file.path)).asImageBitmap() }
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { onError() }
    }
    bitmap?.let { Image(it, stringResource(R.string.export_title), modifier.testTag("xnote-export-preview")) }
}
