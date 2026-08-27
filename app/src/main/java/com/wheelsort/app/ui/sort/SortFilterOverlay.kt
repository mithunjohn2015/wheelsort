package com.wheelsort.app.ui.sort

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Sits on top of the wheel (bottom-sheet style, matching WheelSettingsOverlay) rather than a
 * separate screen - sort order and filters used to only be choosable once on Home before
 * starting; this lets them change mid-session without leaving the review flow.
 */
@Composable
fun SortFilterOverlay(
    filters: SortFilters,
    onUpdate: ((SortFilters) -> SortFilters) -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
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
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {}
                ),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sort & filter", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }
                Spacer(Modifier.height(16.dp))

                Text(
                    "ORDER",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = filters.newestFirst,
                        onClick = { onUpdate { it.copy(newestFirst = true) } },
                        label = { Text("Newest first") }
                    )
                    FilterChip(
                        selected = !filters.newestFirst,
                        onClick = { onUpdate { it.copy(newestFirst = false) } },
                        label = { Text("Oldest first") }
                    )
                }

                Spacer(Modifier.height(20.dp))
                Text(
                    "SHOW",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = filters.mediaType == MediaTypeFilter.ALL,
                        onClick = { onUpdate { it.copy(mediaType = MediaTypeFilter.ALL) } },
                        label = { Text("Everything") }
                    )
                    FilterChip(
                        selected = filters.mediaType == MediaTypeFilter.PHOTOS_ONLY,
                        onClick = { onUpdate { it.copy(mediaType = MediaTypeFilter.PHOTOS_ONLY) } },
                        label = { Text("Photos only") }
                    )
                    FilterChip(
                        selected = filters.mediaType == MediaTypeFilter.VIDEOS_ONLY,
                        onClick = { onUpdate { it.copy(mediaType = MediaTypeFilter.VIDEOS_ONLY) } },
                        label = { Text("Videos only") }
                    )
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Favorites only", style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = filters.favoritesOnly,
                        onCheckedChange = { v -> onUpdate { it.copy(favoritesOnly = v) } }
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}
