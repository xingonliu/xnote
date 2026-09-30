package com.xnote.app.design

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xnote.app.R
import com.kyant.backdrop.Backdrop
import com.xnote.app.design.liquidglass.LiquidToggle
import androidx.compose.ui.semantics.clearAndSetSemantics

// -- Functions

@Composable
fun XNoteSettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        XNoteGroupCard(content = content)
        if (!description.isNullOrBlank()) Text(description, Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun XNoteSettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    @DrawableRes icon: Int? = null,
    value: String? = null,
    destructive: Boolean = false,
    showsDisclosure: Boolean = true,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(modifier.fillMaxWidth()
        .then(if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick) else Modifier)
        .heightIn(min = 56.dp).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (icon != null) Icon(painterResource(icon), null, Modifier.size(24.dp), tint = color)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge,
                color = color.copy(alpha = if (enabled) 1f else 0.5f))
            if (!summary.isNullOrBlank()) Text(summary, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (value != null) Text(value, modifier = Modifier.widthIn(max = 160.dp),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onClick != null && showsDisclosure && !destructive) Icon(painterResource(R.drawable.ic_keyline_stroke_chevron_right), null,
            Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun XNoteSettingsSwitch(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop,
    summary: String? = null,
    enabled: Boolean = true,
) {
    Row(modifier.fillMaxWidth().toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
        .heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (!summary.isNullOrBlank()) Text(summary, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LiquidToggle(selected = { checked }, onSelect = onChange, backdrop = backdrop,
            enabled = enabled, modifier = Modifier.clearAndSetSemantics { })
    }
}

@Composable
fun <T> XNoteChoiceGroup(
    choices: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    optionModifier: (T) -> Modifier = { Modifier },
) {
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup()
        .clip(XNoteSmoothCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
        .padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        choices.forEach { choice ->
            val active = choice == selected
            val shape = XNoteSmoothCornerShape(12.dp)
            Box(optionModifier(choice).weight(1f).fillMaxHeight().clip(shape)
                .background(if (active) MaterialTheme.colorScheme.surface else Color.Transparent)
                .then(if (active) Modifier.border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), shape) else Modifier)
                .selectable(active, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(choice) })
                .heightIn(min = 44.dp).padding(horizontal = 8.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                Text(label(choice), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.5f))
            }
        }
    }
}
