package com.wheelsort.app.ui.splash

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.wheelsort.app.R
import kotlinx.coroutines.delay

private const val HOLD_MS = 550L

/**
 * Shown once per cold start, after the system splash screen hands off. The system splash (see
 * Theme.App.Starting) only supports a static icon with a basic exit transition - this picks up
 * from there with a proper entrance animation using the same launcher artwork, so the "wheel"
 * icon actually feels like it's spinning into place rather than just appearing.
 */
@Composable
fun AnimatedSplashScreen(onFinished: () -> Unit) {
    var started by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        started = 1f
        delay(HOLD_MS)
        onFinished()
    }

    val scale by animateFloatAsState(
        targetValue = started,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "splashScale"
    )
    val alpha by animateFloatAsState(
        targetValue = started,
        animationSpec = tween(320),
        label = "splashAlpha"
    )
    val rotation by animateFloatAsState(
        targetValue = started * 360f,
        animationSpec = tween(650),
        label = "splashRotation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(Color(0xFF6E6BFF), Color(0xFF242180))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier
                .size(140.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    rotationZ = rotation
                    this.alpha = alpha
                }
        )
    }
}
