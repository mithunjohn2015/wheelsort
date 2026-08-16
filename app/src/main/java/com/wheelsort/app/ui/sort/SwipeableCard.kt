package com.wheelsort.app.ui.sort

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.wheelsort.app.R
import kotlin.math.roundToInt

/**
 * Wraps [content] with Tinder-style horizontal swipe-to-decide behavior.
 * Vertical drags are intentionally NOT handled here - [WheelGesture] routes
 * those to wheel navigation instead, so this composable only reacts to
 * horizontal deltas fed to it externally via [dragState].
 */
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
    val rotation = (dragState.offsetX / 42f).coerceIn(-14f, 14f)
    val keepAlpha = (dragState.offsetX / (dragState.widthPx * 0.28f)).coerceIn(0f, 1f)
    val deleteAlpha = (-dragState.offsetX / (dragState.widthPx * 0.28f)).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { dragState.widthPx = it.size.width.toFloat() }
            .offset { IntOffset(dragState.offsetX.roundToInt(), 0) }
            .rotate(rotation)
    ) {
        content()

        // KEEP badge (top-left), fades in while dragging right
        Text(
            text = androidx.compose.ui.res.stringResource(R.string.sort_keep),
            color = MaterialTheme.colorScheme.tertiary,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(20.dp)
                .rotate(-16f)
                .border(3.dp, Color(0xFF00B894), RoundedCornerShape(8.dp))
                .background(Color(0x1500B894))
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .alpha(keepAlpha)
        )

        // DELETE badge (top-right), fades in while dragging left
        Text(
            text = androidx.compose.ui.res.stringResource(R.string.sort_delete),
            color = Color(0xFFFF5E5E),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(20.dp)
                .rotate(16f)
                .border(3.dp, Color(0xFFFF5E5E), RoundedCornerShape(8.dp))
                .background(Color(0x15FF5E5E))
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .alpha(deleteAlpha)
        )
    }
}
