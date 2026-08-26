package com.wheelsort.app.ui.sort

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.pow

private enum class SortPhase { LOADING, COMPLETE, WHEEL }

@Composable
fun SortScreen(
    albumFilter: String?,
    newestFirst: Boolean = true,
    screenshotsFirst: Boolean = false,
    onExit: () -> Unit,
    onOpenTrash: () -> Unit,
    viewModel: SortViewModel = viewModel(),
    settingsViewModel: com.wheelsort.app.ui.settings.SettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val wheelSettings by settingsViewModel.settings.collectAsState()
    var viewerPhoto by remember { mutableStateOf<Photo?>(null) }
    var showSettingsOverlay by remember { mutableStateOf(false) }

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
                currentIndex = uiState.currentIndex,
                total = uiState.photos.size,
                onExit = ::handleExit,
                onOpenSettings = { showSettingsOverlay = true },
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
                        settings = wheelSettings,
                        scrollToIndexEvents = viewModel.scrollToIndex,
                        onCommitDelete = { photo -> viewModel.queueDelete(photo) },
                        onCommitKeep = { photo -> viewModel.onKeep(photo) },
                        onCenterIndexChanged = { index -> viewModel.setCurrentIndex(index) },
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

    androidx.compose.animation.AnimatedVisibility(
        visible = showSettingsOverlay,
        enter = androidx.compose.animation.fadeIn(tween(180)),
        exit = androidx.compose.animation.fadeOut(tween(180))
    ) {
        com.wheelsort.app.ui.settings.WheelSettingsOverlay(
            settings = wheelSettings,
            onUpdate = settingsViewModel::update,
            onReset = { settingsViewModel.resetToDefaults() },
            onClose = { showSettingsOverlay = false }
        )
    }

    BackHandler(onBack = { if (showSettingsOverlay) showSettingsOverlay = false else handleExit() })
}

@Composable
private fun SortTopBar(
    currentIndex: Int,
    total: Int,
    onExit: () -> Unit,
    onOpenSettings: () -> Unit,
    onUndo: () -> Unit
) {
    Column {
        TopAppBar(
            title = {
                Column {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                    if (total > 0) {
                        Text(
                            stringResource(R.string.sort_progress, (currentIndex + 1).coerceIn(1, total), total),
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
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Tune, contentDescription = "Wheel settings")
                }
                IconButton(onClick = onUndo) {
                    Icon(Icons.Filled.Undo, contentDescription = stringResource(R.string.sort_undo))
                }
            }
        )
        if (total > 0) {
            LinearProgressIndicator(
                progress = { (currentIndex + 1).coerceIn(1, total) / total.toFloat() },
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

private const val PHOTO_REQUEST_PX = 900
/** How many photos ahead/behind to keep warm in Coil's cache beyond what's on screen. */
private const val PRELOAD_RADIUS = 6
private val KEEP_COLOR = com.wheelsort.app.ui.theme.ActionKeep
private val DELETE_COLOR = com.wheelsort.app.ui.theme.ActionDelete
private val NEUTRAL_SHADOW = Color.Black.copy(alpha = 0.32f)

private fun levelScale(distance: Float, shrinkPerLevel: Float): Float = shrinkPerLevel.pow(distance)
private fun levelDim(distance: Float, dimPerLevel: Float, maxDim: Float): Float =
    (distance * dimPerLevel).coerceIn(0f, maxDim)

/** Signed: negative above viewport center, positive below - needed to know which direction to push/pull for spacing. */
private fun centerSignedDistance(listState: LazyListState, index: Int): Float {
    val info = listState.layoutInfo
    val itemInfo = info.visibleItemsInfo.find { it.index == index } ?: return 3f
    if (itemInfo.size <= 0) return 3f
    val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2f
    val itemCenter = itemInfo.offset + itemInfo.size / 2f
    return (itemCenter - viewportCenter) / itemInfo.size.toFloat()
}

/** How far (in units of "item heights") a list item currently sits from the viewport's center. */
private fun centerDistance(listState: LazyListState, index: Int): Float = abs(centerSignedDistance(listState, index))

/**
 * The wheel, built directly on Compose's own scrolling/snapping engine instead of a hand-rolled
 * fixed window of composables. This is what actually fixes the recurring problems rather than
 * patching around them:
 *  - "loading in sets": LazyColumn composes whatever's actually near the viewport continuously as
 *    you scroll - there's no fixed window that has to jump to a new batch.
 *  - fling distance not matching swipe speed: a fast fling travels further using Compose's real
 *    fling physics, exactly like scrolling any normal list - slow to fast is all one continuum.
 *  - "click and stop in the middle": content padding sizes the list so each item naturally centers
 *    itself when Compose's own snap-to-item behavior settles after a fling.
 *  - wrong-photo-swiped / z-order bugs: the horizontal keep/delete gesture is only ever attached to
 *    the ACTUAL centered item's composable - there's no separate "which one did I touch" resolution
 *    to get wrong, and no draw-order bookkeeping needed, because there's nothing stacked to order.
 */
@Composable
private fun WheelCarousel(
    uiState: SortUiState,
    settings: com.wheelsort.app.data.WheelSettings,
    scrollToIndexEvents: kotlinx.coroutines.flow.Flow<Int>,
    onCommitDelete: (Photo) -> Unit,
    onCommitKeep: (Photo) -> Unit,
    onCenterIndexChanged: (Int) -> Unit,
    onTapCenter: (Photo) -> Unit,
    onOpenTrash: () -> Unit,
    pendingDeleteCount: Int
) {
    val photos = uiState.photos
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val snapLayoutInfo = remember(listState) { SnapLayoutInfoProvider(listState) }
    val flingBehavior = rememberSnapFlingBehavior(
        snapLayoutInfo,
        snapAnimationSpec = spring(dampingRatio = settings.snapDamping, stiffness = settings.snapStiffness)
    )
    val latestState = rememberUpdatedState(uiState)

    val dragOffsetX = remember { Animatable(0f) }
    var dragWidthPx by remember { mutableFloatStateOf(1f) }

    // Single source of truth for "which photo is active" - whichever item is currently nearest
    // the viewport's vertical center. Recomputed only when the underlying scroll state actually
    // changes (derivedStateOf), not on every recomposition.
    val centeredIndex by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            if (info.visibleItemsInfo.isEmpty()) return@derivedStateOf 0
            val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs((it.offset + it.size / 2) - viewportCenter) }?.index ?: 0
        }
    }

    LaunchedEffect(centeredIndex) { onCenterIndexChanged(centeredIndex) }

    // Programmatic scroll requests (undo restoring a photo) - separate from the reactive sync
    // above, which only flows the other direction (user scroll -> ViewModel).
    LaunchedEffect(Unit) {
        scrollToIndexEvents.collect { index ->
            if (photos.indices.contains(index)) listState.animateScrollToItem(index)
        }
    }

    LaunchedEffect(centeredIndex, photos) {
        val loader = context.imageLoader
        val range = (centeredIndex - PRELOAD_RADIUS)..(centeredIndex + PRELOAD_RADIUS)
        for (i in range) {
            val p = photos.getOrNull(i) ?: continue
            loader.enqueue(ImageRequest.Builder(context).data(p.uri).size(PHOTO_REQUEST_PX).build())
        }
    }

    val commitDist = dragWidthPx * settings.swipeCommitDistanceFraction
    val dragProgress = (abs(dragOffsetX.value) / commitDist).coerceIn(0f, 1f)
    val dragDirectionColor = if (dragOffsetX.value >= 0f) KEEP_COLOR else DELETE_COLOR
    val density = LocalDensity.current
    val itemSpacingPx = with(density) { settings.itemSpacingDp.dp.toPx() }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        WheelMeshBackground(modifier = Modifier.fillMaxSize())

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(dragDirectionColor.copy(alpha = dragProgress * 0.38f))
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val itemHeight = maxHeight * settings.cardHeightFraction
                val itemHeightPx = with(LocalDensity.current) { itemHeight.toPx() }
                val verticalPadding = ((maxHeight - itemHeight) / 2).coerceAtLeast(0.dp)

                LazyColumn(
                    state = listState,
                    flingBehavior = flingBehavior,
                    contentPadding = PaddingValues(vertical = verticalPadding),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(count = photos.size, key = { photos[it].id }) { index ->
                        val photo = photos[index]
                        val isCentered = index == centeredIndex

                        Box(
                            modifier = Modifier
                                .fillParentMaxWidth(settings.cardWidthFraction)
                                .height(itemHeight)
                                .align(Alignment.CenterHorizontally)
                                .graphicsLayer {
                                    // Reads live scroll state at DRAW time, not recomposition time -
                                    // this is what keeps scrolling itself from forcing recomposition.
                                    val signedDistance = centerSignedDistance(listState, index)
                                    val distance = abs(signedDistance)
                                    val scale = levelScale(distance, settings.shrinkPerLevel)
                                    scaleX = scale
                                    scaleY = scale
                                    // User-tunable extra pull-together/push-apart on top of natural
                                    // list spacing - negative dp overlaps neighboring photos more,
                                    // positive dp spaces them out further.
                                    translationY = signedDistance * itemSpacingPx
                                    if (isCentered) {
                                        translationX = dragOffsetX.value
                                        rotationZ = (dragOffsetX.value / 38f).coerceIn(-16f, 16f)
                                    }
                                }
                                .then(
                                    if (isCentered) {
                                        Modifier.pointerInput(photo.id) {
                                            dragWidthPx = size.width.toFloat()
                                            val velocityTracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
                                            coroutineScope {
                                                launch {
                                                    detectTapGestures(onTap = { onTapCenter(photo) })
                                                }
                                                launch {
                                                    detectHorizontalDragGestures(
                                                        onDragStart = { velocityTracker.resetTracking() },
                                                        onDragCancel = {
                                                            scope.launch {
                                                                dragOffsetX.animateTo(
                                                                    0f,
                                                                    spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMedium)
                                                                )
                                                            }
                                                        },
                                                        onDragEnd = {
                                                            val velocity = velocityTracker.calculateVelocity().x
                                                            val dist = dragWidthPx * settings.swipeCommitDistanceFraction
                                                            val right = dragOffsetX.value > dist ||
                                                                (dragOffsetX.value > 0f && velocity > settings.swipeCommitVelocity)
                                                            val left = dragOffsetX.value < -dist ||
                                                                (dragOffsetX.value < 0f && velocity < -settings.swipeCommitVelocity)
                                                            when {
                                                                right -> {
                                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                    scope.launch {
                                                                        animate(
                                                                            initialValue = dragOffsetX.value,
                                                                            targetValue = dragWidthPx * 1.4f,
                                                                            animationSpec = tween(190, easing = FastOutLinearInEasing)
                                                                        ) { value, _ -> dragOffsetX.snapTo(value) }
                                                                        dragOffsetX.snapTo(0f)
                                                                    }
                                                                    scope.launch { listState.animateScrollBy(itemHeightPx) }
                                                                    onCommitKeep(photo)
                                                                }
                                                                left -> {
                                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                    scope.launch {
                                                                        animate(
                                                                            initialValue = dragOffsetX.value,
                                                                            targetValue = -dragWidthPx * 1.4f,
                                                                            animationSpec = tween(190, easing = FastOutLinearInEasing)
                                                                        ) { value, _ -> dragOffsetX.snapTo(value) }
                                                                        dragOffsetX.snapTo(0f)
                                                                    }
                                                                    // Deleting removes the photo from the list, which shifts
                                                                    // every later index down by one - the item that was next
                                                                    // naturally reflows into this same visual position, so no
                                                                    // explicit scroll is needed here (unlike the keep case).
                                                                    onCommitDelete(photo)
                                                                }
                                                                else -> {
                                                                    scope.launch {
                                                                        dragOffsetX.animateTo(
                                                                            0f,
                                                                            spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMedium)
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        },
                                                        onHorizontalDrag = { change, dragAmount ->
                                                            change.consume()
                                                            velocityTracker.addPointerInputChange(change)
                                                            scope.launch { dragOffsetX.snapTo(dragOffsetX.value + dragAmount) }
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                            PhotoCard(
                                photo = photo,
                                modifier = Modifier.fillMaxSize(),
                                glowColor = if (isCentered && dragProgress > 0.02f) dragDirectionColor else null,
                                glowStrength = if (isCentered) dragProgress else 0f,
                                dimProvider = { levelDim(centerDistance(listState, index), settings.dimPerLevel, settings.maxDim) }
                            )
                        }
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
 *
 * [dimProvider] darkens back cards via an opaque scrim rather than by reducing alpha - a
 * translucent card would let whatever's behind it show through. It's a lambda (read inside
 * graphicsLayer, at draw-time) rather than a plain Float so scrolling can update it every frame
 * without forcing this composable to recompose - matching how the scale transform above works.
 */
@Composable
private fun PhotoCard(
    photo: Photo,
    modifier: Modifier = Modifier,
    glowColor: Color? = null,
    glowStrength: Float = 0f,
    dimProvider: () -> Float = { 0f }
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
        // Fully opaque surface so this card hides whatever is stacked behind it.
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box {
            CrossfadeThumbnail(
                uri = photo.uri,
                contentDescription = photo.displayName,
                sizePx = PHOTO_REQUEST_PX,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = dimProvider().coerceIn(0f, 1f) }
                    .background(Color.Black)
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
