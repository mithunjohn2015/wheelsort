package com.wheelsort.app.ui.sort

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
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
import kotlin.math.cos
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

// 900 was noticeably softer than the center card actually renders at on most modern phones
// (a card at ~55% of screen height on a 1440x3200-class device is well over 1000px tall).
// 1280 roughly doubles the pixel area for a real visible sharpness gain, while staying within
// what MediaStore's pre-generated thumbnail cache typically covers - going much higher risks
// falling back to a slow full-resolution decode per photo, reintroducing the lag that switching
// to loadThumbnail() was meant to fix in the first place.
private const val PHOTO_REQUEST_PX = 1280
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
    // NOTE: this Compose Foundation version only exposes rememberSnapFlingBehavior(provider) with
    // no way to pass a custom spring - the vertical scroll's settle animation isn't independently
    // tunable here. "Swipe-back smoothness/bounciness" in settings instead drive the horizontal
    // keep/delete swipe's spring, which is something this code does have direct control over.
    val flingBehavior = rememberSnapFlingBehavior(snapLayoutInfo)
    val latestState = rememberUpdatedState(uiState)

    val dragOffsetX = remember { Animatable(0f) }

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

    val density = LocalDensity.current
    val itemSpacingPx = with(density) { settings.itemSpacingDp.dp.toPx() }
    // Read fresh inside the gesture detector below (which is only ever launched once) so that
    // adjusting a setting live via the in-wheel overlay actually takes effect immediately,
    // instead of only applying once the pointerInput happened to restart.
    val latestSettings = rememberUpdatedState(settings)

    Box(modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        WheelMeshBackground(modifier = Modifier.fillMaxSize())

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val itemHeight = maxHeight * settings.cardHeightFraction
            val itemWidth = maxWidth * settings.cardWidthFraction
            val itemHeightPx = with(density) { itemHeight.toPx() }
            // Computed directly from layout constraints rather than measured off whichever card
            // happened to be centered - that measurement is what used to make swipe commit
            // distance depend on a specific item's pointerInput having already run once.
            val itemWidthPx = with(density) { itemWidth.toPx() }
            val verticalPadding = ((maxHeight - itemHeight) / 2).coerceAtLeast(0.dp)
            val latestItemWidthPx = rememberUpdatedState(itemWidthPx)
            val latestItemHeightPx = rememberUpdatedState(itemHeightPx)

            val commitDist = (itemWidthPx * settings.swipeCommitDistanceFraction).coerceAtLeast(1f)
            val dragProgress = (abs(dragOffsetX.value) / commitDist).coerceIn(0f, 1f)
            val dragDirectionColor = if (dragOffsetX.value >= 0f) KEEP_COLOR else DELETE_COLOR

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(dragDirectionColor.copy(alpha = dragProgress * 0.38f))
                    // A SINGLE gesture detector, permanently attached here rather than
                    // conditionally attached to whichever item happens to be centered. That
                    // conditional attachment was the actual bug: a freshly-centered item's
                    // detector needs a brand new touch-down to start working, so any swipe
                    // attempted in the moment right after landing on a photo - especially after
                    // a fast flick, where the target keeps changing until the fling settles -
                    // fell through to the list's own vertical scroll instead, with no horizontal
                    // detector yet in place to claim it. This detector always exists, so it's
                    // always ready the instant you touch down, and looks up whichever photo is
                    // CURRENTLY centered at gesture-time rather than baking in a specific one.
                    .pointerInput(Unit) {
                        val velocityTracker = VelocityTracker()

                        suspend fun springBack() {
                            val s = latestSettings.value
                            dragOffsetX.animateTo(0f, spring(dampingRatio = s.snapDamping, stiffness = s.snapStiffness))
                        }

                        suspend fun commitDrag() {
                            val photo = latestState.value.photos.getOrNull(centeredIndex) ?: return
                            val s = latestSettings.value
                            val widthPx = latestItemWidthPx.value
                            val velocity = velocityTracker.calculateVelocity().x
                            val dist = widthPx * s.swipeCommitDistanceFraction
                            val right = dragOffsetX.value > dist ||
                                (dragOffsetX.value > 0f && velocity > s.swipeCommitVelocity)
                            val left = dragOffsetX.value < -dist ||
                                (dragOffsetX.value < 0f && velocity < -s.swipeCommitVelocity)
                            when {
                                right -> {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    scope.launch {
                                        dragOffsetX.animateTo(
                                            targetValue = widthPx * 1.4f,
                                            animationSpec = tween(190, easing = FastOutLinearInEasing)
                                        )
                                        dragOffsetX.snapTo(0f)
                                    }
                                    scope.launch { listState.animateScrollBy(latestItemHeightPx.value) }
                                    onCommitKeep(photo)
                                }
                                left -> {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    scope.launch {
                                        dragOffsetX.animateTo(
                                            targetValue = -widthPx * 1.4f,
                                            animationSpec = tween(190, easing = FastOutLinearInEasing)
                                        )
                                        dragOffsetX.snapTo(0f)
                                    }
                                    // Deleting removes the photo from the list, which shifts
                                    // every later index down by one - the item that was next
                                    // naturally reflows into this same visual position, so no
                                    // explicit scroll is needed here (unlike the keep case).
                                    onCommitDelete(photo)
                                }
                                else -> scope.launch { springBack() }
                            }
                        }

                        coroutineScope {
                            launch {
                                detectTapGestures(onTap = {
                                    latestState.value.photos.getOrNull(centeredIndex)?.let(onTapCenter)
                                })
                            }
                            launch {
                                // Hand-written instead of detectHorizontalDragGestures, which only
                                // ever examines events on the Main pass. Main pass propagates
                                // leaf-to-root - LazyColumn (the child here) gets first look at
                                // every touch event and can claim it as a vertical scroll before a
                                // Main-pass detector on this parent ever sees it, no matter how
                                // clearly horizontal the drag was. Initial pass propagates root-to-
                                // leaf instead, so examining events here means this code decides
                                // the axis and can consume a horizontal drag BEFORE LazyColumn's own
                                // scroll handling gets its turn - which is what actually fixes swipes
                                // losing to scroll instead of just changing where the detector lives.
                                awaitEachGesture {
                                    val down = awaitFirstDown(pass = PointerEventPass.Initial)
                                    velocityTracker.resetTracking()
                                    velocityTracker.addPointerInputChange(down)
                                    val pointerId = down.id
                                    var lockedHorizontal = false
                                    var lastX = down.position.x

                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                                        if (change.isConsumed) break
                                        if (!change.pressed) break

                                        velocityTracker.addPointerInputChange(change)

                                        if (!lockedHorizontal) {
                                            val total = change.position - down.position
                                            if (total.getDistance() > viewConfiguration.touchSlop) {
                                                if (abs(total.x) > abs(total.y)) {
                                                    lockedHorizontal = true
                                                } else {
                                                    // Clearly vertical - not ours. Stop watching this
                                                    // gesture entirely and let LazyColumn's own Main-
                                                    // pass handling see every event untouched.
                                                    break
                                                }
                                            }
                                        }

                                        if (lockedHorizontal) {
                                            change.consume()
                                            val delta = change.position.x - lastX
                                            scope.launch { dragOffsetX.snapTo(dragOffsetX.value + delta) }
                                        }
                                        lastX = change.position.x
                                    }

                                    if (lockedHorizontal) scope.launch { commitDrag() }
                                }
                            }
                        }
                    }
            ) {
                LazyColumn(
                    state = listState,
                    flingBehavior = flingBehavior,
                    contentPadding = PaddingValues(vertical = verticalPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(count = photos.size, key = { photos[it].id }) { index ->
                        val photo = photos[index]
                        val isCentered = index == centeredIndex

                        Box(
                            modifier = Modifier
                                .fillParentMaxWidth(settings.cardWidthFraction)
                                .height(itemHeight)
                                .graphicsLayer {
                                    // Reads live scroll state at DRAW time, not recomposition time -
                                    // this is what keeps scrolling itself from forcing recomposition.
                                    val signedDistance = centerSignedDistance(listState, index)
                                    val distance = abs(signedDistance)
                                    val scale = levelScale(distance, settings.shrinkPerLevel)

                                    when (settings.transitionStyle) {
                                        com.wheelsort.app.data.WheelTransitionStyle.STACK -> {
                                            scaleX = scale
                                            scaleY = scale
                                            // User-tunable extra pull-together/push-apart on top of
                                            // natural list spacing - negative dp overlaps neighboring
                                            // photos more, positive dp spaces them out further.
                                            translationY = signedDistance * itemSpacingPx
                                        }
                                        com.wheelsort.app.data.WheelTransitionStyle.FLIP -> {
                                            // Deliberately NOT using rotationX/cameraDistance (true 3D
                                            // rotation) - this exact combination caused a GPU-specific
                                            // transparent-card bug earlier in this project. This fakes
                                            // a similar "turning edge-on and flipping away" look safely
                                            // by squeezing scaleX toward zero instead of tilting in 3D.
                                            val angleDeg = (signedDistance * 55f).coerceIn(-80f, 80f)
                                            val angleRad = angleDeg * (kotlin.math.PI.toFloat() / 180f)
                                            val flipScaleX = cos(angleRad).coerceAtLeast(0.08f)
                                            scaleX = flipScaleX * scale
                                            scaleY = scale
                                            rotationZ = (signedDistance * 6f).coerceIn(-18f, 18f)
                                            translationY = signedDistance * itemSpacingPx * 0.6f
                                        }
                                        com.wheelsort.app.data.WheelTransitionStyle.SLIDE -> {
                                            // No shrink, no extra offset beyond natural list flow -
                                            // a plain flat slide, depth conveyed only by the dim scrim.
                                            scaleX = 1f
                                            scaleY = 1f
                                            translationY = 0f
                                        }
                                    }

                                    if (isCentered) {
                                        translationX = dragOffsetX.value
                                        rotationZ = (dragOffsetX.value / 38f).coerceIn(-16f, 16f)
                                    }
                                }
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
