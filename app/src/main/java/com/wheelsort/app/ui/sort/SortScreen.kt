package com.wheelsort.app.ui.sort

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.wheelsort.app.R
import com.wheelsort.app.data.Photo
import com.wheelsort.app.util.formatBytes
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun SortScreen(
    albumFilter: String?,
    newestFirst: Boolean = true,
    onExit: () -> Unit,
    onOpenTrash: () -> Unit,
    viewModel: SortViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val dragState = rememberSwipeCardDragState()
    var viewerPhoto by remember { mutableStateOf<Photo?>(null) }

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
    val favoriteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { /* no-op, best effort */ }

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

    LaunchedEffect(albumFilter, newestFirst) { viewModel.loadPhotos(albumFilter, newestFirst) }

    Scaffold(
        topBar = {
            SortTopBar(
                reviewed = uiState.reviewedCount,
                total = uiState.photos.size,
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
                    onNavigateDelta = { steps -> viewModel.goToDelta(steps) },
                    onTapCenter = { photo -> viewerPhoto = photo },
                    onOpenTrash = onOpenTrash,
                    pendingDeleteCount = uiState.pendingDeleteCount
                )
            }
        }
    }

    viewerPhoto?.let { photo ->
        PhotoViewerOverlay(
            photo = photo,
            onClose = { viewerPhoto = null },
            onToggleFavorite = { p ->
                try {
                    val intent = viewModel.buildFavoriteIntent(p, !p.isFavorite)
                    favoriteLauncher.launch(IntentSenderRequest.Builder(intent.intentSender).build())
                } catch (_: Exception) { }
            }
        )
    }

    BackHandler(onBack = ::handleExit)
}

@Composable
private fun SortTopBar(
    reviewed: Int,
    total: Int,
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

/** Slots rendered above/below center. Beyond ~3.5 slots the ring math fades them to invisible anyway. */
private const val VISIBLE_RADIUS = 4
private const val ANGLE_PER_SLOT_DEG = 24f
private val KEEP_COLOR = Color(0xFF00B894)
private val DELETE_COLOR = Color(0xFFFF5E5E)

/**
 * A real wheel: photos are positioned along a vertical circular arc (angle = distance-from-center
 * * a fixed step), so they curve away and shrink/fade continuously as they approach the edge -
 * exactly like looking at a rotating drum from the front - rather than a flat stack. A fast
 * vertical flick spins through several photos at once using real release velocity, and the whole
 * background tints green/red as you drag the centered photo left or right.
 */
@Composable
private fun WheelCarousel(
    uiState: SortUiState,
    dragState: SwipeCardDragState,
    onCommitDelete: (Photo) -> Unit,
    onCommitKeep: (Photo) -> Unit,
    onNavigateDelta: (Int) -> Unit,
    onTapCenter: (Photo) -> Unit,
    onOpenTrash: () -> Unit,
    pendingDeleteCount: Int
) {
    val photos = uiState.photos
    val currentIndex = uiState.currentIndex
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val verticalOffset = remember { Animatable(0f) }
    var slotHeightPx by remember { mutableFloatStateOf(1f) }
    var containerHeightPx by remember { mutableFloatStateOf(1f) }
    val decay = rememberSplineBasedDecay<Float>()
    val density = LocalDensity.current

    // pointerInput(Unit) keeps the same gesture coroutine alive across recompositions, so
    // callbacks inside it must read state through this rather than closing over `photos` /
    // `currentIndex` directly - otherwise they'd stay frozen at their first-composition values.
    val latestState = rememberUpdatedState(uiState)

    // Warm the image cache a few photos ahead in both directions so scrolling never waits on a
    // fresh decode - this, plus the bounded request sizes in PhotoCard, is what removes the lag.
    LaunchedEffect(currentIndex, photos) {
        val loader = context.imageLoader
        val range = (currentIndex - VISIBLE_RADIUS - 2)..(currentIndex + VISIBLE_RADIUS + 2)
        for (i in range) {
            val p = photos.getOrNull(i) ?: continue
            loader.enqueue(ImageRequest.Builder(context).data(p.uri).size(600).build())
        }
    }

    val commitDist = dragState.widthPx * SwipeTuning.COMMIT_DISTANCE_FRACTION
    val tintProgress = (abs(dragState.offsetX) / commitDist).coerceIn(0f, 1f)
    val tintColor = if (dragState.offsetX >= 0) KEEP_COLOR else DELETE_COLOR

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surface)
                )
            )
            .background(tintColor.copy(alpha = tintProgress * 0.4f))
            .onGloballyPositioned {
                containerHeightPx = it.size.height.toFloat()
                slotHeightPx = it.size.height * 0.30f
            }
            .wheelSortGesture(
                onTap = {
                    val state = latestState.value
                    state.photos.getOrNull(state.currentIndex)?.let(onTapCenter)
                },
                onHorizontalDrag = { dx -> dragState.offsetX += dx },
                onHorizontalDragEnd = { velocity ->
                    val state = latestState.value
                    val photo = state.photos.getOrNull(state.currentIndex)
                    if (photo != null) {
                        val dist = dragState.widthPx * SwipeTuning.COMMIT_DISTANCE_FRACTION
                        val right = dragState.offsetX > dist ||
                            (dragState.offsetX > 0f && velocity > SwipeTuning.COMMIT_VELOCITY_PX_PER_SEC)
                        val left = dragState.offsetX < -dist ||
                            (dragState.offsetX < 0f && velocity < -SwipeTuning.COMMIT_VELOCITY_PX_PER_SEC)
                        when {
                            right -> scope.launch {
                                animate(
                                    initialValue = dragState.offsetX,
                                    targetValue = dragState.widthPx * 1.4f,
                                    initialVelocity = velocity,
                                    animationSpec = tween(220)
                                ) { value, _ -> dragState.offsetX = value }
                                dragState.offsetX = 0f
                                onCommitKeep(photo)
                            }
                            left -> scope.launch {
                                animate(
                                    initialValue = dragState.offsetX,
                                    targetValue = -dragState.widthPx * 1.4f,
                                    initialVelocity = velocity,
                                    animationSpec = tween(220)
                                ) { value, _ -> dragState.offsetX = value }
                                dragState.offsetX = 0f
                                onCommitDelete(photo)
                            }
                            else -> scope.launch {
                                animate(
                                    initialValue = dragState.offsetX,
                                    targetValue = 0f,
                                    initialVelocity = velocity,
                                    animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)
                                ) { value, _ -> dragState.offsetX = value }
                            }
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
                        val decayTarget = decay.calculateTargetValue(
                            Float.VectorConverter, verticalOffset.value, velocity
                        )
                        var steps = (-decayTarget / slot).roundToInt()
                        val maxForward = (state.photos.size - 1 - state.currentIndex).coerceAtLeast(0)
                        val maxBackward = state.currentIndex.coerceAtLeast(0)
                        steps = steps.coerceIn(-maxBackward, maxForward)
                        val settleTarget = -steps * slot
                        // Heavier, springier feel (more "weight"/inertia) than a critically damped snap.
                        verticalOffset.animateTo(
                            targetValue = settleTarget,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            initialVelocity = velocity
                        )
                        if (steps != 0) onNavigateDelta(steps)
                        verticalOffset.snapTo(0f)
                    }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        val radiusPx = containerHeightPx * 0.62f

        // Draw furthest-from-center first, centered card last, so the interactive center
        // card is always on top of its shrinking, tilted neighbors.
        val drawOrder = (-VISIBLE_RADIUS..VISIBLE_RADIUS).sortedByDescending { abs(it) }
        for (slot in drawOrder) {
            val i = currentIndex + slot
            val photo = photos.getOrNull(i) ?: continue

            val signedDistance = slot + verticalOffset.value / slotHeightPx.coerceAtLeast(1f)
            val angleDeg = signedDistance * ANGLE_PER_SLOT_DEG
            val angleRad = Math.toRadians(angleDeg.toDouble())
            val yPx = (radiusPx * sin(angleRad)).toFloat()
            val depth = cos(angleRad).toFloat().coerceAtLeast(0f)
            if (depth < 0.02f) continue // fully edge-on - skip, it's invisible anyway

            val scale = 0.34f + 0.66f * depth
            val itemAlpha = depth.coerceIn(0f, 1f)
            val isNearCenter = abs(signedDistance) < 0.5f

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .fillMaxHeight(0.34f)
                    .graphicsLayer {
                        translationY = yPx
                        scaleX = scale
                        scaleY = scale
                        alpha = itemAlpha
                        rotationX = angleDeg
                        cameraDistance = 10f * density.density
                    }
            ) {
                if (isNearCenter) {
                    SwipeableCard(dragState = dragState, modifier = Modifier.fillMaxSize()) {
                        PhotoCard(photo, highPriority = true)
                    }
                } else {
                    PhotoCard(photo, highPriority = false)
                }
            }
        }

        Text(
            text = stringResource(R.string.sort_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 8.dp)
        )

        // Trash bin, visible right inside the wheel view - tap to jump into the Trash screen.
        TrashBinButton(
            pendingCount = pendingDeleteCount,
            onClick = onOpenTrash,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        )
    }
}

@Composable
private fun TrashBinButton(pendingCount: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 6.dp,
            modifier = Modifier.size(52.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.DeleteSweep,
                    contentDescription = stringResource(R.string.home_trash),
                    tint = DELETE_COLOR
                )
            }
        }
        if (pendingCount > 0) {
            Surface(
                shape = CircleShape,
                color = DELETE_COLOR,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(20.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        pendingCount.toString(),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp)
                    )
                }
            }
        }
    }
}

/**
 * [highPriority] photos (the centered card) decode at a larger target size; off-center photos
 * only need to look right shrunk down, so they request a smaller bitmap - both are bounded well
 * below the source photo's full resolution, and combined with the preload pass above and the
 * shared memory cache, this is what makes scrolling feel instant.
 */
@Composable
private fun PhotoCard(photo: Photo, highPriority: Boolean) {
    val context = LocalContext.current
    val targetPx = if (highPriority) 1000 else 500

    Card(
        modifier = Modifier
            .fillMaxSize()
            .shadow(8.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(photo.uri)
                .size(targetPx)
                .crossfade(150)
                .build(),
            contentDescription = photo.displayName,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}
