package com.xnote.app.feature.background

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.xnote.app.R
import com.xnote.app.design.XNoteRadiusMedium
import com.xnote.app.design.XNoteSmoothCornerShape
import com.xnote.app.design.XNoteSpacingMedium
import com.xnote.app.design.XNoteSpacingSmall
import com.xnote.app.domain.model.BackgroundKey

// -- Composables

@Composable
fun XNoteBackgroundPicker(
    selectedKey: BackgroundKey?,
    previewBackground: BackgroundKey,
    scopeDescription: String,
    onSelect: (BackgroundKey?) -> Unit,
    modifier: Modifier = Modifier,
    allowDefaultInheritance: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(XNoteSpacingMedium),
    ) {
        Text(
            text = scopeDescription,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BackgroundPreview(previewBackground)
        if (allowDefaultInheritance) {
            DefaultInheritanceChoice(
                selected = selectedKey == null,
                onClick = { onSelect(null) },
            )
        }
        XNoteBuiltinBackgroundPresets.chunked(2).forEach { rowPresets ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(XNoteSpacingSmall),
            ) {
                rowPresets.forEach { preset ->
                    val background = BackgroundKey(preset.id)
                    BackgroundChoice(
                        label = stringResource(preset.nameRes),
                        background = background,
                        selected = selectedKey == background,
                        onClick = { onSelect(background) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowPresets.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BackgroundPreview(background: BackgroundKey) {
    val shape = XNoteSmoothCornerShape(XNoteRadiusMedium)
    XNoteNoteSurface(
        background = background,
        modifier = Modifier
            .fillMaxWidth()
            .height(156.dp)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.32f), shape),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(XNoteSpacingMedium),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.background_preview_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.background_preview_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DefaultInheritanceChoice(
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = XNoteSmoothCornerShape(XNoteRadiusMedium)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                },
                shape = shape,
            )
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.54f), shape)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(XNoteSpacingMedium),
        horizontalArrangement = Arrangement.spacedBy(XNoteSpacingSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.background_use_default),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.background_use_default_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.ic_lucide_check),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun BackgroundChoice(
    label: String,
    background: BackgroundKey,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = XNoteSmoothCornerShape(XNoteRadiusMedium)
    Column(
        modifier = modifier
            .clip(shape)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                },
                shape = shape,
            )
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.42f), shape)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(4.dp),
    ) {
        XNoteNoteSurface(
            background = background,
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .clip(XNoteSmoothCornerShape(16.dp)),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = XNoteSpacingSmall, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (selected) {
                Icon(
                    painter = painterResource(R.drawable.ic_lucide_check),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
