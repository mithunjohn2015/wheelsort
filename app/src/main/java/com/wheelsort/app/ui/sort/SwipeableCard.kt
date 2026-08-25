package com.wheelsort.app.ui.sort

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Live drag state for the centered card - external gesture code drives [offsetX] directly, and
 * the wheel's graphicsLayer block reads it each frame to apply the horizontal offset/rotation
 * (inlined there rather than via a separate wrapper composable, so the card's composable
 * structure never changes shape - see WheelCarousel for why that matters).
 */
class SwipeCardDragState {
    var offsetX by mutableFloatStateOf(0f)
        internal set
    var widthPx by mutableFloatStateOf(1f)
        internal set
}

@Composable
fun rememberSwipeCardDragState() = remember { SwipeCardDragState() }
