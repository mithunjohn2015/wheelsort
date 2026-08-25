package com.wheelsort.app.ui.sort

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.imageLoader
import coil.request.ImageRequest
import com.wheelsort.app.R
import com.wheelsort.app.data.Photo
import com.wheelsort.app.util.formatBytes
import com.wheelsort.app.util.formatDuration
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class SortPhase { LOADING, COMPLETE, WHEEL }

@Composable
fun SortScreen(
    albumFilter: String?,
    newestFirst: Boolean = true,
    screenshotsFirst: Boolean = false,
    onExit: () -> Unit,
    onOpenTrash: () -> Unit,
    viewModel: SortViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val dragState = rememberSwipeCardDragState()
    var viewerPhoto by remember { mutableStateOf<Photo?>(null) }

    var isFlushing by remember { mutableStateOf(false) }
    var afterFlush by remember { mutableStateOf<(() -> Unit)?>(null) }

    val flushLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onFlushResult(result.resultCode == android.app.Activity.RESULT_OK)
        isFlushing = false
        afterFlush?.invoke()
        afterFlush = null
    }
    val favoriteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { /* no-op, best effort */ }

    fun performFlush(then: () -> Unit) {
        val intent = viewModel.buildFlushIntent()
        if (intent == null) {
            then()
            return
        }
        isFlushing = true
        afterFlush = then
        flushLauncher.launch(IntentSenderRequest.Builder(intent.intentSender).build())
    }

    fun handleExit() {
        if (viewModel.uiState.value.pendingDeleteCount > 0) performFlush(then = onExit) else onExit()
    }

    fun handleOpenTrash() {
        if (viewModel.uiState.value.pendingDeleteCount > 0) performFlush(then = onOpenTrash) else onOpenTrash()
    }

    LaunchedEffect(albumFilter, newestFirst, screenshotsFirst) {
        viewModel.loadPhotos(albumFilter, newestFirst, screenshotsFirst)
    }

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
        val phase = when {
            uiState.isLoading -> SortPhase.LOADING
            uiState.sessionComplete || uiState.photos.isEmpty() -> SortPhase.COMPLETE
            else -> SortPhase.WHEEL
        }
        Box(Modifier.fillMaxSize().padding(padding)) {
            AnimatedContent(
                targetState = phase,
                transitionSpec = { fadeIn(tween(280)) togetherWith fadeOut(tween(180)) },
                label = "sortPhase"
            ) { p ->
                when (p) {
                    SortPhase.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    SortPhase.COMPLETE -> SessionCompleteState(
                        reviewed = uiState.reviewedCount,
                        freedBytes = uiState.spaceFreed,
                        onBack = ::handleExit
                    )
                    SortPhase.WHEEL -> WheelCarousel(
                        uiState = uiState,
                        dragState = dragState,
                        onCommitDelete = { photo -> viewModel.queueDelete(photo) },
                        onCommitKeep = { photo -> viewModel.onKeep(photo) },
                        onNavigateDelta = { steps -> viewModel.goToDelta(steps) },
                        onTapCenter = { photo -> viewerPhoto = photo },
                        onOpenTrash = ::handleOpenTrash,
                        pendingDeleteCount = uiState.pendingDeleteCount
                    )
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

/** Only prev/current/next are rendered - simpler, calmer, and cuts decode load significantly. */
private const val VISIBLE_RADIUS = 1
private const val PHOTO_REQUEST_PX = 900
private const val MIN_SCALE = 0.90f
private const val MIN_ALPHA = 0.55f
private const val SLOT_SPACING_FRACTION = 0.13f
private val KEEP_COLOR = com.wheelsort.app.ui.theme.ActionKeep
private val DELETE_COLOR = com.wheelsort.app.ui.theme.ActionDelete
private val NEUTRAL_SHADOW = Color.Black.copy(alpha = 0.32f)

/** Smoothstep - cheap, and gives motion a gentler ease-out than a raw linear mapping. */
private fun smooth(t: Float): Float {
    val c = t.coerceIn(0f, 1f)
    return c * c * (3f - 2f * c)
}

/** Vertical screen position (px, relative to wheel center) for a slot at [signedDistance]. */
private fun slotTranslationY(signedDistance: Float, spacingPx: Float): Float = signedDistance * spacingPx

/** 1 = dead center (full size), shrinking smoothly as distance grows. */
private fun slotScale(signedDistance: Float): Float {
    val closeness = 1f - abs(signedDistance).coerceIn(0f, 1f)
    return MIN_SCALE + (1f - MIN_SCALE) * smooth(closeness)
}

private fun slotAlpha(signedDistance: Float): Float {
    val closeness = 1f - abs(signedDistance).coerceIn(0f, 1f)
    return MIN_ALPHA + (1f - MIN_ALPHA) * smooth(closeness)
}

/**
 * The wheel: the centered photo renders at full size; the previous/next photo shrink and fade
 * continuously as you drag, growing back to full size as they cross through center - a plain,
 * reliable scale animation rather than a 3D-tilted one (which is more prone to rendering glitches
 * on some devices' hardware layers). A fast vertical flick spins through several photos using
 * real release velocity. The centered card glows green/red as you drag it.
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
    val haptics = LocalHapticFeedback.current
    val verticalOffset = remember { Animatable(0f) }
    var slotHeightPx by remember { mutableFloatStateOf(1f) }
    var containerHeightPx by remember { mutableFloatStateOf(1f) }
    val decay = rememberSplineBasedDecay<Float>()

    // pointerInput(Unit) keeps the same gesture coroutine alive across recompositions, so
    // callbacks inside it must read state through this rather than closing over `photos` /
    // `currentIndex` directly - otherwise they'd stay frozen at their first-composition values.
    val latestState = rememberUpdatedState(uiState)

    // Which photo is actually under the finger right now - resolved fresh on every touch-down
    // by comparing the touch position against each visible slot's CURRENT on-screen position
    // (mid-animation state included). This is what the swipe acts on, not just "whatever
    // currentIndex happens to be" - fixes swiping the wrong photo when the wheel is still settling.
    var activeSwipeSlot by remember { mutableIntStateOf(0) }

    // Warm the cache a few photos ahead in both directions, at the EXACT same request size
    // PhotoCard displays at - a mismatched preload size is a cache miss in disguise, which was
    // the real reason scrolling still felt laggy even with preloading in place before.
    LaunchedEffect(currentIndex, photos) {
        val loader = context.imageLoader
        val range = (currentIndex - VISIBLE_RADIUS - 8)..(currentIndex + VISIBLE_RADIUS + 8)
        for (i in range) {
            val p = photos.getOrNull(i) ?: continue
            loader.enqueue(ImageRequest.Builder(context).data(p.uri).size(PHOTO_REQUEST_PX).build())
        }
    }

    val commitDist = dragState.widthPx * SwipeTuning.COMMIT_DISTANCE_FRACTION
    val dragProgress = (abs(dragState.offsetX) / commitDist).coerceIn(0f, 1f)
    val dragDirectionColor = if (dragState.offsetX >= 0) KEEP_COLOR else DELETE_COLOR

    Box(modifier = Modifier.fillMaxSize()) {
        // Base + soft colorful gradient-mesh pattern, sitting behind everything else.
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        WheelMeshBackground(modifier = Modifier.fillMaxSize())

        Box(
            modifier = Modifier
                .fillMaxSize()
                // Clearly visible now - this is the main swipe-direction feedback.
                .background(dragDirectionColor.copy(alpha = dragProgress * 0.38f))
                .onGloballyPositioned {
                    containerHeightPx = it.size.height.toFloat()
                    slotHeightPx = it.size.height * 0.30f
                }
                .wheelSortGesture(
                    onDown = { position ->
                        val state = latestState.value
                    val slotPx = slotHeightPx.coerceAtLeast(1f)
                    val spacing = containerHeightPx * SLOT_SPACING_FRACTION
                    val touchFromCenter = position.y - containerHeightPx / 2f
                    var bestSlot = 0
                    var bestDist = Float.MAX_VALUE
                    for (candidate in -VISIBLE_RADIUS..VISIBLE_RADIUS) {
                        if (state.photos.getOrNull(state.currentIndex + candidate) == null) continue
                        val signedDistance = candidate + verticalOffset.value / slotPx
                        val y = slotTranslationY(signedDistance, spacing)
                        val d = abs(touchFromCenter - y)
                        if (d < bestDist) { bestDist = d; bestSlot = candidate }
                    }
                    activeSwipeSlot = bestSlot
                },
                onTap = {
                    val state = latestState.value
                    state.photos.getOrNull(state.currentIndex + activeSwipeSlot)?.let(onTapCenter)
                },
                onHorizontalDrag = { dx -> dragState.offsetX += dx },
                onHorizontalDragEnd = { velocity ->
                    val state = latestState.value
                    val photo = state.photos.getOrNull(state.currentIndex + activeSwipeSlot)
                    if (photo != null) {
                        val dist = dragState.widthPx * SwipeTuning.COMMIT_DISTANCE_FRACTION
                        val right = dragState.offsetX > dist ||
                            (dragState.offsetX > 0f && velocity > SwipeTuning.COMMIT_VELOCITY_PX_PER_SEC)
                        val left = dragState.offsetX < -dist ||
                            (dragState.offsetX < 0f && velocity < -SwipeTuning.COMMIT_VELOCITY_PX_PER_SEC)
                        when {
                            right -> scope.launch {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                animate(
                                    initialValue = dragState.offsetX,
                                    targetValue = dragState.widthPx * 1.4f,
                                    initialVelocity = velocity,
                                    animationSpec = tween(190, easing = FastOutLinearInEasing)
                                ) { value, _ -> dragState.offsetX = value }
                                dragState.offsetX = 0f
                                onCommitKeep(photo)
                            }
                            left -> scope.launch {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                animate(
                                    initialValue = dragState.offsetX,
                                    targetValue = -dragState.widthPx * 1.4f,
                                    initialVelocity = velocity,
                                    animationSpec = tween(190, easing = FastOutLinearInEasing)
                                ) { value, _ -> dragState.offsetX = value }
                                dragState.offsetX = 0f
                                onCommitDelete(photo)
                            }
                            else -> scope.launch {
                                animate(
                                    initialValue = dragState.offsetX,
                                    targetValue = 0f,
                                    initialVelocity = velocity,
                                    animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMedium)
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
                        // A touch of overshoot for weight/inertia, but resolves quickly - precise, not floaty.
                        verticalOffset.animateTo(
                            targetValue = settleTarget,
                            animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMedium),
                            initialVelocity = velocity
                        )
                        if (steps != 0) {
                            onNavigateDelta(steps)
                        }
                        verticalOffset.snapTo(0f)
                    }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        // Draw furthest-from-the-active-card first, active card last, so whichever photo is
        // actually being swiped renders on top of its shrinking neighbors.
        val drawOrder = (-VISIBLE_RADIUS..VISIBLE_RADIUS).sortedByDescending { abs(it - activeSwipeSlot) }
        for (slot in drawOrder) {
            val i = currentIndex + slot
            val photo = photos.getOrNull(i) ?: continue
            val isDraggable = slot == activeSwipeSlot

            key(slot) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.84f)
                        .fillMaxHeight(0.42f)
                        .onGloballyPositioned { if (isDraggable) dragState.widthPx = it.size.width.toFloat() }
                        .graphicsLayer {
                            // Everything here reads live animated/drag state directly at draw-time,
                            // so the wheel spinning does NOT trigger recomposition every frame - only
                            // the render layer's transform updates, which is what makes this smooth.
                            val signedDistance = slot + verticalOffset.value / slotHeightPx.coerceAtLeast(1f)
                            val spacing = containerHeightPx * SLOT_SPACING_FRACTION
                            val dragMag = (abs(dragState.offsetX) / dragState.widthPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
                            val dim = if (isDraggable) 1f else 1f - dragMag * 0.45f
                            val scale = slotScale(signedDistance)

                            translationY = slotTranslationY(signedDistance, spacing)
                            if (isDraggable) {
                                translationX = dragState.offsetX
                                rotationZ = (dragState.offsetX / 38f).coerceIn(-16f, 16f)
                            }
                            scaleX = scale
                            scaleY = scale
                            alpha = slotAlpha(signedDistance) * dim
                        }
                ) {
                    // PhotoCard is always called the same way here (never conditionally wrapped) -
                    // that structural stability is what lets its internal crossfade state survive
                    // across photo changes instead of being torn down and recreated.
                    PhotoCard(
                        photo = photo,
                        modifier = Modifier.fillMaxSize(),
                        glowColor = if (isDraggable && dragProgress > 0.02f) dragDirectionColor else null,
                        glowStrength = if (isDraggable) dragProgress else 0f
                    )
                }
            }
        }

        Text(
            text = stringResource(R.string.sort_hint),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 10.dp)
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
}

/**
 * A soft, colorful "gradient mesh" backdrop - several large, low-opacity radial blobs in the
 * app's accent palette. Reads as intentional and alive rather than a flat black/neutral void,
 * without competing with the photos for attention (kept subtle and mostly out of the center).
 */
@Composable
private fun WheelMeshBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawMeshBlob(Offset(w * 0.12f, h * 0.10f), w * 0.60f, Color(0xFF4A47E3))
        drawMeshBlob(Offset(w * 0.95f, h * 0.22f), w * 0.55f, Color(0xFFE0704F))
        drawMeshBlob(Offset(w * 0.05f, h * 0.88f), w * 0.60f, Color(0xFF1FAE83))
        drawMeshBlob(Offset(w * 1.0f, h * 0.92f), w * 0.55f, Color(0xFFD6579B))
    }
}

private fun DrawScope.drawMeshBlob(center: Offset, radius: Float, color: Color) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = 0.26f), color.copy(alpha = 0.10f), Color.Transparent),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}

@Composable
private fun TrashBinButton(pendingCount: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 3.dp,
            modifier = Modifier.size(50.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.DeleteSweep,
                    contentDescription = stringResource(R.string.home_trash),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        if (pendingCount > 0) {
            Surface(
                shape = CircleShape,
                color = DELETE_COLOR,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(18.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        pendingCount.toString(),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 10.sp)
                    )
                }
            }
        }
    }
}

/**
 * Every photo requests the exact same bounded size ([PHOTO_REQUEST_PX]), matching what the
 * preload pass above warms the cache with - a mismatched size here would silently defeat the
 * preload (different size = different cache entry = cache miss). The centered card gets an
 * optional colored glow shadow instead of a flat screen-wide tint.
 */
@Composable
private fun PhotoCard(
    photo: Photo,
    modifier: Modifier = Modifier,
    glowColor: Color? = null,
    glowStrength: Float = 0f
) {
    val shadowColor = if (glowColor != null) glowColor.copy(alpha = 0.85f) else NEUTRAL_SHADOW
    val elevation = lerp(8.dp, 26.dp, glowStrength.coerceIn(0f, 1f))

    Card(
        modifier = modifier.shadow(
            elevation = elevation,
            shape = RoundedCornerShape(24.dp),
            ambientColor = shadowColor,
            spotColor = shadowColor
        ),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box {
            CrossfadeThumbnail(
                uri = photo.uri,
                contentDescription = photo.displayName,
                sizePx = PHOTO_REQUEST_PX,
                modifier = Modifier.fillMaxSize()
            )
            if (photo.isVideo) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Surface(
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        formatDuration(photo.durationMs),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * Coil's AsyncImage resets to its placeholder the instant `model` changes, even if the new image
 * is already cached - which reads as a visible flash/blink every time the wheel moves to a new
 * photo. This keeps the LAST successfully loaded image visible (remembered per call-site, so it's
 * tied to this slot's stable identity via the key(slot) at the call site) until the next one is
 * actually ready, then swaps - so there's never a blank frame between photos.
 */
@Composable
private fun CrossfadeThumbnail(
    uri: android.net.Uri,
    contentDescription: String?,
    sizePx: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var lastPainter by remember { mutableStateOf<androidx.compose.ui.graphics.painter.Painter?>(null) }

    coil.compose.SubcomposeAsyncImage(
        model = ImageRequest.Builder(context).data(uri).size(sizePx).build(),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Crop
    ) {
        val state = painter.state
        if (state is coil.compose.AsyncImagePainter.State.Success) {
            lastPainter = state.painter
        }
        val toShow = (state as? coil.compose.AsyncImagePainter.State.Success)?.painter ?: lastPainter
        if (toShow != null) {
            androidx.compose.foundation.Image(
                painter = toShow,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
        }
    }
}
