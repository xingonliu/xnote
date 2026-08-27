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

// -- Composables

@Composable
fun RichTextField(
    inlines: List<InlineRun>,
    fieldsEpoch: Int,
    textStyle: TextStyle,
    textAlign: TextAlign,
    placeholder: String,
    focused: Boolean,
    onFocused: () -> Unit,
    onTextChange: (oldText: String, newText: String, selection: TextRange, composing: Boolean) -> Unit,
    onDeleteBackwardAtStart: () -> Unit,
    modifier: Modifier = Modifier,
    fieldTestTag: String? = null,
    singleLine: Boolean = false,
) {
    val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(inlines, highlightColor, linkColor) {
        inlines.toAnnotatedString(highlightColor, linkColor)
    }
    var value by remember(fieldsEpoch) {
        mutableStateOf(TextFieldValue(annotated, TextRange(annotated.text.length)))
    }
    val focusRequester = remember { FocusRequester() }

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
    LaunchedEffect(focused) {
        if (focused) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    val fieldModifier = modifier
        .fillMaxWidth()
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

    BasicTextField(
        value = value,
        onValueChange = { incoming ->
            val oldText = value.text
            value = incoming
            onTextChange(
                oldText,
                incoming.text,
                incoming.selection,
                incoming.composition != null,
            )
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
                if (inlines.plainText().isEmpty() && value.text.isEmpty()) {
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
