package com.wheelsort.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wheelsort.app.R
import com.wheelsort.app.data.PhotoRepository
import com.wheelsort.app.data.ReviewTracker
import com.wheelsort.app.ui.theme.AccentPrimary
import com.wheelsort.app.ui.theme.ActionDelete
import com.wheelsort.app.ui.theme.ActionKeep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [reviewed] only counts photos actually decided on (kept/deleted) - flicking through the wheel
 *  without acting on a photo never counts toward this. */
private data class AlbumSummary(val name: String, val total: Int, val reviewed: Int) {
    val percent: Int get() = if (total == 0) 0 else (reviewed * 100 / total)
    val isFullySorted: Boolean get() = total > 0 && reviewed >= total
}

@Composable
fun HomeScreen(
    onStartSorting: (albumFilter: String?) -> Unit,
    onOpenTrash: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenOrganize: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenGrid: (albumFilter: String?) -> Unit,
    onOpenDuplicates: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    var albums by remember { mutableStateOf<List<AlbumSummary>>(emptyList()) }
    var selectedAlbum by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        albums = withContext(Dispatchers.IO) {
            val reviewedIds = ReviewTracker(context).reviewedIds()
            // One query, grouped locally - avoids querying MediaStore once per album, which
            // would scale badly with a library of any real size.
            PhotoRepository(context).queryActivePhotos()
                .filter { !it.bucketName.isNullOrBlank() }
                .groupBy { it.bucketName!! }
                .map { (name, photos) ->
                    AlbumSummary(name, photos.size, photos.count { it.id in reviewedIds })
                }
                // Fully-sorted albums sink to the bottom - everything still needing attention
                // stays up top where it's actually useful to see first.
                .sortedWith(compareBy({ it.isFullySorted }, { it.name }))
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                AccentPrimary.copy(alpha = 0.16f),
                                Color(0xFFE0704F).copy(alpha = 0.10f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(horizontal = 24.dp)
                    .padding(top = 20.dp, bottom = 20.dp)
            ) {
                Column {
                    Text(
                        stringResource(R.string.home_title),
                        style = MaterialTheme.typography.headlineLarge
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.home_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onOpenSettings, modifier = Modifier.align(Alignment.TopEnd)) {
                    Icon(Icons.Filled.Tune, contentDescription = "Wheel settings")
                }
            }

            Column(modifier = Modifier.weight(1f).padding(horizontal = 24.dp)) {

            Spacer(Modifier.height(28.dp))
            Text(
                stringResource(R.string.home_choose_album).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                item {
                    AlbumRow(
                        name = stringResource(R.string.home_all_photos),
                        selected = selectedAlbum == null,
                        onClick = { selectedAlbum = null }
                    )
                }
                items(albums, key = { it.name }) { album ->
                    AlbumRow(
                        name = album.name,
                        selected = selectedAlbum == album.name,
                        onClick = { selectedAlbum = album.name },
                        total = album.total,
                        reviewed = album.reviewed,
                        percent = album.percent,
                        isFullySorted = album.isFullySorted
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                QuietAction(
                    icon = Icons.Filled.DeleteSweep,
                    label = stringResource(R.string.home_trash),
                    accent = ActionDelete,
                    onClick = onOpenTrash
                )
                QuietAction(
                    icon = Icons.Filled.Insights,
                    label = stringResource(R.string.home_stats),
                    accent = Color(0xFF3FA7D6),
                    onClick = onOpenStats
                )
                QuietAction(
                    icon = Icons.Filled.CalendarMonth,
                    label = stringResource(R.string.home_organize),
                    accent = Color(0xFFE0A72E),
                    onClick = onOpenOrganize
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                QuietAction(
                    icon = Icons.Filled.CloudDone,
                    label = stringResource(R.string.home_backup),
                    accent = Color(0xFF1FAE83),
                    onClick = onOpenBackup
                )
                QuietAction(
                    icon = Icons.Filled.ContentCopy,
                    label = stringResource(R.string.home_duplicates),
                    accent = Color(0xFFD6579B),
                    onClick = onOpenDuplicates
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { onOpenGrid(selectedAlbum) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f).height(56.dp)
                ) {
                    Icon(Icons.Filled.GridView, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.home_grid))
                }
                Button(
                    onClick = { onStartSorting(selectedAlbum) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f).height(56.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.home_start))
                }
            }
            Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun AlbumRow(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    total: Int? = null,
    reviewed: Int = 0,
    percent: Int = 0,
    isFullySorted: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.PhotoLibrary,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (isFullySorted) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "Fully sorted",
                        tint = ActionKeep,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            if (total != null && total > 0) {
                Spacer(Modifier.height(3.dp))
                Text(
                    "$reviewed of $total sorted \u2014 $percent%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = if (isFullySorted) ActionKeep else MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn() + scaleIn(initialScale = 0.6f),
            exit = fadeOut() + scaleOut(targetScale = 0.6f)
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@Composable
private fun QuietAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, accent: Color, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
