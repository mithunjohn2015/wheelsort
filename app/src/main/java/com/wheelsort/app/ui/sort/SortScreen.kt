package com.wheelsort.app.ui.sort

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.wheelsort.app.R
import com.wheelsort.app.util.formatBytes
import kotlinx.coroutines.launch

@Composable
fun SortScreen(
    albumFilter: String?,
    onExit: () -> Unit,
    viewModel: SortViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var pendingDeletePhoto by remember { mutableStateOf<com.wheelsort.app.data.Photo?>(null) }
    val dragState = rememberSwipeCardDragState()

    val trashLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val photo = pendingDeletePhoto
        pendingDeletePhoto = null
        if (result.resultCode == android.app.Activity.RESULT_OK && photo != null) {
            viewModel.onDeleteConfirmed(photo)
            scope.launch {
                val res = snackbarHostState.showSnackbar(
                    message = context.getString(R.string.sort_undo_snackbar, photo.displayName),
                    actionLabel = context.getString(R.string.sort_undo)
                )
                if (res == SnackbarResult.ActionPerformed) {
                    val entry = viewModel.undoLast()
                    if (entry != null) {
                        // fire-and-forget restore; own-app-trashed items usually need no re-confirmation
                        try {
                            viewModel.buildRestoreIntent(entry.photo).send()
                        } catch (_: Exception) { }
                    }
                }
            }
        }
    }

    LaunchedEffect(albumFilter) { viewModel.loadPhotos(albumFilter) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            SortTopBar(
                reviewed = uiState.reviewedCount,
                total = uiState.photos.size,
                onExit = onExit,
                onUndo = {
                    val entry = viewModel.undoLast()
                    if (entry != null && entry.action == SwipeAction.DELETE) {
                        try { viewModel.buildRestoreIntent(entry.photo).send() } catch (_: Exception) { }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading -> LoadingState()
                uiState.sessionComplete || uiState.photos.isEmpty() -> SessionCompleteState(
                    reviewed = uiState.reviewedCount,
                    freedBytes = uiState.spaceFreed,
                    onBack = onExit
                )
                else -> {
                    val haptics = LocalHapticFeedback.current
                    WheelSortStack(
                        uiState = uiState,
                        dragState = dragState,
                        onCommitDelete = { photo ->
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            pendingDeletePhoto = photo
                            val intent = viewModel.buildTrashIntent(photo)
                            trashLauncher.launch(IntentSenderRequest.Builder(intent.intentSender).build())
                        },
                        onCommitKeep = { photo ->
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.onKeep(photo)
                        },
                        onNavigate = { up ->
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (up) viewModel.goToNext() else viewModel.goToPrevious()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SortTopBar(reviewed: Int, total: Int, onExit: () -> Unit, onUndo: () -> Unit) {
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

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
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
 * The "wheel": current photo full-bleed in the center, thin peeks of the
 * previous/next photo above and below hinting at the vertical carousel.
 * A single [wheelSortGesture] region interprets vertical drags as wheel
 * turns and horizontal drags as the keep/delete decision.
 */
@Composable
private fun WheelSortStack(
    uiState: SortUiState,
    dragState: SwipeCardDragState,
    onCommitDelete: (com.wheelsort.app.data.Photo) -> Unit,
    onCommitKeep: (com.wheelsort.app.data.Photo) -> Unit,
    onNavigate: (up: Boolean) -> Unit
) {
    val photo = uiState.photos.getOrNull(uiState.currentIndex) ?: return
    val prevPhoto = uiState.photos.getOrNull(uiState.currentIndex - 1)
    val nextPhoto = uiState.photos.getOrNull(uiState.currentIndex + 1)

    Column(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surface)
                )
            )
    ) {
        // top peek (previous photo)
        WheelPeek(uri = prevPhoto?.uri, modifier = Modifier.weight(0.12f))

        Box(
            modifier = Modifier
                .weight(0.76f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            AnimatedContent(
                targetState = uiState.currentIndex,
                transitionSpec = {
                    slideInVertically(tween(220)) { h -> h } togetherWith slideOutVertically(tween(220)) { h -> -h }
                },
                label = "wheelTurn"
            ) { _ ->
                SwipeableCard(
                    dragState = dragState,
                    modifier = Modifier
                        .fillMaxSize()
                        .wheelSortGesture(
                            onHorizontalDrag = { dx -> dragState.offsetX += dx },
                            onHorizontalDragEnd = {
                                val threshold = dragState.widthPx * 0.28f
                                when {
                                    dragState.offsetX > threshold -> {
                                        dragState.offsetX = 0f
                                        onCommitKeep(photo)
                                    }
                                    dragState.offsetX < -threshold -> {
                                        val toDelete = photo
                                        dragState.offsetX = 0f
                                        onCommitDelete(toDelete)
                                    }
                                    else -> dragState.offsetX = 0f
                                }
                            },
                            onHorizontalDragCancel = { dragState.offsetX = 0f },
                            onVerticalSwipe = { up -> onNavigate(up) }
                        )
                ) {
                    Card(
                        modifier = Modifier.fillMaxSize(),
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
            }
        }

        // bottom peek (next photo)
        WheelPeek(uri = nextPhoto?.uri, modifier = Modifier.weight(0.12f))

        Text(
            text = stringResource(R.string.sort_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        )
    }
}

@Composable
private fun WheelPeek(uri: android.net.Uri?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp)
            .clip(RoundedCornerShape(12.dp))
    ) {
        if (uri != null) {
            AsyncImage(
                model = uri,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.05f)),
                contentScale = ContentScale.Crop,
                alpha = 0.55f
            )
        }
    }
}
