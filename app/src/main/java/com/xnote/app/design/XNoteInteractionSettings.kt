package com.xnote.app.design

import android.animation.ValueAnimator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

// -- Type Definitions

@Immutable
data class XNoteInteractionSettings(
    val reduceMotion: Boolean = false,
    val highContrast: Boolean = false,
)

// -- State

val LocalXNoteInteractionSettings = staticCompositionLocalOf {
    XNoteInteractionSettings()
}

// -- Composables

@Composable
fun XNoteInteractionSettingsProvider(
    reduceMotion: Boolean? = null,
    highContrast: Boolean = false,
    content: @Composable () -> Unit,
) {
    val systemReduceMotion = rememberSystemReduceMotion()
    val settings = remember(reduceMotion, systemReduceMotion, highContrast) {
        XNoteInteractionSettings(
            reduceMotion = reduceMotion ?: systemReduceMotion,
            highContrast = highContrast,
        )
    }

    CompositionLocalProvider(
        LocalXNoteInteractionSettings provides settings,
        content = content,
    )
}

@Composable
private fun rememberSystemReduceMotion(): Boolean {
    var durationScale by remember { mutableFloatStateOf(ValueAnimator.getDurationScale()) }

    DisposableEffect(Unit) {
        val listener = ValueAnimator.DurationScaleChangeListener { scale ->
            durationScale = scale
        }
        ValueAnimator.registerDurationScaleChangeListener(listener)
        onDispose {
            ValueAnimator.unregisterDurationScaleChangeListener(listener)
        }
    }

    return durationScale == 0f
}
