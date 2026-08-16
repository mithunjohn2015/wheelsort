package com.wheelsort.app.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wheelsort.app.R
import com.wheelsort.app.data.PhotoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    onStartSorting: (albumFilter: String?) -> Unit,
    onOpenTrash: () -> Unit,
    onOpenStats: () -> Unit
) {
    val context = LocalContext.current
    var albums by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedAlbum by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        albums = withContext(Dispatchers.IO) { PhotoRepository(context).distinctAlbums() }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
        ) {
            Text(
                stringResource(R.string.home_title),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.home_choose_album), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                item {
                    AlbumRow(
                        name = stringResource(R.string.home_all_photos),
                        selected = selectedAlbum == null,
                        onClick = { selectedAlbum = null }
                    )
                }
                items(albums) { album ->
                    AlbumRow(
                        name = album,
                        selected = selectedAlbum == album,
                        onClick = { selectedAlbum = album }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onOpenTrash, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.home_trash))
                }
                OutlinedButton(onClick = onOpenStats, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.BarChart, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.home_stats))
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onStartSorting(selectedAlbum) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_start), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun AlbumRow(name: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Spacer(Modifier.width(8.dp))
            Text(name, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
