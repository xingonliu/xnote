package com.xnote.app.feature.creative

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.xnote.app.design.XNoteButton

// -- Functions

@Composable
fun CreativeIconButton(label: String, @DrawableRes icon: Int, enabled: Boolean = true, onClick: () -> Unit) {
    XNoteButton(onClick, Modifier.size(48.dp).semantics { contentDescription = label }, enabled = enabled) {
        Icon(painterResource(icon), null, Modifier.size(24.dp))
    }
}

@Composable
fun CreativeModeButton(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    XNoteButton(onClick, Modifier.heightIn(min = 48.dp).semantics { this.selected = selected }, enabled,
        tint = if (selected) MaterialTheme.colorScheme.primary else Color.Unspecified) { Text(label) }
}
