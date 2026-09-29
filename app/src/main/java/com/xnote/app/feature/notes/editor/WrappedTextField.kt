package com.xnote.app.feature.notes.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.Layout
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.*
import android.view.Gravity
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.widget.EditText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import com.xnote.app.domain.document.InlineRun
import com.xnote.app.domain.document.plainText
import kotlin.math.ceil

// -- Type Definitions

private class WrapMargin(private val width: Int, private val lines: Int) : LeadingMarginSpan.LeadingMarginSpan2 {
    override fun getLeadingMargin(first: Boolean): Int = if (first) width else 0
    override fun getLeadingMarginLineCount(): Int = lines
    override fun drawLeadingMargin(canvas: Canvas, paint: Paint, x: Int, dir: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, first: Boolean, layout: Layout?) = Unit
}

private class WrapEditText(context: Context) : EditText(context) {
    // -- State and Variables

    var syncing = false
    var changed: (() -> Unit)? = null
    var appliedEpoch = -1
    var deleteAtStart: (() -> Unit)? = null

    // -- Functions

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        if (!syncing) changed?.invoke()
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val connection = super.onCreateInputConnection(outAttrs) ?: return null
        return object : InputConnectionWrapper(connection, false) {
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (beforeLength > 0 && selectionStart == 0 && selectionEnd == 0) { deleteAtStart?.invoke(); return true }
                return super.deleteSurroundingText(beforeLength, afterLength)
            }
        }
    }
}

// -- Functions

@Composable
fun WrappedTextField(
    inlines: List<InlineRun>, fieldsEpoch: Int, textStyle: TextStyle, textAlign: TextAlign,
    exclusionWidth: Float, exclusionHeight: Float, focused: Boolean, selection: TextRange?,
    onFocused: () -> Unit, onTextChange: (String, String, TextRange, Boolean) -> Unit,
    onDeleteBackwardAtStart: () -> Unit,
    modifier: Modifier,
) {
    val density = LocalDensity.current
    val foreground = MaterialTheme.colorScheme.onBackground.toArgb()
    val primary = MaterialTheme.colorScheme.primary.toArgb()
    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f).toArgb()
    val fontPx = with(density) { textStyle.fontSize.toPx() }
    val linePx = with(density) { textStyle.lineHeight.toPx() }
    val currentOnText by rememberUpdatedState(onTextChange)
    val currentOnFocus by rememberUpdatedState(onFocused)
    val currentOnDelete by rememberUpdatedState(onDeleteBackwardAtStart)
    AndroidView(modifier = modifier, factory = { context ->
        WrapEditText(context).apply {
            background = null
            setPadding(0, 0, 0, 0)
            includeFontPadding = false
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setHorizontallyScrolling(false)
            setSelectAllOnFocus(false)
            deleteAtStart = { currentOnDelete() }
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == android.view.KeyEvent.KEYCODE_DEL && event.action == android.view.KeyEvent.ACTION_DOWN && selectionStart == 0 && selectionEnd == 0) {
                    currentOnDelete(); true
                } else false
            }
            var previous = ""
            changed = {
                if (!syncing && hasFocus()) {
                    val now = text.toString()
                    currentOnText(previous, now, TextRange(selectionStart.coerceAtLeast(0), selectionEnd.coerceAtLeast(0)), BaseInputConnection.getComposingSpanStart(text) >= 0)
                    previous = now
                }
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { previous = s.toString() }
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) { changed?.invoke() }
            })
            setOnFocusChangeListener { _, hasFocus -> if (hasFocus) { previous = text.toString(); currentOnFocus() } }
        }
    }, update = { view ->
        view.syncing = true
        try {
            view.setTextColor(foreground)
            view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, fontPx)
            view.typeface = Typeface.create(if (textStyle.fontFamily == FontFamily.Monospace) Typeface.MONOSPACE else Typeface.DEFAULT,
                if ((textStyle.fontWeight?.weight ?: 400) >= 600) Typeface.BOLD else Typeface.NORMAL)
            view.setLineSpacing((linePx - view.paint.fontSpacing).coerceAtLeast(0f), 1f)
            view.textAlignment = when (textAlign) { TextAlign.Center -> android.view.View.TEXT_ALIGNMENT_CENTER; TextAlign.End, TextAlign.Right -> android.view.View.TEXT_ALIGNMENT_TEXT_END; else -> android.view.View.TEXT_ALIGNMENT_TEXT_START }
            val plain = inlines.plainText()
            val composing = BaseInputConnection.getComposingSpanStart(view.text) >= 0
            val reset = view.appliedEpoch != fieldsEpoch
            if (reset || (!composing && view.text.toString() != plain)) {
                view.setText(plain)
                val range = selection ?: TextRange(plain.length)
                view.setSelection(range.start.coerceIn(0, plain.length), range.end.coerceIn(0, plain.length))
                view.appliedEpoch = fieldsEpoch
            }
            val editable = view.text
            if (editable != null && !composing) {
                editable.getSpans(0, editable.length, CharacterStyle::class.java).forEach(editable::removeSpan)
                var offset = 0
                for (run in inlines) {
                    val end = (offset + run.text.length).coerceAtMost(editable.length)
                    fun span(value: Any) { if (end > offset) editable.setSpan(value, offset, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
                    if (run.bold || run.italic) span(StyleSpan(when { run.bold && run.italic -> Typeface.BOLD_ITALIC; run.bold -> Typeface.BOLD; else -> Typeface.ITALIC }))
                    if (run.underline) span(UnderlineSpan())
                    if (run.strikethrough) span(StrikethroughSpan())
                    if (run.highlight) span(BackgroundColorSpan(highlight))
                    if (run.linkUrl != null) { span(ForegroundColorSpan(primary)); span(UnderlineSpan()) }
                    offset = end
                }
            }
            editable?.getSpans(0, editable.length, WrapMargin::class.java)?.forEach(editable::removeSpan)
            if (editable != null && exclusionWidth > 0 && exclusionHeight > 0) {
                editable.setSpan(WrapMargin(exclusionWidth.toInt(), ceil(exclusionHeight / view.lineHeight.coerceAtLeast(1)).toInt()),
                    0, editable.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
            }
            if (focused && !view.hasFocus()) view.requestFocus()
        } finally { view.syncing = false }
    })
}
