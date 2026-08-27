package com.wheelsort.app.ui.grid

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.wheelsort.app.data.Photo
import com.wheelsort.app.ui.sort.PhotoViewerOverlay
import com.wheelsort.app.util.formatDuration
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val MIN_COLUMNS = 2
private const val MAX_COLUMNS = 6
private const val DEFAULT_COLUMNS = 3

@Composable
fun GridScreen(
    albumFilter: String?,
    onExit: () -> Unit,
    viewModel: GridViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var columnCount by remember { mutableIntStateOf(DEFAULT_COLUMNS) }
    var viewerPhoto by remember { mutableStateOf<Photo?>(null) }
    val gridState = rememberLazyGridState()

    val trashLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) viewModel.onDeleteConfirmed()
    }
    val favoriteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { /* best-effort */ }

    LaunchedEffect(albumFilter) { viewModel.refresh(albumFilter) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("${uiState.photos.size} photos") },
                    navigationIcon = {
                        IconButton(onClick = onExit) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                    actions = {
                        if (uiState.photos.isNotEmpty()) {
                            TextButton(onClick = {
                                if (uiState.selected.size == uiState.photos.size) viewModel.clearSelection()
                                else viewModel.selectAll()
                            }) {
                                Text(if (uiState.selected.size == uiState.photos.size) "Clear" else "Select all")
                            }
                        }
                    }
                )
                if (uiState.selected.isNotEmpty()) {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${uiState.selected.size} selected")
                            TextButton(onClick = {
                                val intent = viewModel.buildTrashIntent() ?: return@TextButton
                                trashLauncher.launch(IntentSenderRequest.Builder(intent.intentSender).build())
                            }) {
                                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Delete")
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                uiState.photos.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No photos", style = MaterialTheme.typography.bodyLarge)
                }
                else -> {
                    val photos = uiState.photos
                    val selected = uiState.selected
                    val hasSelection = selected.isNotEmpty()

                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(columnCount),
                        contentPadding = PaddingValues(3.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            // Pinch (two fingers) resizes the grid; single-finger tap/long-press/
                            // drag-select is a separate detector below. Distinguished by pointer
                            // count, so the two coexist without the axis-conflict issues a single-
                            // finger gesture would have against the grid's own scroll.
                            .pointerInput(hasSelection, photos, columnCount) {
                                // Shared between the drag-select coroutine and the auto-scroll
                                // coroutine below - auto-scroll needs to keep running even while
                                // the finger holds still near an edge, which onDrag alone can't
                                // do since it only fires on actual movement.
                                var dragPosition: Offset? = null
                                var dragStartIndex: Int? = null
                                var isDragging = false

                                coroutineScope {
                                    // Pinch to resize the grid. Distinguished from the single-
                                    // finger gestures below by pointer count, and now sharing this
                                    // same pointerInput block rather than a separate one - two
                                    // independent pointerInput nodes both trying to interpret the
                                    // same touches was very likely why pinch wasn't registering
                                    // reliably; a single node's coroutines cooperate correctly.
                                    launch {
                                        // zoom here is a per-frame incremental ratio (close to 1.0,
                                        // e.g. 1.01 for a 1% change since the last callback), not a
                                        // cumulative total - a flat +-5% per-frame threshold was
                                        // essentially unreachable during normal pinching, which is
                                        // why it looked like nothing was happening. Accumulating
                                        // across the gesture and resetting once a step triggers is
                                        // the standard pattern for this API.
                                        var accumulatedZoom = 1f
                                        detectTransformGestures { _, _, zoom, _ ->
                                            accumulatedZoom *= zoom
                                            if (accumulatedZoom > 1.25f) {
                                                columnCount = (columnCount - 1).coerceAtLeast(MIN_COLUMNS)
                                                accumulatedZoom = 1f
                                            } else if (accumulatedZoom < 0.8f) {
                                                columnCount = (columnCount + 1).coerceAtMost(MAX_COLUMNS)
                                                accumulatedZoom = 1f
                                            }
                                        }
                                    }
                                    launch {
                                        detectTapGestures(onTap = { pos ->
                                            val index = indexAt(gridState, pos) ?: return@detectTapGestures
                                            val photo = photos.getOrNull(index) ?: return@detectTapGestures
                                            if (hasSelection) viewModel.toggleSelect(photo.id) else viewerPhoto = photo
                                        })
                                    }
                                    launch {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { pos ->
                                                isDragging = true
                                                dragPosition = pos
                                                val index = indexAt(gridState, pos) ?: return@detectDragGesturesAfterLongPress
                                                dragStartIndex = index
                                                photos.getOrNull(index)?.let { viewModel.ensureSelected(it.id) }
                                            },
                                            onDragEnd = { isDragging = false; dragPosition = null; dragStartIndex = null },
                                            onDragCancel = { isDragging = false; dragPosition = null; dragStartIndex = null },
                                            onDrag = { change, _ ->
                                                dragPosition = change.position
                                                // Selecting individual items the exact finger path
                                                // crossed missed most of a row whenever the sweep
                                                // moved diagonally or fast - Samsung Gallery selects
                                                // every item in every ROW the drag has covered, not
                                                // just items directly under the path. Bounded to the
                                                // swept row range (not the whole list) so this stays
                                                // cheap even on a very large library.
                                                val startIndex = dragStartIndex ?: return@detectDragGesturesAfterLongPress
                                                val currentIndex = indexAt(gridState, change.position)
                                                    ?: return@detectDragGesturesAfterLongPress
                                                val startRow = startIndex / columnCount
                                                val currentRow = currentIndex / columnCount
                                                val minRow = minOf(startRow, currentRow)
                                                val maxRow = maxOf(startRow, currentRow)
                                                val fromIndex = (minRow * columnCount).coerceIn(0, photos.size - 1)
                                                val toIndex = ((maxRow + 1) * columnCount - 1).coerceIn(0, photos.size - 1)
                                                for (i in fromIndex..toIndex) {
                                                    photos.getOrNull(i)?.let { viewModel.ensureSelected(it.id) }
                                                }
                                            }
                                        )
                                    }
                                    // Continuously scrolls the grid while a drag-select is active
                                    // and the finger sits near the top or bottom edge - runs on its
                                    // own timer rather than piggybacking on drag events, so it keeps
                                    // scrolling even if the finger holds still in the edge zone.
                                    launch {
                                        val edgeZonePx = 90f
                                        val maxScrollPerTickPx = 22f
                                        while (isActive) {
                                            val pos = dragPosition
                                            if (isDragging && pos != null) {
                                                val height = size.height.toFloat()
                                                val distanceFromTop = pos.y
                                                val distanceFromBottom = height - pos.y
                                                when {
                                                    distanceFromTop < edgeZonePx -> {
                                                        val strength = 1f - (distanceFromTop / edgeZonePx).coerceIn(0f, 1f)
                                                        gridState.scrollBy(-maxScrollPerTickPx * strength)
                                                    }
                                                    distanceFromBottom < edgeZonePx -> {
                                                        val strength = 1f - (distanceFromBottom / edgeZonePx).coerceIn(0f, 1f)
                                                        gridState.scrollBy(maxScrollPerTickPx * strength)
                                                    }
                                                }
                                            }
                                            delay(16)
                                        }
                                    }
                                }
                            }
                    ) {
                        items(photos, key = { it.id }) { photo ->
                            val isSelected = photo.id in selected
                            Box(
                                modifier = Modifier
                                    .padding(3.dp)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(8.dp))
                            ) {
                                AsyncImage(
                                    model = photo.uri,
                                    contentDescription = photo.displayName,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                if (photo.isVideo) {
                                    Row(
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Filled.PlayArrow,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            formatDuration(photo.durationMs),
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 10.sp)
                                        )
                                    }
                                }
                                if (isSelected) {
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.35f))
                                    )
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    viewerPhoto?.let { photo ->
        PhotoViewerOverlay(
            photo = photo,
            onClose = { viewerPhoto = null },
            onToggleFavorite = { p ->
                try {
                    val intent = viewModel.buildFavoriteIntentForPhoto(p, !p.isFavorite)
                    favoriteLauncher.launch(IntentSenderRequest.Builder(intent.intentSender).build())
                } catch (_: Exception) { }
            }
        )
    }
}

/** Which grid item (by index into the photo list) currently contains this position, if any. */
private fun indexAt(gridState: LazyGridState, position: Offset): Int? {
    val item = gridState.layoutInfo.visibleItemsInfo.find { info ->
        val x = position.x.toInt()
        val y = position.y.toInt()
        x >= info.offset.x && x < info.offset.x + info.size.width &&
            y >= info.offset.y && y < info.offset.y + info.size.height
    }
    return item?.index
}
