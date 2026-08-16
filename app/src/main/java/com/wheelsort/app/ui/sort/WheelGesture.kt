package com.wheelsort.app.ui.sort

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs

private enum class DragAxis { UNDECIDED, HORIZONTAL, VERTICAL }
private const val AXIS_LOCK_THRESHOLD_PX = 18f

/**
 * A single gesture region that decides, per-drag, whether the user is:
 *  - turning the photo wheel (vertical drag -> [onVerticalSwipe]), or
 *  - making a keep/delete decision on the centered photo (horizontal drag).
 *
 * Only one axis is ever "live" per gesture, so the two behaviors never fight
 * over the same touch the way two independent detectors would.
 */
fun Modifier.wheelSortGesture(
    onHorizontalDrag: (deltaPx: Float) -> Unit,
    onHorizontalDragEnd: () -> Unit,
    onHorizontalDragCancel: () -> Unit,
    onVerticalSwipe: (isUpward: Boolean) -> Unit
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var axis = DragAxis.UNDECIDED
        var accumulatedDx = 0f
        var accumulatedDy = 0f

        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break

            val delta = change.positionChange()

            when (axis) {
                DragAxis.UNDECIDED -> {
                    accumulatedDx += delta.x
                    accumulatedDy += delta.y
                    if (abs(accumulatedDx) > AXIS_LOCK_THRESHOLD_PX || abs(accumulatedDy) > AXIS_LOCK_THRESHOLD_PX) {
                        axis = if (abs(accumulatedDx) > abs(accumulatedDy)) DragAxis.HORIZONTAL else DragAxis.VERTICAL
                        if (axis == DragAxis.HORIZONTAL) onHorizontalDrag(accumulatedDx)
                    }
                    change.consume()
                }
                DragAxis.HORIZONTAL -> {
                    change.consume()
                    onHorizontalDrag(delta.x)
                }
                DragAxis.VERTICAL -> {
                    change.consume()
                }
            }
        }

        when (axis) {
            DragAxis.HORIZONTAL -> onHorizontalDragEnd()
            DragAxis.VERTICAL -> onVerticalSwipe(accumulatedDy < 0f)
            DragAxis.UNDECIDED -> onHorizontalDragCancel()
        }
    }
}
