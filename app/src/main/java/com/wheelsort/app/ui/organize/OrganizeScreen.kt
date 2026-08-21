package com.wheelsort.app.ui.organize

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.wheelsort.app.R
import com.wheelsort.app.data.PhotoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A little color variety for the month rows - cycles through, purely decorative. */
private val MONTH_ACCENTS = listOf(
    Color(0xFF4A47E3), Color(0xFFE0704F), Color(0xFF1FAE83),
    Color(0xFFD6579B), Color(0xFF3FA7D6), Color(0xFFE0A72E)
)

@Composable
fun OrganizeScreen(
    onExit: () -> Unit,
    viewModel: OrganizeViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var lastResult by remember { mutableStateOf<OrganizeResult?>(null) }

    var albums by remember { mutableStateOf<List<String>>(emptyList()) }
    var albumChosen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        albums = withContext(Dispatchers.IO) { PhotoRepository(context).distinctAlbums() }
    }

    val writeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.performMove { res -> lastResult = res }
        }
    }

    fun chooseAlbum(name: String?) {
        albumChosen = true
        viewModel.refresh(name)
    }

    fun handleBack() {
        if (albumChosen) {
            albumChosen = false
            lastResult = null
        } else {
            onExit()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.organize_title)) },
                navigationIcon = {
                    IconButton(onClick = ::handleBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (albumChosen && uiState.groups.isNotEmpty()) {
                        TextButton(onClick = {
                            if (uiState.selected.size == uiState.groups.size) viewModel.clearSelection()
                            else viewModel.selectAll()
                        }) {
                            Text(
                                if (uiState.selected.size == uiState.groups.size) stringResource(R.string.organize_select_none)
                                else stringResource(R.string.organize_select_all)
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AnimatedContent(
                targetState = albumChosen,
                transitionSpec = {
                    if (targetState) {
                        (slideInHorizontally(tween(280)) { it / 3 } + fadeIn(tween(280))) togetherWith
                            (slideOutHorizontally(tween(280)) { -it / 4 } + fadeOut(tween(200)))
                    } else {
                        (slideInHorizontally(tween(280)) { -it / 4 } + fadeIn(tween(280))) togetherWith
                            (slideOutHorizontally(tween(280)) { it / 3 } + fadeOut(tween(200)))
                    }
                },
                label = "organizeStep"
            ) { chosen ->
                if (!chosen) {
                    FolderPickerStep(albums = albums, onChoose = ::chooseAlbum)
                } else {
                    MonthGroupsStep(
                        uiState = uiState,
                        lastResult = lastResult,
                        onToggleGroup = { viewModel.toggleGroup(it) },
                        onOrganize = {
                            val intent = viewModel.buildWriteRequest() ?: return@MonthGroupsStep
                            writeLauncher.launch(IntentSenderRequest.Builder(intent.intentSender).build())
                        },
                        onResultDone = { lastResult = null; onExit() }
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderPickerStep(albums: List<String>, onChoose: (String?) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.organize_pick_folder_info),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        )
        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                FolderRow(
                    name = stringResource(R.string.home_all_photos),
                    accent = MaterialTheme.colorScheme.primary,
                    onClick = { onChoose(null) }
                )
            }
            itemsIndexed(albums) { index, album ->
                FolderRow(
                    name = album,
                    accent = MONTH_ACCENTS[index % MONTH_ACCENTS.size],
                    onClick = { onChoose(album) }
                )
            }
        }
    }
}

@Composable
private fun FolderRow(name: String, accent: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

@Composable
private fun MonthGroupsStep(
    uiState: OrganizeUiState,
    lastResult: OrganizeResult?,
    onToggleGroup: (String) -> Unit,
    onOrganize: () -> Unit,
    onResultDone: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            lastResult != null -> OrganizeResultState(lastResult, onDone = onResultDone)
            uiState.groups.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.organize_empty), style = MaterialTheme.typography.bodyLarge)
            }
            else -> Column(Modifier.fillMaxSize()) {
                Text(
                    stringResource(R.string.organize_info),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                )
                LazyColumn(modifier = Modifier.weight(1f)) {
                    itemsIndexed(uiState.groups) { index, group ->
                        MonthGroupRow(
                            group = group,
                            accent = MONTH_ACCENTS[index % MONTH_ACCENTS.size],
                            selected = group.folderName in uiState.selected,
                            onClick = { onToggleGroup(group.folderName) }
                        )
                    }
                    item { Spacer(Modifier.height(96.dp)) }
                }
            }
        }

        AnimatedVisibility(
            visible = uiState.selected.isNotEmpty() && !uiState.isWorking && lastResult == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val selectedCount = uiState.groups.filter { it.folderName in uiState.selected }.sumOf { it.photos.size }
            Surface(shadowElevation = 8.dp) {
                Button(
                    onClick = onOrganize,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .height(56.dp)
                ) {
                    Icon(Icons.Filled.CreateNewFolder, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.organize_button, selectedCount, uiState.selected.size),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        if (uiState.isWorking) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.organize_working), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun MonthGroupRow(group: MonthGroup, accent: Color, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp))
        ) {
            AsyncImage(
                model = group.photos.first().uri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(group.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(13.dp), tint = accent)
                Spacer(Modifier.width(4.dp))
                Text(
                    "${group.folderName}  \u00b7  ${group.photos.size} photos",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn() + scaleIn(initialScale = 0.6f),
            exit = fadeOut() + scaleOut(targetScale = 0.6f)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

@Composable
private fun OrganizeResultState(result: OrganizeResult, onDone: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.organize_done_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.organize_done_body, result.moved, result.folderCount),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
            if (result.failed > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.organize_done_failed, result.failed),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = onDone) { Text(stringResource(R.string.organize_done_back)) }
        }
    }
}
