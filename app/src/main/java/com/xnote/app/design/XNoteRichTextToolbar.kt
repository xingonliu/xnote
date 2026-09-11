package com.xnote.app.design

import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xnote.app.R

// -- Type Definitions

enum class XNoteParagraphStyle(
    @param:StringRes val labelRes: Int,
) {
    Body(R.string.rich_text_paragraph_body),
    Heading(R.string.rich_text_paragraph_heading),
    Subheading(R.string.rich_text_paragraph_subheading),
    Monospace(R.string.rich_text_paragraph_monospace),
}

enum class XNoteRichTextAction(
    @param:StringRes val labelRes: Int,
) {
    ParagraphStyle(R.string.rich_text_action_paragraph_style),
    Bold(R.string.rich_text_action_bold),
    Italic(R.string.rich_text_action_italic),
    Underline(R.string.rich_text_action_underline),
    Strikethrough(R.string.rich_text_action_strikethrough),
    Link(R.string.rich_text_action_link),
    Highlight(R.string.rich_text_action_highlight),
    BulletedList(R.string.rich_text_action_bulleted_list),
    DashedList(R.string.rich_text_action_dashed_list),
    NumberedList(R.string.rich_text_action_numbered_list),
    Checklist(R.string.rich_text_action_checklist),
    Quote(R.string.rich_text_action_quote),
    DecreaseIndent(R.string.rich_text_action_decrease_indent),
    IncreaseIndent(R.string.rich_text_action_increase_indent),
    AlignStart(R.string.rich_text_action_align_start),
    AlignCenter(R.string.rich_text_action_align_center),
    AlignEnd(R.string.rich_text_action_align_end),
    Table(R.string.rich_text_action_table),
    ToggleHeadingCollapse(R.string.rich_text_action_heading_collapse),
}

@Stable
data class XNoteRichTextToolbarState(
    val paragraphStyle: XNoteParagraphStyle = XNoteParagraphStyle.Body,
    val selectedActions: Set<XNoteRichTextAction> = emptySet(),
    val disabledActions: Set<XNoteRichTextAction> = emptySet(),
)

// -- Functions

@Composable
fun XNoteRichTextToolbar(
    state: XNoteRichTextToolbarState,
    onAction: (XNoteRichTextAction) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    onParagraphStyle: (XNoteParagraphStyle) -> Unit,
) {
    XNoteLiquidGlassPanel(backdrop = backdrop, shape = XNoteSmoothCornerShape(XNoteRadiusMedium),
        modifier = modifier.fillMaxWidth().testTag("xnote-format-inspector")) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth()) {
                XNoteParagraphStyle.entries.forEach { style ->
                    EditorSymbolButton(stringResource(style.labelRes), stringResource(style.labelRes),
                        selected = state.paragraphStyle == style,
                        enabled = XNoteRichTextAction.ParagraphStyle !in state.disabledActions,
                        modifier = Modifier.weight(1f), onClick = { onParagraphStyle(style) })
                }
            }
            listOf(
                listOf(XNoteRichTextAction.Bold to "B", XNoteRichTextAction.Italic to "I",
                    XNoteRichTextAction.Underline to "U", XNoteRichTextAction.Strikethrough to "S",
                    XNoteRichTextAction.Link to "↗", XNoteRichTextAction.Highlight to "▰"),
                listOf(XNoteRichTextAction.BulletedList to "• ≡", XNoteRichTextAction.NumberedList to "1. ≡",
                    XNoteRichTextAction.DecreaseIndent to "⇤", XNoteRichTextAction.IncreaseIndent to "⇥",
                    XNoteRichTextAction.AlignStart to "≡←", XNoteRichTextAction.AlignCenter to "≡",
                    XNoteRichTextAction.AlignEnd to "→≡"),
            ).forEach { actions ->
                Row(Modifier.fillMaxWidth()) {
                    actions.forEach { (action, symbol) ->
                        EditorSymbolButton(symbol, stringResource(action.labelRes), Modifier.weight(1f),
                            selected = action in state.selectedActions, enabled = action !in state.disabledActions,
                            iconRes = if (action == XNoteRichTextAction.Link) R.drawable.ic_keyline_stroke_link else null,
                            onClick = { onAction(action) })
                    }
                }
            }
        }
    }
}

@Composable
fun EditorSymbolButton(
    symbol: String,
    description: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    destructive: Boolean = false,
    iconRes: Int? = null,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val selectedForeground = if (colors.primary.luminance() > 0.179f) Color.Black else Color.White
    Box(modifier.heightIn(min = 44.dp).clip(XNoteSmoothCornerShape(22.dp))
        .background(if (selected) colors.primary else Color.Transparent)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = description; this.selected = selected },
        contentAlignment = Alignment.Center) {
        if (iconRes != null) {
            Icon(painterResource(iconRes), contentDescription = null,
                tint = (if (selected) selectedForeground else if (destructive) colors.error else colors.onSurface)
                    .copy(alpha = if (enabled) 1f else 0.35f), modifier = Modifier.size(XNoteIconSizeMedium))
        } else Text(symbol, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (symbol == "B") FontWeight.Bold else FontWeight.Medium,
                fontStyle = if (symbol == "I") FontStyle.Italic else FontStyle.Normal,
                textDecoration = when (symbol) {
                    "U" -> TextDecoration.Underline
                    "S" -> TextDecoration.LineThrough
                    else -> TextDecoration.None
                },
            ),
            color = (if (selected) selectedForeground else if (destructive) colors.error else colors.onSurface)
                .copy(alpha = if (enabled) 1f else 0.35f),
            modifier = Modifier.padding(horizontal = 2.dp).clearAndSetSemantics {})
    }
}
