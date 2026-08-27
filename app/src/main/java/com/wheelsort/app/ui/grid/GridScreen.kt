package com.wheelsort.app.ui.grid

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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

                    val latestUiState = rememberUpdatedState(uiState)

                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(columnCount),
                        contentPadding = PaddingValues(3.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            // Pinch, isolated in its own dedicated pointerInput and hand-rolled on
                            // the Initial pass rather than using detectTransformGestures (which
                            // only ever examines the default Main pass). Main pass propagates
                            // leaf-to-root, so the grid's own internal scroll handling - a child,
                            // nested inside this same LazyVerticalGrid - gets first look at every
                            // touch, including a two-finger pinch, before a Main-pass detector on
                            // this node ever sees it. That's the same class of bug already solved
                            // for the wheel's horizontal swipe against its list's scroll; pinch
                            // needed the identical fix. Kept as its own separate pointerInput
                            // (rather than merged with tap/drag-select below) to rule out any
                            // possibility of interference between detectors as a variable, since
                            // this is a repeat fix after prior attempts didn't resolve it.
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    var wasPinching = false
                                    var baselineDistance = 0f
                                    var accumulatedZoom = 1f

                                    while (true) {
                                        val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                        val pressed = event.changes.filter { it.pressed }

                                        if (pressed.size >= 2) {
                                            val p1 = pressed[0]
                                            val p2 = pressed[1]
                                            val currentDistance = (p1.position - p2.position).getDistance()
                                            if (!wasPinching) {
                                                wasPinching = true
                                                baselineDistance = currentDistance
                                            } else if (baselineDistance > 1f) {
                                                val zoom = currentDistance / baselineDistance
                                                accumulatedZoom *= zoom
                                                // Consume so the grid's own scroll handling doesn't
                                                // also try to interpret this multi-pointer movement.
                                                p1.consume()
                                                p2.consume()
                                                baselineDistance = currentDistance
                                                if (accumulatedZoom > 1.25f) {
                                                    columnCount = (columnCount - 1).coerceAtLeast(MIN_COLUMNS)
                                                    accumulatedZoom = 1f
                                                } else if (accumulatedZoom < 0.8f) {
                                                    columnCount = (columnCount + 1).coerceAtMost(MAX_COLUMNS)
                                                    accumulatedZoom = 1f
                                                }
                                            }
                                        } else {
                                            wasPinching = false
                                        }

                                        if (event.changes.none { it.pressed }) break
                                    }
                                }
                            }
                            // Keyed on Unit deliberately - this block used to be keyed on
                            // hasSelection/photos/columnCount, but this SAME block is what changes
                            // hasSelection (selecting an item) and columnCount (pinching). Keying
                            // on values the handler itself mutates meant every successful pinch
                            // step or every first selection tore the whole detector down and
                            // restarted it mid-gesture, cancelling whatever was in progress.
                            // rememberUpdatedState below provides fresh reads instead, so nothing
                            // needs a restart to stay current.
                            .pointerInput(Unit) {
                                // Shared between the drag-select coroutine and the auto-scroll
                                // coroutine below - auto-scroll needs to keep running even while
                                // the finger holds still near an edge, which onDrag alone can't
                                // do since it only fires on actual movement.
                                var dragPosition: Offset? = null
                                var isDragging = false

                                coroutineScope {
                                    launch {
                                        detectTapGestures(onTap = { pos ->
                                            val state = latestUiState.value
                                            val index = indexAt(gridState, pos) ?: return@detectTapGestures
                                            val photo = state.photos.getOrNull(index) ?: return@detectTapGestures
                                            if (state.selected.isNotEmpty()) viewModel.toggleSelect(photo.id) else viewerPhoto = photo
                                        })
                                    }
                                    launch {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { pos ->
                                                isDragging = true
                                                dragPosition = pos
                                                val index = indexAt(gridState, pos) ?: return@detectDragGesturesAfterLongPress
                                                latestUiState.value.photos.getOrNull(index)?.let { viewModel.ensureSelected(it.id) }
                                            },
                                            onDragEnd = { isDragging = false; dragPosition = null },
                                            onDragCancel = { isDragging = false; dragPosition = null },
                                            onDrag = { change, dragAmount ->
                                                dragPosition = change.position
                                                // Selects individual items the finger actually
                                                // sweeps over - not whole rows. Stepping along the
                                                // path between the previous and current position
                                                // (rather than only checking the exact endpoint)
                                                // catches items a fast sweep would otherwise skip
                                                // between two consecutive drag events, while still
                                                // allowing precise selection of just 1-2 photos at
                                                // a slower pace.
                                                val photos = latestUiState.value.photos
                                                val to = change.position
                                                val from = to - dragAmount
                                                val steps = 6
                                                for (i in 0..steps) {
                                                    val t = i / steps.toFloat()
                                                    val point = Offset(
                                                        from.x + (to.x - from.x) * t,
                                                        from.y + (to.y - from.y) * t
                                                    )
                                                    val index = indexAt(gridState, point) ?: continue
                                                    photos.getOrNull(index)?.let { viewModel.ensureSelected(it.id) }
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
                                if (hasSelection) {
                                    // Circular checkbox, always visible once selection mode is
                                    // active (not just on selected items) - outlined and empty
                                    // when unselected, filled with a checkmark when selected.
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(6.dp)
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary
                                                else Color.Black.copy(alpha = 0.32f)
                                            )
                                            .then(
                                                if (!isSelected) {
                                                    Modifier.border(1.5.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                                                } else Modifier
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                Icons.Filled.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
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
