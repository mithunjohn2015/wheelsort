package com.wheelsort.app.ui.sort

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.wheelsort.app.R
import com.wheelsort.app.data.Photo
import com.wheelsort.app.util.formatBytes
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SortScreen(
    albumFilter: String?,
    onExit: () -> Unit,
    viewModel: SortViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val dragState = rememberSwipeCardDragState()

    var isFlushing by remember { mutableStateOf(false) }
    var popAfterFlush by remember { mutableStateOf(false) }

    val flushLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onFlushResult(result.resultCode == android.app.Activity.RESULT_OK)
        isFlushing = false
        if (popAfterFlush) {
            popAfterFlush = false
            onExit()
        }
    }

    fun performFlush(thenPop: Boolean) {
        val intent = viewModel.buildFlushIntent()
        if (intent == null) {
            if (thenPop) onExit()
            return
        }
        isFlushing = true
        popAfterFlush = thenPop
        flushLauncher.launch(IntentSenderRequest.Builder(intent.intentSender).build())
    }

    fun handleExit() {
        if (viewModel.uiState.value.pendingDeleteCount > 0) performFlush(thenPop = true) else onExit()
    }

    // Auto-flush once enough deletes have piled up locally, so the trash dialog stays rare.
    LaunchedEffect(uiState.pendingDeleteCount) {
        if (!isFlushing && viewModel.shouldAutoFlush()) performFlush(thenPop = false)
    }

    LaunchedEffect(albumFilter) { viewModel.loadPhotos(albumFilter) }

    Scaffold(
        topBar = {
            SortTopBar(
                reviewed = uiState.reviewedCount,
                total = uiState.photos.size,
                pendingDeletes = uiState.pendingDeleteCount,
                onExit = ::handleExit,
                onUndo = {
                    val entry = viewModel.undoLast()
                    if (entry != null && entry.action == SwipeAction.DELETE && entry.flushed) {
                        try { viewModel.buildRestoreIntent(entry.photo).send() } catch (_: Exception) { }
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                uiState.sessionComplete || uiState.photos.isEmpty() -> SessionCompleteState(
                    reviewed = uiState.reviewedCount,
                    freedBytes = uiState.spaceFreed,
                    onBack = ::handleExit
                )
                else -> WheelCarousel(
                    uiState = uiState,
                    dragState = dragState,
                    onCommitDelete = { photo -> viewModel.queueDelete(photo) },
                    onCommitKeep = { photo -> viewModel.onKeep(photo) },
                    onNavigateDelta = { steps -> viewModel.goToDelta(steps) }
                )
            }
        }
    }

    BackHandler(onBack = ::handleExit)
}

@Composable
private fun SortTopBar(
    reviewed: Int,
    total: Int,
    pendingDeletes: Int,
    onExit: () -> Unit,
    onUndo: () -> Unit
) {
    Column {
        TopAppBar(
            title = {
                Column {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                    if (total > 0) {
                        Text(
                            stringResource(R.string.sort_progress, (reviewed + 1).coerceAtMost(total), total),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onExit) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
            },
            actions = {
                if (pendingDeletes > 0) {
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        leadingIcon = {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text(pendingDeletes.toString()) }
                    )
                    Spacer(Modifier.width(4.dp))
                }
                IconButton(onClick = onUndo) {
                    Icon(Icons.Filled.Undo, contentDescription = stringResource(R.string.sort_undo))
                }
            }
        )
        if (total > 0) {
            LinearProgressIndicator(
                progress = { reviewed / total.toFloat() },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SessionCompleteState(reviewed: Int, freedBytes: Long, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.sort_complete_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.sort_complete_body, reviewed, formatBytes(freedBytes)),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onBack) { Text(stringResource(R.string.sort_complete_back)) }
        }
    }
}

/**
 * The actual "wheel": the centered photo renders large; photos above/below shrink and fade
 * the further they are from center, continuously, as you drag - and a fast vertical flick
 * spins through several photos at once using real release velocity (spline decay to pick
 * how far it should travel, then a velocity-seeded spring to settle).
 */
@Composable
private fun WheelCarousel(
    uiState: SortUiState,
    dragState: SwipeCardDragState,
    onCommitDelete: (Photo) -> Unit,
    onCommitKeep: (Photo) -> Unit,
    onNavigateDelta: (Int) -> Unit
) {
    val photos = uiState.photos
    val currentIndex = uiState.currentIndex
    val scope = rememberCoroutineScope()
    val verticalOffset = remember { Animatable(0f) }
    var slotHeightPx by remember { mutableFloatStateOf(1f) }
    val decay = rememberSplineBasedDecay<Float>()

    // pointerInput(Unit) keeps the same gesture coroutine alive across recompositions, so
    // callbacks inside it must read state through this rather than closing over `photos` /
    // `currentIndex` directly - otherwise they'd stay frozen at their first-composition values.
    val latestState = rememberUpdatedState(uiState)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surface)
                )
            )
            .onGloballyPositioned { slotHeightPx = it.size.height * 0.58f }
            .wheelSortGesture(
                onHorizontalDrag = { dx -> dragState.offsetX += dx },
                onHorizontalDragEnd = { velocity ->
                    val state = latestState.value
                    val photo = state.photos.getOrNull(state.currentIndex)
                    if (photo != null) {
                        val commitDist = dragState.widthPx * SwipeTuning.COMMIT_DISTANCE_FRACTION
                        val right = dragState.offsetX > commitDist ||
                            (dragState.offsetX > 0f && velocity > SwipeTuning.COMMIT_VELOCITY_PX_PER_SEC)
                        val left = dragState.offsetX < -commitDist ||
                            (dragState.offsetX < 0f && velocity < -SwipeTuning.COMMIT_VELOCITY_PX_PER_SEC)
                        when {
                            right -> { dragState.offsetX = 0f; onCommitKeep(photo) }
                            left -> { dragState.offsetX = 0f; onCommitDelete(photo) }
                            else -> dragState.offsetX = 0f
                        }
                    } else {
                        dragState.offsetX = 0f
                    }
                },
                onHorizontalDragCancel = { dragState.offsetX = 0f },
                onVerticalDrag = { dy ->
                    scope.launch { verticalOffset.snapTo(verticalOffset.value + dy) }
                },
                onVerticalDragEnd = { velocity ->
                    scope.launch {
                        val state = latestState.value
                        val slot = slotHeightPx.coerceAtLeast(1f)
                        val decayTarget = decay.calculateTargetValue(verticalOffset.value, velocity)
                        var steps = (-decayTarget / slot).roundToInt()
                        val maxForward = (state.photos.size - 1 - state.currentIndex).coerceAtLeast(0)
                        val maxBackward = state.currentIndex.coerceAtLeast(0)
                        steps = steps.coerceIn(-maxBackward, maxForward)
                        val settleTarget = -steps * slot
                        verticalOffset.animateTo(
                            targetValue = settleTarget,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            initialVelocity = velocity
                        )
                        if (steps != 0) onNavigateDelta(steps)
                        verticalOffset.snapTo(0f)
                    }
                }
            )
    ) {
        // Draw furthest-from-center first, centered card last, so the interactive center
        // card is always on top of its shrinking neighbors.
        for (slot in listOf(-2, 2, -1, 1, 0)) {
            val i = currentIndex + slot
            val photo = photos.getOrNull(i) ?: continue
            val baseY = slot * slotHeightPx
            val yPx = baseY + verticalOffset.value
            val distanceInSlots = abs(yPx) / slotHeightPx.coerceAtLeast(1f)
            val scale = (1f - 0.32f * distanceInSlots).coerceIn(0.4f, 1f)
            val itemAlpha = (1f - 0.6f * distanceInSlots).coerceIn(0.1f, 1f)
            val isNearCenter = distanceInSlots < 0.5f

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.86f)
                    .fillMaxHeight(0.6f)
                    .graphicsLayer {
                        translationY = yPx
                        scaleX = scale
                        scaleY = scale
                        alpha = itemAlpha
                    }
            ) {
                if (isNearCenter) {
                    SwipeableCard(dragState = dragState, modifier = Modifier.fillMaxSize()) {
                        PhotoCard(photo)
                    }
                } else {
                    PhotoCard(photo)
                }
            }
        }

        Text(
            text = stringResource(R.string.sort_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        )
    }
}

@Composable
private fun PhotoCard(photo: Photo) {
    Card(
        modifier = Modifier
            .fillMaxSize()
            .shadow(8.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        AsyncImage(
            model = photo.uri,
            contentDescription = photo.displayName,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}
