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
import com.xnote.app.domain.document.TextAddress
import com.xnote.app.domain.document.focus
import com.xnote.app.domain.document.inlinesAt
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.geometry.Rect
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
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
    var documentSelection: DocumentSelectionController? = null
    var currentInputConnection: InputConnection? = null

    // -- Functions

    override fun onTextContextMenuItem(id: Int): Boolean {
        if (id == android.R.id.selectAll && documentSelection != null) {
            documentSelection?.selectAll()
            return true
        }
        val controller = documentSelection
        if (controller?.active == true) when (id) {
            android.R.id.copy -> { controller.copy(); return true }
            android.R.id.cut -> { controller.copy(true); return true }
            android.R.id.paste -> { controller.paste(); return true }
        }
        return super.onTextContextMenuItem(id)
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        if (!syncing) changed?.invoke()
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val connection = super.onCreateInputConnection(outAttrs) ?: return null
        currentInputConnection = connection
        val localConnection = object : InputConnectionWrapper(connection, false) {
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (beforeLength > 0 && selectionStart == 0 && selectionEnd == 0) { deleteAtStart?.invoke(); return true }
                return super.deleteSurroundingText(beforeLength, afterLength)
            }
        }
        return documentSelection?.let { DocumentSelectionInputConnection(localConnection, it) } ?: localConnection
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
    address: TextAddress? = null,
) {
    val controller = LocalDocumentSelection.current.takeIf { address != null }
    val selected = address?.let { controller?.range(it) }
    var viewReference by remember { mutableStateOf<WrapEditText?>(null) }
    DisposableEffect(controller, address) {
        onDispose { if (address != null) controller?.fields?.remove(address) }
    }
    val density = LocalDensity.current
    val foreground = MaterialTheme.colorScheme.onBackground.toArgb()
    val primary = MaterialTheme.colorScheme.primary.toArgb()
    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f).toArgb()
    val fontPx = with(density) { textStyle.fontSize.toPx() }
    val linePx = with(density) { textStyle.lineHeight.toPx() }
    val currentOnText by rememberUpdatedState(onTextChange)
    val currentOnFocus by rememberUpdatedState(onFocused)
    val currentOnDelete by rememberUpdatedState(onDeleteBackwardAtStart)
    AndroidView(modifier = modifier.onGloballyPositioned { coordinates ->
        val view = viewReference
        val layout = view?.layout
        if (controller != null && address != null && layout != null) {
            controller.fields[address] = SelectionGeometry(Rect(coordinates.positionInRoot(), coordinates.size.toSize()),
                { point -> layout.getOffsetForHorizontal(layout.getLineForVertical(point.y.toInt()), point.x) },
                { offset ->
                    val at = offset.coerceIn(0, view.text.length)
                    val line = layout.getLineForOffset(at)
                    val x = layout.getPrimaryHorizontal(at)
                    Rect(x, layout.getLineTop(line).toFloat(), x + 1, layout.getLineBottom(line).toFloat())
                })
        }
    }, factory = { context ->
        WrapEditText(context).apply {
            viewReference = this
            documentSelection = controller
            customSelectionActionModeCallback = object : ActionMode.Callback {
                override fun onCreateActionMode(mode: ActionMode, menu: Menu) = controller?.active != true
                override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                    if (controller?.active == true) mode.finish()
                    return false
                }
                override fun onActionItemClicked(mode: ActionMode, item: MenuItem) = false
                override fun onDestroyActionMode(mode: ActionMode) = Unit
            }
            background = null
            setPadding(0, 0, 0, 0)
            includeFontPadding = false
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setHorizontallyScrolling(false)
            setSelectAllOnFocus(false)
            deleteAtStart = { currentOnDelete() }
            setOnKeyListener { _, keyCode, event ->
                if (event.action == android.view.KeyEvent.ACTION_DOWN && controller?.active == true &&
                    (keyCode == android.view.KeyEvent.KEYCODE_DEL || keyCode == android.view.KeyEvent.KEYCODE_FORWARD_DEL)) {
                    controller.session.replaceSelection(""); true
                } else if (event.action == android.view.KeyEvent.ACTION_DOWN && (event.isCtrlPressed || event.isMetaPressed) &&
                    keyCode == android.view.KeyEvent.KEYCODE_A && controller != null) {
                    controller.selectAll(); true
                } else if (keyCode == android.view.KeyEvent.KEYCODE_DEL && event.action == android.view.KeyEvent.ACTION_DOWN && selectionStart == 0 && selectionEnd == 0) {
                    currentOnDelete(); true
                } else false
            }
            var previous = ""
            changed = {
                if (!syncing && hasFocus()) {
                    val now = text.toString()
                    val changedText = previous != now
                    val composingStart = BaseInputConnection.getComposingSpanStart(text)
                    val composingEnd = BaseInputConnection.getComposingSpanEnd(text)
                    val oldCaret = selectionEnd.coerceAtLeast(0)
                    currentOnText(previous, now, TextRange(selectionStart.coerceAtLeast(0), oldCaret), composingStart >= 0)
                    if (changedText && controller != null && address != null && controller.selection.focus().address == address) {
                        val updated = controller.session.document.inlinesAt(address).plainText()
                        val caret = controller.selection.end.coerceIn(0, updated.length)
                        if (updated != now) {
                            syncing = true
                            try {
                                setText(updated)
                                setSelection(caret)
                                if (composingStart >= 0) {
                                    val shift = caret - oldCaret
                                    currentInputConnection?.setComposingRegion((composingStart + shift).coerceIn(0, updated.length),
                                        (composingEnd + shift).coerceIn(0, updated.length))
                                }
                            } finally { syncing = false }
                        }
                    }
                    previous = text.toString()
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
            if (!composing && (reset || view.text.toString() != plain)) {
                view.setText(plain)
                val range = selection ?: TextRange(plain.length)
                view.setSelection(range.start.coerceIn(0, plain.length), range.end.coerceIn(0, plain.length))
            }
            view.appliedEpoch = fieldsEpoch
            val editable = view.text
            if (selected != null && !composing) {
                val caret = selected.end.coerceIn(0, plain.length)
                if (view.selectionStart != caret || view.selectionEnd != caret) view.setSelection(caret)
            }
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
                if (selected != null && !selected.collapsed) {
                    editable.setSpan(BackgroundColorSpan(highlight), selected.min.coerceIn(0, editable.length),
                        selected.max.coerceIn(0, editable.length), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
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
