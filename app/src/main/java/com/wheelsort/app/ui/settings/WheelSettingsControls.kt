package com.wheelsort.app.ui.settings

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wheelsort.app.data.WheelSettings
import kotlin.math.roundToInt

@Composable
internal fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )
}

/**
 * [interactionSource] is shared across every slider by the caller, so observing it once tells you
 * "is any slider currently being dragged" - that's what powers the in-wheel overlay's fade.
 */
@Composable
internal fun SettingSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: (Float) -> String,
    interactionSource: MutableInteractionSource,
    onChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                valueLabel(value),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = valueRange,
            interactionSource = interactionSource
        )
    }
}

/**
 * Every tunable slider, as LazyColumn items - shared between the standalone Settings screen
 * (reached from Home, no live wheel to preview against) and the in-wheel overlay (reached from
 * inside an active review session, where the real wheel is visible and responding underneath).
 */
internal fun LazyListScope.wheelSettingsSliderItems(
    settings: WheelSettings,
    interactionSource: MutableInteractionSource,
    onUpdate: ((WheelSettings) -> WheelSettings) -> Unit
) {
    item { SectionHeader("Layout") }
    item {
        SettingSlider(
            label = "Photo size",
            value = settings.cardHeightFraction,
            valueRange = 0.35f..0.75f,
            valueLabel = { "${(it * 100).roundToInt()}%" },
            interactionSource = interactionSource,
            onChange = { v -> onUpdate { it.copy(cardHeightFraction = v) } }
        )
    }
    item {
        SettingSlider(
            label = "Space between photos",
            value = settings.itemSpacingDp,
            valueRange = -50f..50f,
            valueLabel = { "${it.roundToInt()} dp" },
            interactionSource = interactionSource,
            onChange = { v -> onUpdate { it.copy(itemSpacingDp = v) } }
        )
    }
    item {
        SettingSlider(
            label = "Depth (how much smaller behind photos are)",
            value = 1f - settings.shrinkPerLevel,
            valueRange = 0f..0.45f,
            valueLabel = { "${(it * 100).roundToInt()}%" },
            interactionSource = interactionSource,
            onChange = { v -> onUpdate { it.copy(shrinkPerLevel = 1f - v) } }
        )
    }
    item {
        SettingSlider(
            label = "Fade of background photos",
            value = settings.dimPerLevel,
            valueRange = 0f..0.5f,
            valueLabel = { "${(it * 100).roundToInt()}%" },
            interactionSource = interactionSource,
            onChange = { v -> onUpdate { it.copy(dimPerLevel = v) } }
        )
    }

    item { SectionHeader("Motion") }
    item {
        // Displayed as 0-100 "smoothness", inverted against spring stiffness (lower
        // stiffness = slower/smoother settle, higher = snappier) so the slider reads
        // intuitively even though the underlying physics value moves the other way.
        val smoothness = 1f - ((settings.snapStiffness - 50f) / (2000f - 50f)).coerceIn(0f, 1f)
        SettingSlider(
            label = "Scroll smoothness",
            value = smoothness,
            valueRange = 0f..1f,
            valueLabel = { "${(it * 100).roundToInt()}%" },
            interactionSource = interactionSource,
            onChange = { v ->
                val stiffness = 2000f - v.coerceIn(0f, 1f) * (2000f - 50f)
                onUpdate { it.copy(snapStiffness = stiffness) }
            }
        )
    }
    item {
        val bounciness = 1f - settings.snapDamping.coerceIn(0.35f, 1f).let { (it - 0.35f) / 0.65f }
        SettingSlider(
            label = "Bounciness",
            value = bounciness,
            valueRange = 0f..1f,
            valueLabel = { "${(it * 100).roundToInt()}%" },
            interactionSource = interactionSource,
            onChange = { v ->
                val damping = 1f - v.coerceIn(0f, 1f) * 0.65f
                onUpdate { it.copy(snapDamping = damping) }
            }
        )
    }

    item { SectionHeader("Gestures") }
    item {
        val sensitivity = 1f - ((settings.swipeCommitDistanceFraction - 0.15f) / (0.5f - 0.15f)).coerceIn(0f, 1f)
        SettingSlider(
            label = "Swipe sensitivity",
            value = sensitivity,
            valueRange = 0f..1f,
            valueLabel = { "${(it * 100).roundToInt()}%" },
            interactionSource = interactionSource,
            onChange = { v ->
                val dist = 0.5f - v.coerceIn(0f, 1f) * (0.5f - 0.15f)
                onUpdate { it.copy(swipeCommitDistanceFraction = dist) }
            }
        )
    }
    item {
        val flickSensitivity = 1f - ((settings.swipeCommitVelocity - 600f) / (2500f - 600f)).coerceIn(0f, 1f)
        SettingSlider(
            label = "Flick sensitivity",
            value = flickSensitivity,
            valueRange = 0f..1f,
            valueLabel = { "${(it * 100).roundToInt()}%" },
            interactionSource = interactionSource,
            onChange = { v ->
                val velocity = 2500f - v.coerceIn(0f, 1f) * (2500f - 600f)
                onUpdate { it.copy(swipeCommitVelocity = velocity) }
            }
        )
    }
}
