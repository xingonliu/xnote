package com.xnote.app.design

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xnote.app.R
import com.xnote.app.design.liquidglass.LiquidButton

// -- Type Definitions

enum class XNoteParagraphStyle(
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
) {
    Body(R.string.rich_text_paragraph_body, R.drawable.ic_keyline_stroke_scan_text),
    Heading(R.string.rich_text_paragraph_heading, R.drawable.ic_keyline_stroke_file_text),
    Subheading(R.string.rich_text_paragraph_subheading, R.drawable.ic_keyline_stroke_queue),
    Monospace(R.string.rich_text_paragraph_monospace, R.drawable.ic_keyline_stroke_code),
}

enum class XNoteRichTextAction(
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
) {
    ParagraphStyle(R.string.rich_text_action_paragraph_style, R.drawable.ic_keyline_stroke_scan_text),
    Bold(R.string.rich_text_action_bold, R.drawable.ic_keyline_stroke_bold),
    Italic(R.string.rich_text_action_italic, R.drawable.ic_keyline_stroke_italic),
    Underline(R.string.rich_text_action_underline, R.drawable.ic_keyline_stroke_underline),
    Strikethrough(R.string.rich_text_action_strikethrough, R.drawable.ic_keyline_stroke_strikethrough),
    Link(R.string.rich_text_action_link, R.drawable.ic_keyline_stroke_link),
    Highlight(R.string.rich_text_action_highlight, R.drawable.ic_keyline_stroke_paintbrush),
    BulletedList(R.string.rich_text_action_bulleted_list, R.drawable.ic_keyline_stroke_list),
    DashedList(R.string.rich_text_action_dashed_list, R.drawable.ic_keyline_stroke_list),
    NumberedList(R.string.rich_text_action_numbered_list, R.drawable.ic_keyline_stroke_list_ordered),
    Checklist(R.string.rich_text_action_checklist, R.drawable.ic_keyline_stroke_square_check),
    Quote(R.string.rich_text_action_quote, R.drawable.ic_keyline_stroke_quote),
    DecreaseIndent(R.string.rich_text_action_decrease_indent, R.drawable.ic_keyline_stroke_bracket_arrow_left),
    IncreaseIndent(R.string.rich_text_action_increase_indent, R.drawable.ic_keyline_stroke_bracket_arrow_right),
    AlignStart(R.string.rich_text_action_align_start, R.drawable.ic_keyline_stroke_align_left),
    AlignCenter(R.string.rich_text_action_align_center, R.drawable.ic_keyline_stroke_align_center),
    AlignEnd(R.string.rich_text_action_align_end, R.drawable.ic_keyline_stroke_align_right),
    Table(R.string.rich_text_action_table, R.drawable.ic_keyline_stroke_grid_3x3),
    ToggleHeadingCollapse(R.string.rich_text_action_heading_collapse, R.drawable.ic_keyline_stroke_chevron_right),
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
    XNoteLiquidGlassPanel(
        backdrop = backdrop,
        shape = XNoteSmoothCornerShape(XNoteRadiusMedium),
        modifier = modifier.fillMaxWidth().testTag("xnote-format-inspector"),
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth()) {
                XNoteParagraphStyle.entries.forEach { style ->
                    EditorSymbolButton(
                        description = stringResource(style.labelRes),
                        iconRes = style.iconRes,
                        selected = state.paragraphStyle == style,
                        enabled = XNoteRichTextAction.ParagraphStyle !in state.disabledActions,
                        modifier = Modifier.weight(1f),
                        onClick = { onParagraphStyle(style) },
                    )
                }
            }
            listOf(
                listOf(
                    XNoteRichTextAction.Bold, XNoteRichTextAction.Italic,
                    XNoteRichTextAction.Underline, XNoteRichTextAction.Strikethrough,
                    XNoteRichTextAction.Link, XNoteRichTextAction.Highlight,
                ),
                listOf(
                    XNoteRichTextAction.BulletedList, XNoteRichTextAction.NumberedList,
                    XNoteRichTextAction.DecreaseIndent, XNoteRichTextAction.IncreaseIndent,
                    XNoteRichTextAction.AlignStart, XNoteRichTextAction.AlignCenter,
                    XNoteRichTextAction.AlignEnd,
                ),
            ).forEach { actions ->
                Row(Modifier.fillMaxWidth()) {
                    actions.forEach { action ->
                        EditorSymbolButton(
                            description = stringResource(action.labelRes),
                            iconRes = action.iconRes,
                            modifier = Modifier.weight(1f),
                            selected = action in state.selectedActions,
                            enabled = action !in state.disabledActions,
                            onClick = { onAction(action) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EditorGlassIconButton(
    iconRes: Int,
    description: String,
    backdrop: Backdrop,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(XNoteButtonSize),
    enabled: Boolean = true,
    selected: Boolean = false,
    destructive: Boolean = false,
) {
    LiquidButton(
        onClick = onClick,
        backdrop = backdrop,
        enabled = enabled,
        tint = if (selected) MaterialTheme.colorScheme.primary else Color.Unspecified,
        modifier = modifier,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = description,
            tint = if (destructive) {
                MaterialTheme.colorScheme.error.copy(alpha = if (enabled) 1f else 0.35f)
            } else {
                LocalContentColor.current
            },
            modifier = Modifier.size(XNoteIconSizeMedium),
        )
    }
}

@Composable
fun EditorSymbolButton(
    description: String,
    iconRes: Int,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val selectedForeground = if (colors.primary.luminance() > 0.179f) Color.Black else Color.White
    Box(
        modifier
            .heightIn(min = XNoteButtonSize)
            .clip(CircleShape)
            .background(if (selected) colors.primary else Color.Transparent)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description; this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = (if (selected) selectedForeground else if (destructive) colors.error else colors.onSurface)
                .copy(alpha = if (enabled) 1f else 0.35f),
            modifier = Modifier.size(XNoteIconSizeMedium),
        )
    }
}
