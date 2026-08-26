package com.wheelsort.app.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.wheelsort.app.data.WheelSettings

/**
 * Sits on top of the live wheel (which keeps running underneath, unaffected) rather than
 * navigating to a separate screen - so the "preview" is just the real thing, not an approximation.
 * While any slider is being actively dragged, the whole panel fades to near-transparent so the
 * wheel is what you're actually watching; it returns to fully visible the moment you let go.
 */
@Composable
fun WheelSettingsOverlay(
    settings: WheelSettings,
    onUpdate: ((WheelSettings) -> WheelSettings) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    var isDragging by remember { mutableStateOf(false) }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> isDragging = true
                is DragInteraction.Stop, is DragInteraction.Cancel -> isDragging = false
            }
        }
    }
    // Fades out fast (feels immediate) and back in a little slower (feels settled, not abrupt).
    val panelAlpha by animateFloatAsState(
        targetValue = if (isDragging) 0.12f else 1f,
        animationSpec = tween(if (isDragging) 80 else 220),
        label = "settingsPanelAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Tapping the visible wheel area above the panel closes it.
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClose
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.62f)
                .graphicsLayer { alpha = panelAlpha }
                // Absorb clicks so tapping inside the panel doesn't fall through to the close handler above.
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {}
                ),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Wheel settings", style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onReset) { Text("Reset") }
                        IconButton(onClick = onClose) {
                            Icon(Icons.Filled.Close, contentDescription = "Close settings")
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp)
                ) {
                    wheelSettingsSliderItems(
                        settings = settings,
                        interactionSource = interactionSource,
                        onUpdate = onUpdate
                    )
                    item { Spacer(Modifier.height(20.dp)) }
                }
            }
        }
    }
}
