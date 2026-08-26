package com.wheelsort.app.data

import android.content.Context

/** How each card visually transitions as it moves through the center. */
enum class WheelTransitionStyle {
    /** Cards shrink, dim, and peek behind each other - the original "stack" look. */
    STACK,
    /** Cards tip away in 3D as they leave center, like a rolodex card flipping over. */
    FLIP,
    /** Minimal transform - cards just slide vertically with a light fade, no depth stacking. */
    SLIDE
}

/**
 * Every tunable knob for the wheel's look and feel, previously hardcoded constants in
 * SortScreen.kt. Exposed via the Settings screen so this can be dialed in per-device/per-taste
 * instead of guessed at blind.
 */
data class WheelSettings(
    val transitionStyle: WheelTransitionStyle = WheelTransitionStyle.STACK,
    val cardHeightFraction: Float = 0.56f,
    val cardWidthFraction: Float = 0.84f,
    /** Extra pull-together (-) or push-apart (+) offset between photos, in dp. 0 = natural spacing. */
    val itemSpacingDp: Float = 0f,
    /** How much smaller each photo gets per "item" of distance from center. Lower = more dramatic. */
    val shrinkPerLevel: Float = 0.80f,
    /** How much each photo darkens per "item" of distance from center. */
    val dimPerLevel: Float = 0.26f,
    /** Cap on how dark a background photo can get, regardless of distance. */
    val maxDim: Float = 0.65f,
    /**
     * Spring stiffness for the swipe returning to center when a keep/delete drag doesn't commit -
     * lower = slower/smoother, higher = snappier. (Compose's built-in vertical scroll-snap
     * animation isn't independently customizable in this project's Compose Foundation version,
     * so this controls the horizontal swipe spring instead - the one motion here that is.)
     */
    val snapStiffness: Float = 400f,
    /** Damping ratio for that same swipe-back spring - 1.0 = no bounce, lower = bouncier. */
    val snapDamping: Float = 1f,
    /** Fraction of card width you must drag before a keep/delete swipe commits - lower = easier. */
    val swipeCommitDistanceFraction: Float = 0.34f,
    /** A flick faster than this (px/sec) commits a keep/delete swipe even with little travel. */
    val swipeCommitVelocity: Float = 1500f
) {
    companion object {
        val DEFAULT = WheelSettings()
    }
}

class WheelSettingsRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("wheel_settings", Context.MODE_PRIVATE)

    fun load(): WheelSettings = WheelSettings(
        transitionStyle = prefs.getString(KEY_STYLE, null)?.let {
            try {
                WheelTransitionStyle.valueOf(it)
            } catch (_: IllegalArgumentException) {
                WheelSettings.DEFAULT.transitionStyle
            }
        } ?: WheelSettings.DEFAULT.transitionStyle,
        cardHeightFraction = prefs.getFloat(KEY_CARD_HEIGHT, WheelSettings.DEFAULT.cardHeightFraction),
        cardWidthFraction = prefs.getFloat(KEY_CARD_WIDTH, WheelSettings.DEFAULT.cardWidthFraction),
        itemSpacingDp = prefs.getFloat(KEY_SPACING, WheelSettings.DEFAULT.itemSpacingDp),
        shrinkPerLevel = prefs.getFloat(KEY_SHRINK, WheelSettings.DEFAULT.shrinkPerLevel),
        dimPerLevel = prefs.getFloat(KEY_DIM, WheelSettings.DEFAULT.dimPerLevel),
        maxDim = prefs.getFloat(KEY_MAX_DIM, WheelSettings.DEFAULT.maxDim),
        snapStiffness = prefs.getFloat(KEY_SNAP_STIFFNESS, WheelSettings.DEFAULT.snapStiffness),
        snapDamping = prefs.getFloat(KEY_SNAP_DAMPING, WheelSettings.DEFAULT.snapDamping),
        swipeCommitDistanceFraction = prefs.getFloat(KEY_COMMIT_DIST, WheelSettings.DEFAULT.swipeCommitDistanceFraction),
        swipeCommitVelocity = prefs.getFloat(KEY_COMMIT_VEL, WheelSettings.DEFAULT.swipeCommitVelocity)
    )

    fun save(settings: WheelSettings) {
        prefs.edit()
            .putString(KEY_STYLE, settings.transitionStyle.name)
            .putFloat(KEY_CARD_HEIGHT, settings.cardHeightFraction)
            .putFloat(KEY_CARD_WIDTH, settings.cardWidthFraction)
            .putFloat(KEY_SPACING, settings.itemSpacingDp)
            .putFloat(KEY_SHRINK, settings.shrinkPerLevel)
            .putFloat(KEY_DIM, settings.dimPerLevel)
            .putFloat(KEY_MAX_DIM, settings.maxDim)
            .putFloat(KEY_SNAP_STIFFNESS, settings.snapStiffness)
            .putFloat(KEY_SNAP_DAMPING, settings.snapDamping)
            .putFloat(KEY_COMMIT_DIST, settings.swipeCommitDistanceFraction)
            .putFloat(KEY_COMMIT_VEL, settings.swipeCommitVelocity)
            .apply()
    }

    fun reset() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_STYLE = "transition_style"
        const val KEY_CARD_HEIGHT = "card_height"
        const val KEY_CARD_WIDTH = "card_width"
        const val KEY_SPACING = "spacing"
        const val KEY_SHRINK = "shrink"
        const val KEY_DIM = "dim"
        const val KEY_MAX_DIM = "max_dim"
        const val KEY_SNAP_STIFFNESS = "snap_stiffness"
        const val KEY_SNAP_DAMPING = "snap_damping"
        const val KEY_COMMIT_DIST = "commit_dist"
        const val KEY_COMMIT_VEL = "commit_vel"
    }
}
