package com.xnote.app.design

import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import com.kyant.shapes.RoundedCornerStyle
import com.kyant.shapes.RoundedRectangle

// -- Functions

fun XNoteSmoothCornerShape(radius: Dp): Shape = RoundedRectangle(
    cornerRadius = radius,
    style = RoundedCornerStyle.Continuous,
)
