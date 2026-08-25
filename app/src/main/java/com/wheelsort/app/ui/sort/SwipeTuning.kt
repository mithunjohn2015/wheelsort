package com.wheelsort.app.ui.sort

/** Tunable feel constants for the keep/delete swipe and the wheel fling. */
object SwipeTuning {
    /** Fraction of card width you need to drag before it counts as a decision - lower = easier. */
    const val COMMIT_DISTANCE_FRACTION = 0.34f

    /** A flick faster than this (px/sec) commits immediately, even with very little travel. */
    const val COMMIT_VELOCITY_PX_PER_SEC = 1500f

    /** Fraction of one slot's height you need to drag before the wheel commits to the next photo. */
    const val WHEEL_COMMIT_DISTANCE_FRACTION = 0.35f

    /** A flick faster than this (px/sec) commits the wheel to the next photo even with little travel. */
    const val WHEEL_FLING_VELOCITY_PX_PER_SEC = 600f
}
