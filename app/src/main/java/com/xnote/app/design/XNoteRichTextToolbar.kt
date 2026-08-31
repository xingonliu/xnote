package com.xnote.app.design

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xnote.app.R
import com.xnote.app.design.liquidglass.LiquidButton

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

// -- Constants

private val InlineActions = listOf(
    XNoteRichTextAction.Bold,
    XNoteRichTextAction.Italic,
    XNoteRichTextAction.Underline,
    XNoteRichTextAction.Strikethrough,
    XNoteRichTextAction.Link,
    XNoteRichTextAction.Highlight,
)

private val StructureActions = listOf(
    XNoteRichTextAction.BulletedList,
    XNoteRichTextAction.DashedList,
    XNoteRichTextAction.NumberedList,
    XNoteRichTextAction.Checklist,
    XNoteRichTextAction.Quote,
    XNoteRichTextAction.DecreaseIndent,
    XNoteRichTextAction.IncreaseIndent,
)

private val LayoutActions = listOf(
    XNoteRichTextAction.AlignStart,
    XNoteRichTextAction.AlignCenter,
    XNoteRichTextAction.AlignEnd,
    XNoteRichTextAction.Table,
    XNoteRichTextAction.ToggleHeadingCollapse,
)

// -- Composables

@Composable
fun XNoteRichTextToolbar(
    state: XNoteRichTextToolbarState,
    onAction: (XNoteRichTextAction) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    XNoteLiquidGlassPanel(
        backdrop = backdrop,
        shape = XNoteSmoothCornerShape(XNoteRadiusMedium),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(XNoteSpacingSmall),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            XNoteToolbarAction(
                label = stringResource(state.paragraphStyle.labelRes),
                action = XNoteRichTextAction.ParagraphStyle,
                state = state,
                backdrop = backdrop,
                onAction = onAction,
            )
            XNoteToolbarDivider()
            XNoteToolbarActionGroup(
                actions = InlineActions,
                state = state,
                backdrop = backdrop,
                onAction = onAction,
            )
            XNoteToolbarDivider()
            XNoteToolbarActionGroup(
                actions = StructureActions,
                state = state,
                backdrop = backdrop,
                onAction = onAction,
            )
            XNoteToolbarDivider()
            XNoteToolbarActionGroup(
                actions = LayoutActions,
                state = state,
                backdrop = backdrop,
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun RowScope.XNoteToolbarActionGroup(
    actions: List<XNoteRichTextAction>,
    state: XNoteRichTextToolbarState,
    backdrop: Backdrop,
    onAction: (XNoteRichTextAction) -> Unit,
) {
    actions.forEach { action ->
        XNoteToolbarAction(
            label = stringResource(action.labelRes),
            action = action,
            state = state,
            backdrop = backdrop,
            onAction = onAction,
        )
    }
}

@Composable
private fun XNoteToolbarAction(
    label: String,
    action: XNoteRichTextAction,
    state: XNoteRichTextToolbarState,
    backdrop: Backdrop,
    onAction: (XNoteRichTextAction) -> Unit,
) {
    val selected = action in state.selectedActions
    val foreground = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    LiquidButton(
        onClick = { onAction(action) },
        backdrop = backdrop,
        enabled = action !in state.disabledActions,
        tint = if (selected) MaterialTheme.colorScheme.primary else Color.Unspecified,
        modifier = Modifier.semantics { this.selected = selected },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = foreground,
            maxLines = 1,
        )
    }
}

@Composable
private fun XNoteToolbarDivider() {
    Spacer(
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .width(1.dp)
            .height(24.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.24f)),
    )
}
