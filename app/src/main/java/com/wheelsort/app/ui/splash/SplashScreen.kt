package com.wheelsort.app.ui.splash

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.wheelsort.app.R
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

private const val TILE_STAGGER_MS = 55
private const val TILE_ANIM_MS = 420
private const val STACK_HOLD_MS = 260L
private const val REVEAL_MS = 380

/** Matches the app icon's own petal palette, so the splash reads as the same brand rather than
 *  a generic loading animation. */
private val TILE_COLORS = listOf(
    Color(0xFF8E7CFF), Color(0xFFE0704F), Color(0xFF1FAE83),
    Color(0xFFD6579B), Color(0xFF3FA7D6)
)

/**
 * Shown once per cold start, after the system splash screen hands off. Rather than just
 * animating the static icon, this echoes the app's own core interaction (photos converging into
 * a stacked wheel): a handful of colored tiles fly in from scattered positions and angles,
 * settle into a small vertical stack one after another, then cross-fade into the real app icon.
 */
@Composable
fun AnimatedSplashScreen(onFinished: () -> Unit) {
    var started by remember { mutableFloatStateOf(0f) }
    var revealing by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        started = 1f
        delay(TILE_STAGGER_MS.toLong() * TILE_COLORS.size + TILE_ANIM_MS + STACK_HOLD_MS)
        revealing = 1f
        delay(REVEAL_MS.toLong())
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xFF6E6BFF), Color(0xFF242180)))),
        contentAlignment = Alignment.Center
    ) {
        // The tile stack fades out as the icon fades in - a cross-fade, not a hard cut.
        val stackAlpha by animateFloatAsState(
            targetValue = 1f - revealing,
            animationSpec = tween(REVEAL_MS),
            label = "stackAlpha"
        )
        val stackScale by animateFloatAsState(
            targetValue = 1f - revealing * 0.3f,
            animationSpec = tween(REVEAL_MS),
            label = "stackScale"
        )

        Box(
            modifier = Modifier.graphicsLayer {
                alpha = stackAlpha
                scaleX = stackScale
                scaleY = stackScale
            }
        ) {
            TILE_COLORS.forEachIndexed { index, color ->
                SplashTile(
                    color = color,
                    stackOffset = index,
                    started = started,
                    delayMs = index * TILE_STAGGER_MS
                )
            }
        }

        val iconAlpha by animateFloatAsState(
            targetValue = revealing,
            animationSpec = tween(REVEAL_MS),
            label = "iconAlpha"
        )
        val iconScale by animateFloatAsState(
            targetValue = if (revealing > 0f) 1f else 0.7f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
            label = "iconScale"
        )
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier
                .size(140.dp)
                .graphicsLayer {
                    alpha = iconAlpha
                    scaleX = iconScale
                    scaleY = iconScale
                }
        )
    }
}

/**
 * One tile: starts scattered outward at a distinct angle with zero scale, and animates into its
 * resting position in a small vertical stack (mimicking the real wheel's stacked-card look),
 * each one starting slightly after the previous for a "falling into place" feel.
 */
@Composable
private fun SplashTile(color: Color, stackOffset: Int, started: Float, delayMs: Int) {
    val progress by animateFloatAsState(
        targetValue = started,
        animationSpec = tween(TILE_ANIM_MS, delayMillis = delayMs, easing = FastOutSlowInEasing),
        label = "tileProgress$stackOffset"
    )

    // Scattered starting angle/distance, distinct per tile, purely for the "converging" motion -
    // not meant to represent anything beyond a pleasant flying-in arrangement.
    val angle = (stackOffset * 73f) * (Math.PI.toFloat() / 180f)
    val scatterDistance = 220f
    val startX = cos(angle) * scatterDistance
    val startY = sin(angle) * scatterDistance

    val restY = (stackOffset - (TILE_COLORS.size - 1) / 2f) * 14f

    Box(
        modifier = Modifier
            .size(width = 46.dp, height = 60.dp)
            .graphicsLayer {
                translationX = startX * (1f - progress)
                translationY = startY * (1f - progress) + restY * progress
                scaleX = 0.4f + 0.6f * progress
                scaleY = 0.4f + 0.6f * progress
                rotationZ = (1f - progress) * (if (stackOffset % 2 == 0) 140f else -140f)
                alpha = progress
            }
            .clip(RoundedCornerShape(10.dp))
            .background(color)
    )
}
