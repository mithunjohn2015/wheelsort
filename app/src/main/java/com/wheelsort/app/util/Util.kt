package com.wheelsort.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlin.math.ln
import kotlin.math.pow

/**
 * The runtime permission(s) to request for reading photos AND videos, based on SDK level.
 * Android 13+ split media access into separate permissions per media type - requesting only
 * READ_MEDIA_IMAGES (as this app used to) means video queries silently return nothing, since
 * there's no permission to read them, not because anything is actually broken.
 */
fun requiredMediaPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

/**
 * True if the app can read the media library at all - either full access (every permission
 * above granted) OR partial access (the user picked "Select photos" in the system dialog,
 * which grants READ_MEDIA_VISUAL_USER_SELECTED instead of the full permissions). Treating only
 * full access as "granted" was the bug behind "I selected some photos and nothing happened" -
 * partial access is a legitimate, deliberate choice the user made and the app should work with it.
 */
fun hasMediaAccess(context: Context): Boolean {
    val fullAccess = requiredMediaPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    if (fullAccess) return true
    if (Build.VERSION.SDK_INT >= 34) {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        ) == PackageManager.PERMISSION_GRANTED
    }
    return false
}

/** True if access is currently PARTIAL (user selected specific photos rather than "Allow all"). */
fun hasPartialMediaAccess(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < 34) return false
    val fullAccess = requiredMediaPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    if (fullAccess) return false
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    ) == PackageManager.PERMISSION_GRANTED
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    val exp = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(1, units.size)
    val value = bytes / 1024.0.pow(exp.toDouble())
    return String.format("%.1f %s", value, units[exp - 1])
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
