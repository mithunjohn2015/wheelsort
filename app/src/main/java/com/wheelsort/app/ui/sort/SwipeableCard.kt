package com.wheelsort.app.ui.sort

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.wheelsort.app.R
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
    val commitDistance = dragState.widthPx * SwipeTuning.COMMIT_DISTANCE_FRACTION
    val keepAlpha = (dragState.offsetX / commitDistance).coerceIn(0f, 1f)
    val deleteAlpha = (-dragState.offsetX / commitDistance).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { dragState.widthPx = it.size.width.toFloat() }
            .offset { IntOffset(dragState.offsetX.roundToInt(), 0) }
            .rotate(rotation)
    ) {
        content()

        Text(
            text = stringResource(R.string.sort_keep),
            color = Color(0xFF00B894),
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

        Text(
            text = stringResource(R.string.sort_delete),
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
