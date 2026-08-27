package com.xnote.app.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xnote.app.R
import com.xnote.app.design.XNoteEmptyState
import com.xnote.app.design.XNoteMinimumTouchTarget
import com.xnote.app.design.XNoteSpacingMedium
import com.xnote.app.design.XNoteSpacingSmall
import com.xnote.app.design.XNoteTextField
import com.xnote.app.design.liquidglass.LiquidButton
import com.xnote.app.domain.model.NoteSearchResult
import com.xnote.app.domain.model.Notebook
import com.xnote.app.domain.text.searchMatchRanges
import com.xnote.app.feature.notes.displayTitle
import com.xnote.app.feature.notes.formatNoteTimestamp
import com.xnote.app.feature.notes.notebookName

// -- Composables

@Composable
fun SearchScreen(
    query: String,
    selectedNotebookId: String?,
    results: List<NoteSearchResult>,
    recentQueries: List<String>,
    notebooks: List<Notebook>,
    backdrop: Backdrop,
    contentPadding: PaddingValues,
    listState: LazyListState,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onNotebookSelected: (String?) -> Unit,
    onOpenNote: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val untitledLabel = stringResource(R.string.notes_untitled)

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxHeight()
            .widthIn(max = 560.dp)
            .fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(XNoteSpacingSmall),
    ) {
        item {
            XNoteTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = stringResource(R.string.search_field_placeholder),
                imeAction = ImeAction.Search,
                keyboardActions = KeyboardActions(
                    onSearch = {
                        onSearch(query)
                        keyboardController?.hide()
                    },
                ),
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .testTag("xnote-search-field"),
            )
        }

        item {
            SearchNotebookFilters(
                notebooks = notebooks,
                selectedNotebookId = selectedNotebookId,
                onNotebookSelected = onNotebookSelected,
                backdrop = backdrop,
            )
        }

        when {
            query.isBlank() && recentQueries.isEmpty() -> item {
                XNoteEmptyState(
                    title = stringResource(R.string.search_start_title),
                    description = stringResource(R.string.search_start_description),
                    iconRes = R.drawable.ic_lucide_search,
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            query.isBlank() -> {
                item {
                    Text(
                        text = stringResource(R.string.search_recent_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = XNoteSpacingSmall),
                    )
                }
                items(recentQueries, key = { it.lowercase() }) { recentQuery ->
                    RecentSearchRow(
                        query = recentQuery,
                        onClick = { onSearch(recentQuery) },
                    )
                }
            }

            results.isEmpty() -> item {
                XNoteEmptyState(
                    title = stringResource(R.string.search_no_results_title),
                    description = stringResource(R.string.search_no_results_description, query),
                    iconRes = R.drawable.ic_lucide_search,
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            else -> {
                item {
                    Text(
                        text = stringResource(R.string.search_results_count, results.size),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = XNoteSpacingSmall),
                    )
                }
                items(results, key = { it.note.id }) { result ->
                    SearchResultRow(
                        result = result,
                        query = query,
                        notebookName = notebookName(notebooks, result.note.notebookId),
                        untitledLabel = untitledLabel,
                        onClick = {
                            onSearch(query)
                            onOpenNote(result.note.id)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchNotebookFilters(
    notebooks: List<Notebook>,
    selectedNotebookId: String?,
    onNotebookSelected: (String?) -> Unit,
    backdrop: Backdrop,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(XNoteSpacingSmall),
        contentPadding = PaddingValues(vertical = XNoteSpacingSmall),
    ) {
        item(key = "all") {
            SearchFilterButton(
                label = stringResource(R.string.search_filter_all),
                selected = selectedNotebookId == null,
                onClick = { onNotebookSelected(null) },
                backdrop = backdrop,
            )
        }
        items(notebooks, key = Notebook::id) { notebook ->
            SearchFilterButton(
                label = notebook.name,
                selected = selectedNotebookId == notebook.id,
                onClick = { onNotebookSelected(notebook.id) },
                backdrop = backdrop,
            )
        }
    }
}

@Composable
private fun SearchFilterButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    backdrop: Backdrop,
) {
    LiquidButton(
        onClick = onClick,
        backdrop = backdrop,
        tint = if (selected) MaterialTheme.colorScheme.primary else Color.Unspecified,
        modifier = Modifier.semantics { this.selected = selected },
        height = XNoteMinimumTouchTarget,
        contentPadding = PaddingValues(horizontal = XNoteSpacingMedium),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RecentSearchRow(
    query: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(XNoteSpacingSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_lucide_search),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = query,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SearchResultRow(
    result: NoteSearchResult,
    query: String,
    notebookName: String?,
    untitledLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val highlight = primary.copy(alpha = 0.18f)
    val title = result.note.displayTitle(untitledLabel)
    val metadata = buildString {
        append(notebookName ?: stringResource(R.string.notes_scope_unfiled))
        append(" · ")
        append(formatNoteTimestamp(result.note.updatedAtEpochMs))
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("xnote-search-result")
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = highlightedText(title, query, primary, highlight),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (result.matchedText.isNotBlank()) {
            Text(
                text = highlightedText(result.matchedText, query, primary, highlight),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = metadata,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// -- Functions

private fun highlightedText(
    text: String,
    query: String,
    foreground: Color,
    background: Color,
): AnnotatedString = buildAnnotatedString {
    append(text)
    searchMatchRanges(text, query).forEach { range ->
        addStyle(
            style = SpanStyle(
                color = foreground,
                background = background,
                fontWeight = FontWeight.SemiBold,
            ),
            start = range.first,
            end = range.last + 1,
        )
    }
}
