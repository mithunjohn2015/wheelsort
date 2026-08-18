package com.wheelsort.app.ui.sort

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

/** Live drag state for the centered card - external gesture code drives [offsetX] directly. */
class SwipeCardDragState {
    var offsetX by mutableFloatStateOf(0f)
        internal set
    var widthPx by mutableFloatStateOf(1f)
        internal set
}

@Composable
fun rememberSwipeCardDragState() = remember { SwipeCardDragState() }

@Composable
fun SwipeableCard(
    dragState: SwipeCardDragState,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val rotation = (dragState.offsetX / 38f).coerceIn(-16f, 16f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { dragState.widthPx = it.size.width.toFloat() }
            .offset { IntOffset(dragState.offsetX.roundToInt(), 0) }
            .rotate(rotation),
        content = content
    )
}
