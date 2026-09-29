package com.xnote.app.feature.notes.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.textSelectionRange
import androidx.compose.ui.text.TextLayoutResult
import com.xnote.app.domain.document.TextAddress
import com.xnote.app.domain.document.focus
import com.xnote.app.domain.document.inlinesAt
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import com.xnote.app.domain.document.InlineRun
import com.xnote.app.domain.document.plainText

// -- Functions

fun List<InlineRun>.toAnnotatedString(
    highlightColor: Color,
    linkColor: Color,
): AnnotatedString {
    val builder = AnnotatedString.Builder()
    for (run in this) {
        val start = builder.length
        builder.append(run.text)
        val decorations = buildList {
            if (run.underline || !run.linkUrl.isNullOrBlank()) add(TextDecoration.Underline)
            if (run.strikethrough) add(TextDecoration.LineThrough)
        }
        builder.addStyle(
            SpanStyle(
                fontWeight = if (run.bold) FontWeight.Bold else null,
                fontStyle = if (run.italic) FontStyle.Italic else null,
                textDecoration = if (decorations.isEmpty()) {
                    TextDecoration.None
                } else {
                    TextDecoration.combine(decorations)
                },
                background = if (run.highlight) highlightColor else Color.Unspecified,
                color = if (!run.linkUrl.isNullOrBlank()) linkColor else Color.Unspecified,
            ),
            start,
            builder.length,
        )
    }
    return builder.toAnnotatedString()
}

// -- Functions

@Composable
fun RichTextField(
    inlines: List<InlineRun>,
    fieldsEpoch: Int,
    textStyle: TextStyle,
    textAlign: TextAlign,
    focused: Boolean,
    onFocused: () -> Unit,
    onTextChange: (oldText: String, newText: String, selection: TextRange, composing: Boolean) -> Unit,
    onDeleteBackwardAtStart: () -> Unit,
    modifier: Modifier = Modifier,
    fieldTestTag: String? = null,
    placeholder: String = "",
    singleLine: Boolean = false,
    selection: TextRange? = null,
    nativeFlow: Boolean = false,
    exclusionWidth: Float = 0f,
    exclusionHeight: Float = 0f,
    address: TextAddress? = null,
) {
    val controller = LocalDocumentSelection.current.takeIf { address != null }
    if (nativeFlow) {
        WrappedTextField(inlines, fieldsEpoch, textStyle, textAlign, exclusionWidth, exclusionHeight, focused, selection,
            onFocused, onTextChange, onDeleteBackwardAtStart, modifier.then(if (fieldTestTag != null) Modifier.testTag(fieldTestTag) else Modifier), address)
        return
    }
    val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(inlines, highlightColor, linkColor) {
        inlines.toAnnotatedString(highlightColor, linkColor)
    }
    var value by remember {
        val caret = selection?.let { range ->
            TextRange(
                range.start.coerceIn(0, annotated.length),
                range.end.coerceIn(0, annotated.length),
            )
        } ?: TextRange(annotated.length)
        mutableStateOf(TextFieldValue(annotated, caret))
    }
    val focusRequester = remember { FocusRequester() }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val selected = address?.let { controller?.range(it) }
    val selectionColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    val nativeToolbar = LocalTextToolbar.current
    val toolbar = remember(controller, nativeToolbar) {
        object : TextToolbar {
            override val status: TextToolbarStatus get() = nativeToolbar.status
            override fun hide() = nativeToolbar.hide()
            override fun showMenu(rect: Rect, onCopyRequested: (() -> Unit)?, onPasteRequested: (() -> Unit)?,
                onCutRequested: (() -> Unit)?, onSelectAllRequested: (() -> Unit)?) {
                if (controller?.active == true) nativeToolbar.hide()
                else nativeToolbar.showMenu(rect, onCopyRequested, onPasteRequested, onCutRequested,
                    controller?.let { { it.selectAll() } } ?: onSelectAllRequested)
            }
        }
    }
    LaunchedEffect(layout, bounds, controller, address) {
        val result = layout
        if (controller != null && address != null && result != null) {
            controller.fields[address] = SelectionGeometry(bounds,
                { result.getOffsetForPosition(it) }, { result.getCursorRect(it.coerceIn(0, result.layoutInput.text.length)) })
        }
    }
    DisposableEffect(controller, address) {
        onDispose { if (address != null) controller?.fields?.remove(address) }
    }
    LaunchedEffect(selected, controller?.active, fieldsEpoch) {
        if (controller != null && selected != null && value.composition == null) {
            // The document owns selection handles; this field only keeps the IME caret.
            value = value.copy(selection = TextRange(selected.end.coerceIn(0, value.text.length)))
            if (controller.active) nativeToolbar.hide()
        }
    }

    LaunchedEffect(annotated) {
        if (value.composition == null && value.text == annotated.text && value.annotatedString != annotated) {
            value = value.copy(annotatedString = annotated)
        }
    }
    LaunchedEffect(fieldsEpoch, annotated.text) {
        if (value.composition == null && value.text != annotated.text) {
            value = TextFieldValue(annotated, TextRange(annotated.length))
        }
    }
    LaunchedEffect(fieldsEpoch) {
        if (value.composition == null || value.text != annotated.text) {
            val range = selected ?: selection ?: TextRange(annotated.length)
            value = TextFieldValue(annotated, if (controller != null) TextRange(range.end.coerceIn(0, annotated.length))
                else TextRange(range.start.coerceIn(0, annotated.length), range.end.coerceIn(0, annotated.length)))
        }
    }
    LaunchedEffect(focused) {
        if (focused) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    val fieldModifier = modifier
        .fillMaxWidth()
        .semantics {
            if (controller != null) {
                if (selected != null) textSelectionRange = selected
                customActions = listOf(CustomAccessibilityAction("全选正文") { controller.selectAll(); true })
            }
        }
        .onGloballyPositioned { bounds = Rect(it.positionInRoot(), it.size.toSize()) }
        .drawBehind {
            val result = layout
            if (selected != null && !selected.collapsed && result != null) {
                drawPath(result.getPathForRange(selected.min.coerceIn(0, result.layoutInput.text.length),
                    selected.max.coerceIn(0, result.layoutInput.text.length)), selectionColor)
            }
        }
        .focusRequester(focusRequester)
        .onFocusChanged { if (it.isFocused) onFocused() }
        .onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown &&
                event.key == Key.Backspace &&
                value.selection.collapsed &&
                value.selection.start == 0
            ) {
                onDeleteBackwardAtStart()
                true
            } else {
                false
            }
        }
        .then(if (fieldTestTag != null) Modifier.testTag(fieldTestTag) else Modifier)

    DocumentSelectionInput(controller) {
        CompositionLocalProvider(LocalTextToolbar provides if (controller == null) nativeToolbar else toolbar) {
            BasicTextField(
                value = value,
                onTextLayout = { layout = it },
                onValueChange = { incoming ->
                    val oldText = value.text
                    value = incoming
                    onTextChange(
                        oldText,
                        incoming.text,
                        incoming.selection,
                        incoming.composition != null,
                    )
                    if (controller != null && address != null && oldText != incoming.text &&
                        controller.selection.focus().address == address) {
                        val updated = controller.session.document.inlinesAt(address).toAnnotatedString(highlightColor, linkColor)
                        val caret = controller.selection.end.coerceIn(0, updated.length)
                        val shift = caret - incoming.selection.end
                        val composition = incoming.composition?.let {
                            TextRange((it.start + shift).coerceIn(0, updated.length), (it.end + shift).coerceIn(0, updated.length))
                        }
                        value = TextFieldValue(updated, TextRange(caret), composition)
                    }
                },
                modifier = fieldModifier,
                textStyle = textStyle.copy(
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = textAlign,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
                ),
                singleLine = singleLine,
                decorationBox = { inner ->
                    Box {
                        if (placeholder.isNotEmpty() && inlines.plainText().isEmpty() && value.text.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = textStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = textAlign,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        inner()
                    }
                },
            )
        }
    }
}
