package com.wheelsort.app.ui.sort

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import kotlin.math.abs

private enum class DragAxis { UNDECIDED, HORIZONTAL, VERTICAL }

/** Small dead-zone before we commit to an axis - keeps taps and jitter from triggering anything. */
private const val AXIS_LOCK_THRESHOLD_PX = 10f

/**
 * One gesture region that separates:
 *  - a tap (negligible movement) -> [onTap], used to open the full-screen viewer
 *  - vertical drags -> turning the wheel (continuous position + end velocity, for flinging)
 *  - horizontal drags -> the keep/delete decision on the centered photo
 *
 * Axis is decided once per gesture (whichever direction moves first/more), so wheel-turning
 * and keep/delete swipes never interfere with each other mid-gesture.
 */
fun Modifier.wheelSortGesture(
    onDown: (androidx.compose.ui.geometry.Offset) -> Unit = {},
    onTap: () -> Unit,
    onHorizontalDrag: (deltaPx: Float) -> Unit,
    onHorizontalDragEnd: (velocityPxPerSec: Float) -> Unit,
    onHorizontalDragCancel: () -> Unit,
    onVerticalDrag: (deltaPx: Float) -> Unit,
    onVerticalDragEnd: (velocityPxPerSec: Float) -> Unit
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = true)
        onDown(down.position)
        var axis = DragAxis.UNDECIDED
        var accumulatedDx = 0f
        var accumulatedDy = 0f
        val velocityTracker = VelocityTracker()
        velocityTracker.addPointerInputChange(down)

        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            velocityTracker.addPointerInputChange(change)
            if (!change.pressed) break

            val delta = change.positionChange()

            when (axis) {
                DragAxis.UNDECIDED -> {
                    accumulatedDx += delta.x
                    accumulatedDy += delta.y
                    if (abs(accumulatedDx) > AXIS_LOCK_THRESHOLD_PX || abs(accumulatedDy) > AXIS_LOCK_THRESHOLD_PX) {
                        axis = if (abs(accumulatedDx) > abs(accumulatedDy)) DragAxis.HORIZONTAL else DragAxis.VERTICAL
                        if (axis == DragAxis.HORIZONTAL) onHorizontalDrag(accumulatedDx) else onVerticalDrag(accumulatedDy)
                    }
                    change.consume()
                }
                DragAxis.HORIZONTAL -> {
                    change.consume()
                    onHorizontalDrag(delta.x)
                }
                DragAxis.VERTICAL -> {
                    change.consume()
                    onVerticalDrag(delta.y)
                }
            }
        }

        val velocity = velocityTracker.calculateVelocity()
        when (axis) {
            DragAxis.HORIZONTAL -> onHorizontalDragEnd(velocity.x)
            DragAxis.VERTICAL -> onVerticalDragEnd(velocity.y)
            DragAxis.UNDECIDED -> {
                onHorizontalDragCancel()
                onTap()
            }
        }
    }
}
