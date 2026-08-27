package com.wheelsort.app.ui.sort

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wheelsort.app.data.ExifUtils
import com.wheelsort.app.data.Photo
import com.wheelsort.app.data.PhotoDetails
import com.wheelsort.app.util.formatBytes
import com.wheelsort.app.util.formatDuration
import kotlinx.coroutines.launch
import java.util.Date

private const val DETAILS_PANEL_HEIGHT_DP = 320

@Composable
fun PhotoViewerOverlay(
    photo: Photo,
    onClose: () -> Unit,
    onToggleFavorite: (Photo) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val panelHeightPx = with(density) { DETAILS_PANEL_HEIGHT_DP.dp.toPx() }

    // progress in [-0.3f, 1f]: 0 = photo only, 1 = details fully shown, negative = dragging to dismiss
    val progress = remember { Animatable(0f) }
    var details by remember(photo.id) { mutableStateOf<PhotoDetails?>(null) }
    var isFavorite by remember(photo.id) { mutableStateOf(photo.isFavorite) }

    LaunchedEffect(photo.id) {
        details = if (photo.isVideo) PhotoDetails() else ExifUtils.read(context, photo.uri)
    }

    BackHandler {
        if (progress.value > 0.05f) scope.launch { progress.animateTo(0f, spring(Spring.DampingRatioNoBouncy)) }
        else onClose()
    }

    val dismissAmount = (-progress.value).coerceIn(0f, 0.3f) / 0.3f
    val scrimAlpha = 1f - dismissAmount * 0.55f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrimAlpha))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                when {
                                    progress.value < -0.22f -> onClose()
                                    progress.value > 0.35f -> progress.animateTo(1f, spring(Spring.DampingRatioNoBouncy))
                                    else -> progress.animateTo(0f, spring(Spring.DampingRatioNoBouncy))
                                }
                            }
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                progress.snapTo((progress.value - dragAmount / panelHeightPx).coerceIn(-0.3f, 1f))
                            }
                        }
                    )
                }
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(photo.uri).crossfade(150).build(),
                contentDescription = photo.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val shrink = 1f - dismissAmount * 0.18f
                        scaleX = shrink
                        scaleY = shrink
                        translationY = (-progress.value).coerceAtLeast(0f) * panelHeightPx * 0.5f
                    }
            )

            if (photo.isVideo) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable {
                                val playIntent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(photo.uri, "video/*")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                try {
                                    context.startActivity(playIntent)
                                } catch (_: ActivityNotFoundException) {
                                    // no video player installed - nothing sensible to fall back to
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }

            // top bar: close + share + edit-with + favorite
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, start = 8.dp, end = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White)
                }
                Row {
                    IconButton(onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/*"
                            putExtra(Intent.EXTRA_STREAM, photo.uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, null))
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share", tint = Color.White)
                    }
                    IconButton(onClick = {
                        val editIntent = Intent(Intent.ACTION_EDIT).apply {
                            setDataAndType(photo.uri, "image/*")
                            addFlags(
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            )
                        }
                        // Checking for candidates first, rather than only relying on the exception
                        // from startActivity, distinguishes "no editor app actually supports this"
                        // from "something else went wrong" - two attempted fixes without
                        // confirmation means the next step is seeing what's actually happening
                        // rather than guessing again.
                        val candidates = context.packageManager.queryIntentActivities(editIntent, 0)
                        if (candidates.isEmpty()) {
                            android.widget.Toast.makeText(
                                context,
                                "No installed app supports editing images",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            try {
                                context.startActivity(Intent.createChooser(editIntent, "Edit with"))
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(
                                    context,
                                    "Couldn't open editor: ${e.javaClass.simpleName}",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit with\u2026", tint = Color.White)
                    }
                    IconButton(onClick = {
                        isFavorite = !isFavorite
                        onToggleFavorite(photo)
                    }) {
                        Icon(
                            if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = null,
                            tint = if (isFavorite) Color(0xFFFF5E5E) else Color.White
                        )
                    }
                }
            }

            if (progress.value < 0.08f) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null, tint = Color.White.copy(alpha = 0.8f))
                    Text("Swipe up for details", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium)
                }
            }

            // details panel
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(DETAILS_PANEL_HEIGHT_DP.dp)
                    .graphicsLayer {
                        translationY = (1f - progress.value.coerceIn(0f, 1f)) * panelHeightPx
                    }
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                DetailsPanel(photo, details, context)
            }
        }
    }
}

@Composable
private fun DetailsPanel(photo: Photo, details: PhotoDetails?, context: android.content.Context) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        item {
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                    )
                }
                Text(photo.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
            }
        }
        item { DetailRow("Taken", details?.dateTaken ?: DateFormat.format("MMM d, yyyy", Date(photo.dateAdded)).toString()) }
        item { DetailRow("Resolution", "${photo.width} \u00d7 ${photo.height}") }
        if (photo.isVideo) {
            item { DetailRow("Duration", formatDuration(photo.durationMs)) }
        }
        item { DetailRow("File size", formatBytes(photo.size)) }
        details?.cameraModel?.let { item { DetailRow("Camera", it) } }
        details?.exposureTime?.let { item { DetailRow("Exposure", it) } }
        details?.fNumber?.let { item { DetailRow("Aperture", it) } }
        details?.iso?.let { item { DetailRow("ISO", it) } }
        details?.latLong?.let { latLong ->
            item {
                Column {
                    DetailRow("Location", "%.5f, %.5f".format(latLong[0], latLong[1]))
                    TextButton(onClick = {
                        val uri = Uri.parse("geo:${latLong[0]},${latLong[1]}?q=${latLong[0]},${latLong[1]}")
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    }) {
                        Icon(Icons.Filled.Place, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Open in Maps")
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
