package com.wheelsort.app.util

import android.Manifest
import android.os.Build
import kotlin.math.ln
import kotlin.math.pow

/** The correct runtime permission to request for reading photos, based on SDK level. */
fun readImagesPermission(): String = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> Manifest.permission.READ_MEDIA_IMAGES
    else -> Manifest.permission.READ_EXTERNAL_STORAGE
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    val exp = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(1, units.size)
    val value = bytes / 1024.0.pow(exp.toDouble())
    return String.format("%.1f %s", value, units[exp - 1])
}

/** Cheap, metadata-only heuristic - no image decoding needed, so it's safe to run on the whole list. */
fun isLikelyScreenshot(photo: com.wheelsort.app.data.Photo): Boolean {
    val bucket = photo.bucketName?.lowercase() ?: ""
    val name = photo.displayName.lowercase()
    return bucket.contains("screenshot") || name.contains("screenshot")
}

/** mm:ss, or h:mm:ss for anything over an hour. */
fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds)
    else String.format("%d:%02d", minutes, seconds)
}
