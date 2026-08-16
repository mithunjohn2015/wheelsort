package com.wheelsort.app.ui.stats

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wheelsort.app.R
import com.wheelsort.app.data.PhotoRepository
import com.wheelsort.app.util.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun StatsScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    var activeCount by remember { mutableStateOf(0) }
    var trashedCount by remember { mutableStateOf(0) }
    var trashedBytes by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val repo = PhotoRepository(context)
            val active = repo.queryActivePhotos()
            val trashed = repo.queryTrashedPhotos()
            activeCount = active.size
            trashedCount = trashed.size
            trashedBytes = trashed.sumOf { it.size }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_title)) },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
        ) {
            StatCard(Icons.Filled.PhotoLibrary, stringResource(R.string.stats_reviewed), activeCount.toString())
            Spacer(Modifier.height(12.dp))
            StatCard(Icons.Filled.Delete, stringResource(R.string.stats_deleted), trashedCount.toString())
            Spacer(Modifier.height(12.dp))
            StatCard(Icons.Filled.Storage, stringResource(R.string.stats_space), formatBytes(trashedBytes))
        }
    }
}

@Composable
private fun StatCard(icon: ImageVector, label: String, value: String) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(value, style = MaterialTheme.typography.headlineMedium)
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
