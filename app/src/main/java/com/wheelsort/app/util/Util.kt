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
