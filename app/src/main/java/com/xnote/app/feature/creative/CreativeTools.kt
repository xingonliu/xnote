package com.xnote.app.feature.creative

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.xnote.app.design.XNoteButton

// -- Constants

private val DrawingColors = listOf("黑色" to 0xff202020L, "蓝色" to 0xff1769e0L, "红色" to 0xffdb3030L,
    "绿色" to 0xff268248L, "黄色" to 0xffe1aa00L, "白色" to 0xffffffffL)

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

@Composable
fun DrawingColorPicker(color: Long, enabled: Boolean, onSelect: (Long) -> Unit) {
    FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DrawingColors.forEach { (label, value) ->
            Box(Modifier.size(44.dp).selectable(value == color, enabled = enabled, role = Role.RadioButton,
                onClick = { onSelect(value) }).semantics { contentDescription = label }
                .padding(4.dp).then(if (value == color) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier),
                contentAlignment = Alignment.Center) {
                val outline = MaterialTheme.colorScheme.outline
                Canvas(Modifier.size(26.dp)) {
                    drawCircle(Color(value))
                    drawCircle(outline, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                }
            }
        }
    }
}
