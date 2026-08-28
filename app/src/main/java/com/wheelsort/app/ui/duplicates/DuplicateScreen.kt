package com.wheelsort.app.ui.duplicates

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.wheelsort.app.data.DuplicateGroup
import com.wheelsort.app.data.DuplicateUiState
import com.wheelsort.app.data.PhotoRepository
import com.wheelsort.app.ui.theme.ActionKeep
import com.wheelsort.app.util.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DuplicateScreen(
    onExit: () -> Unit,
    viewModel: DuplicateViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Defaults to the picker on every fresh entry to this screen - previously this always
    // jumped straight to whatever the last scan's results were (hasScanned never resets, by
    // design, so the scan can keep running in the background across navigation), with no way
    // back to the picker short of leaving the screen entirely, which also meant there was no
    // way to do anything else. If a scan happens to be actively running when this screen opens,
    // show that instead, so you can watch progress or stop it.
    var showingPicker by remember { mutableStateOf(!uiState.isScanning) }

    androidx.activity.compose.BackHandler {
        if (!showingPicker && !uiState.isScanning) {
            // Viewing results (or empty-results) - back goes to the picker first, not straight
            // out, so you can choose a different folder without leaving the screen.
            showingPicker = true
        } else {
            onExit()
        }
    }

    val trashLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) viewModel.onDeleteConfirmed()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Find duplicates") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (!showingPicker && !uiState.isScanning) showingPicker = true else onExit()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (!showingPicker && !uiState.isScanning) {
                        TextButton(onClick = { showingPicker = true }) {
                            Text("Choose folder")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isScanning -> ScanningState(
                    scanned = uiState.scannedCount,
                    total = uiState.totalCount,
                    scopeAlbum = uiState.scopeAlbum,
                    onStop = { viewModel.stopScan() }
                )
                showingPicker -> FolderPickerState(
                    onScan = { album ->
                        // Picking the exact same folder the last completed scan already covered
                        // shows those results directly instead of needlessly re-scanning.
                        if (uiState.hasScanned && album == uiState.scopeAlbum) {
                            showingPicker = false
                        } else {
                            viewModel.startScan(album)
                            showingPicker = false
                        }
                    },
                    analyzedAlbums = viewModel.analyzedAlbums()
                )
                uiState.groups.isEmpty() -> EmptyResultState(
                    wasStoppedEarly = uiState.wasStoppedEarly,
                    onRescan = { viewModel.startScan(uiState.scopeAlbum) }
                )
                else -> ResultsState(
                    uiState = uiState,
                    onToggle = { viewModel.toggleSelection(it) },
                    onContinueScan = { viewModel.startScan(uiState.scopeAlbum) },
                    onDelete = {
                        val intent = viewModel.buildDeleteIntent() ?: return@ResultsState
                        trashLauncher.launch(IntentSenderRequest.Builder(intent.intentSender).build())
                    }
                )
            }
        }
    }
}

@Composable
private fun FolderPickerState(onScan: (String?) -> Unit, analyzedAlbums: Set<String>) {
    val context = LocalContext.current
    var albums by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        albums = withContext(Dispatchers.IO) { PhotoRepository(context).distinctAlbums() }
    }

    Column(Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text(
                "Scans your photos and groups ones that look alike \u2014 bursts, accidental copies, near-identical shots.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Pick a folder to scan just that one, or scan everything at once.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                FolderPickerRow(
                    name = "All photos",
                    analyzed = false,
                    onClick = { onScan(null) }
                )
            }
            items(albums, key = { it }) { album ->
                FolderPickerRow(
                    name = album,
                    analyzed = album in analyzedAlbums,
                    onClick = { onScan(album) }
                )
            }
        }
    }
}

@Composable
private fun FolderPickerRow(name: String, analyzed: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.PhotoLibrary,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(14.dp))
        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (analyzed) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Already analyzed",
                tint = ActionKeep,
                modifier = Modifier.size(18.dp)
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@Composable
private fun ScanningState(scanned: Int, total: Int, scopeAlbum: String?, onStop: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(scopeAlbum ?: "All photos", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            val progress = if (total > 0) scanned / total.toFloat() else 0f
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Text("$scanned of $total photos scanned", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = onStop) {
                Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Stop and review what's found so far")
            }
        }
    }
}

@Composable
private fun EmptyResultState(wasStoppedEarly: Boolean, onRescan: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = ActionKeep, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                if (wasStoppedEarly) "No duplicates found in what was scanned" else "No duplicates found",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            if (wasStoppedEarly) {
                Button(onClick = onRescan) { Text("Continue scanning") }
            } else {
                TextButton(onClick = onRescan) { Text("Scan again") }
            }
        }
    }
}

@Composable
private fun ResultsState(
    uiState: DuplicateUiState,
    onToggle: (Long) -> Unit,
    onContinueScan: () -> Unit,
    onDelete: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        val selectedCount = uiState.selectedForDeletion.size
        val selectedBytes = uiState.groups.flatMap { it.photos }
            .filter { it.id in uiState.selectedForDeletion }
            .sumOf { it.size }

        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
            Text(
                "${uiState.duplicateCount} duplicate photo${if (uiState.duplicateCount == 1) "" else "s"} found in ${uiState.groups.size} group${if (uiState.groups.size == 1) "" else "s"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (uiState.wasStoppedEarly) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Stopped early \u2014 only part of this folder was scanned.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onContinueScan) { Text("Continue") }
                }
            }
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(uiState.groups, key = { g -> g.photos.first().id }) { group ->
                DuplicateGroupCard(group, uiState.selectedForDeletion, onToggle)
            }
            item { Spacer(Modifier.height(88.dp)) }
        }

        if (selectedCount > 0) {
            Surface(shadowElevation = 8.dp) {
                Button(
                    onClick = onDelete,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(52.dp)
                ) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Delete $selectedCount \u2014 free up ${formatBytes(selectedBytes)}")
                }
            }
        }
    }
}

@Composable
private fun DuplicateGroupCard(group: DuplicateGroup, selected: Set<Long>, onToggle: (Long) -> Unit) {
    Column(Modifier.padding(vertical = 10.dp)) {
        Text(
            "${group.photos.size} similar photos",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(6.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp)) {
            items(group.photos, key = { it.id }) { photo ->
                val isKeep = photo.id == group.keepId
                val isMarkedForDeletion = photo.id in selected
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(100.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onToggle(photo.id) }
                ) {
                    AsyncImage(
                        model = photo.uri,
                        contentDescription = photo.displayName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    if (isMarkedForDeletion) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Marked for deletion",
                            tint = Color.White,
                            modifier = Modifier.align(Alignment.Center).size(28.dp)
                        )
                    }
                    if (isKeep) {
                        Surface(
                            color = ActionKeep,
                            shape = RoundedCornerShape(bottomEnd = 10.dp),
                            modifier = Modifier.align(Alignment.TopStart)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Icon(Icons.Filled.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(2.dp))
                                Text("Keep", color = Color.White, style = MaterialTheme.typography.labelLarge.copy(fontSize = 10.sp))
                            }
                        }
                    }
                }
            }
        }
    }
}
