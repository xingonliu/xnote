package com.xnote.app.feature.agent

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xnote.app.R
import com.xnote.app.design.LocalXNoteInteractionSettings
import com.xnote.app.design.liquidglass.LiquidButton

// -- Functions

@Composable
internal fun AgentScrollToBottomButton(
    running: Boolean,
    backdrop: Backdrop,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LiquidButton(
        onClick = onClick, backdrop = backdrop,
        surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        modifier = modifier.size(52.dp).testTag("agent-scroll-to-bottom").semantics {
            contentDescription = if (running) "Agent 正在运行，回到最新消息" else "滚动到底部"
        },
    ) {
        if (running) AgentRunningDots() else Icon(
            painterResource(R.drawable.ic_keyline_stroke_arrow_down), null,
            Modifier.size(24.dp).testTag("agent-scroll-arrow"), tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun AgentRunningDots() {
    // -- Derived Values

    val reduceMotion = LocalXNoteInteractionSettings.current.reduceMotion
    val amplitude = with(LocalDensity.current) { 4.dp.toPx() }
    val color = MaterialTheme.colorScheme.onSurface

    Row(Modifier.testTag("agent-running-dots"), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { index ->
            val displacement = if (reduceMotion) null else {
                val transition = rememberInfiniteTransition(label = "agent-dot-$index")
                transition.animateFloat(
                    initialValue = 0f, targetValue = 0f,
                    animationSpec = infiniteRepeatable(keyframes {
                        durationMillis = 1200
                        0f at index * 140
                        -1f at index * 140 + 220
                        0f at index * 140 + 440
                    }, RepeatMode.Restart),
                    label = "agent-dot-displacement-$index",
                )
            }
            Box(Modifier.size(5.dp).graphicsLayer {
                translationY = (displacement?.value ?: 0f) * amplitude
            }.background(color, CircleShape))
        }
    }
}
