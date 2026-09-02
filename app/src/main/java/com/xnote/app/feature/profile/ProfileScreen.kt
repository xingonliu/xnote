package com.xnote.app.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xnote.app.R
import com.xnote.app.design.XNoteGroupCard
import com.xnote.app.design.XNoteIconSizeSmall
import com.xnote.app.design.XNoteRadiusSmall
import com.xnote.app.design.XNoteSmoothCornerShape
import com.xnote.app.design.XNoteSpacingMedium
import com.xnote.app.design.XNoteSpacingSmall

// -- Functions

@Composable
fun ProfileScreen(
    trashCount: Int,
    contentPadding: PaddingValues,
    listState: LazyListState,
    onOpenRecycleBin: () -> Unit,
    onOpenBackgroundSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(XNoteSpacingSmall),
    ) {
        item {
            Text(
                text = stringResource(R.string.profile_library_section),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.padding(start = 6.dp, top = 4.dp, bottom = 2.dp),
            )
        }
        item {
            XNoteGroupCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenRecycleBin)
                        .padding(horizontal = XNoteSpacingMedium, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(XNoteSpacingMedium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                                shape = XNoteSmoothCornerShape(XNoteRadiusSmall),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_keyline_stroke_bin),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(XNoteIconSizeSmall),
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.recycle_bin_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = stringResource(R.string.recycle_bin_profile_summary, trashCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (trashCount > 0) {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                                    shape = XNoteSmoothCornerShape(10.dp),
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = "$trashCount",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    Icon(
                        painter = painterResource(R.drawable.ic_keyline_stroke_chevron_right),
                        contentDescription = stringResource(R.string.recycle_bin_open),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(XNoteIconSizeSmall),
                    )
                }
            }
        }
        item {
            Text(
                text = stringResource(R.string.profile_appearance_section),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.padding(start = 6.dp, top = XNoteSpacingMedium, bottom = 2.dp),
            )
        }
        item {
            XNoteGroupCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenBackgroundSettings)
                        .padding(horizontal = XNoteSpacingMedium, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(XNoteSpacingMedium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                shape = XNoteSmoothCornerShape(XNoteRadiusSmall),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_keyline_stroke_star),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(XNoteIconSizeSmall),
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.background_settings_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = stringResource(R.string.background_settings_summary),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        painter = painterResource(R.drawable.ic_keyline_stroke_chevron_right),
                        contentDescription = stringResource(R.string.background_settings_open),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(XNoteIconSizeSmall),
                    )
                }
            }
        }
    }
}
