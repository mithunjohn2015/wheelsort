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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.wheelsort.app.data.DuplicateGroup
import com.wheelsort.app.data.DuplicateUiState
import com.wheelsort.app.ui.theme.ActionKeep
import com.wheelsort.app.util.formatBytes

@Composable
fun DuplicateScreen(
    onExit: () -> Unit,
    viewModel: DuplicateViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

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
                    IconButton(onClick = onExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isScanning -> ScanningState(uiState.scannedCount, uiState.totalCount)
                !uiState.hasScanned -> IntroState(onScan = { viewModel.startScan() })
                uiState.groups.isEmpty() -> EmptyResultState(onRescan = { viewModel.startScan() })
                else -> ResultsState(
                    uiState = uiState,
                    onToggle = { viewModel.toggleSelection(it) },
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
private fun IntroState(onScan: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.PhotoLibrary,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Scans your photos and groups ones that look alike \u2014 bursts, accidental copies, near-identical shots.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "This reads through every photo, so it can take a little while for large libraries.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onScan) { Text("Scan for duplicates") }
        }
    }
}

@Composable
private fun ScanningState(scanned: Int, total: Int) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            val progress = if (total > 0) scanned / total.toFloat() else 0f
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Text("$scanned of $total photos scanned", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun EmptyResultState(onRescan: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = ActionKeep, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text("No duplicates found", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onRescan) { Text("Scan again") }
        }
    }
}

@Composable
private fun ResultsState(uiState: DuplicateUiState, onToggle: (Long) -> Unit, onDelete: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        val selectedCount = uiState.selectedForDeletion.size
        val selectedBytes = uiState.groups.flatMap { it.photos }
            .filter { it.id in uiState.selectedForDeletion }
            .sumOf { it.size }

        Text(
            "${uiState.duplicateCount} duplicate photo${if (uiState.duplicateCount == 1) "" else "s"} found in ${uiState.groups.size} group${if (uiState.groups.size == 1) "" else "s"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
        )

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
