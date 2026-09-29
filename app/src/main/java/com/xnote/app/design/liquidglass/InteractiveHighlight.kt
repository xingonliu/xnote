package com.xnote.app.design.liquidglass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.util.fastCoerceIn
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.asComposeShader
import com.kyant.backdrop.isRuntimeShaderSupported
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// Adapted from AndroidLiquidGlass catalog commit 65ab177 under Apache-2.0.

class InteractiveHighlight(
    val animationScope: CoroutineScope,
    val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset },
    private val surfaceAlpha: Float = 0.04f,
    private val fallbackSurfaceAlpha: Float = 0.125f,
    private val softGlow: Boolean = false,
) {
    // -- State and Variables
    private val pressProgressAnimationSpec =
        spring(0.5f, 300f, 0.001f)
    private val positionAnimationSpec =
        spring(0.5f, 300f, Offset.VisibilityThreshold)

    private val pressProgressAnimation =
        Animatable(0f, 0.001f)
    private val positionAnimation =
        Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)

    private var startPosition = Offset.Zero
    val pressProgress: Float get() = pressProgressAnimation.value
    val offset: Offset get() = positionAnimation.value - startPosition

    private val shader =
        if (!softGlow && isRuntimeShaderSupported()) {
            RuntimeShader(
                """
uniform float2 size;
layout(color) uniform half4 color;
uniform float radius;
uniform float2 position;

half4 main(float2 coord) {
    float dist = distance(coord, position);
    float intensity = smoothstep(radius, radius * 0.5, dist);
    return color * intensity;
}"""
            )
        } else {
            null
        }

    // -- Derived Values

    val modifier: Modifier =
        Modifier.drawWithContent {
            val progress = pressProgressAnimation.value
            if (progress > 0f) {
                if (softGlow) {
                    val touch = position(size, positionAnimation.value)
                    val glow = Color.White.copy(alpha = 0.18f * progress.coerceIn(0f, 1f))
                    // A broad Gaussian falloff avoids a solid center and a visible circular rim.
                    drawRect(
                        Brush.radialGradient(
                            0f to glow,
                            0.125f to glow.copy(alpha = glow.alpha * 0.91f),
                            0.25f to glow.copy(alpha = glow.alpha * 0.69f),
                            0.375f to glow.copy(alpha = glow.alpha * 0.43f),
                            0.5f to glow.copy(alpha = glow.alpha * 0.22f),
                            0.625f to glow.copy(alpha = glow.alpha * 0.094f),
                            0.75f to glow.copy(alpha = glow.alpha * 0.032f),
                            0.875f to glow.copy(alpha = glow.alpha * 0.008f),
                            1f to glow.copy(alpha = 0f),
                            center = Offset(
                                touch.x.fastCoerceIn(0f, size.width),
                                touch.y.fastCoerceIn(0f, size.height),
                            ),
                            radius = size.minDimension * 2f,
                        ),
                    )
                } else if (shader != null) {
                    drawRect(
                        Color.White.copy(surfaceAlpha * progress),
                        blendMode = BlendMode.Plus,
                    )
                    shader.apply {
                        val position = position(size, positionAnimation.value)
                        setFloatUniform("size", size.width, size.height)
                        setColorUniform("color", Color.White.copy(0.15f * progress))
                        setFloatUniform("radius", size.minDimension * 1.5f)
                        setFloatUniform(
                            "position",
                            position.x.fastCoerceIn(0f, size.width),
                            position.y.fastCoerceIn(0f, size.height),
                        )
                    }
                    drawRect(
                        ShaderBrush(shader.asComposeShader()),
                        blendMode = BlendMode.Plus,
                    )
                } else {
                    drawRect(
                        Color.White.copy(fallbackSurfaceAlpha * progress),
                        blendMode = BlendMode.Plus,
                    )
                }
            }

            drawContent()
        }

    val gestureModifier: Modifier =
        Modifier.pointerInput(animationScope) {
            inspectDragGestures(
                onDragStart = { down ->
                    startPosition = down.position
                    animationScope.launch {
                        launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
                        launch { positionAnimation.snapTo(startPosition) }
                    }
                },
                onDragEnd = {
                    animationScope.launch {
                        launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
                        launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
                    }
                },
                onDragCancel = {
                    animationScope.launch {
                        launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
                        launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
                    }
                },
            ) { change, _ ->
                animationScope.launch { positionAnimation.snapTo(change.position) }
            }
        }
}

