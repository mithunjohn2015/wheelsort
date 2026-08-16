package com.wheelsort.app.data

import android.net.Uri

/**
 * Lightweight representation of a MediaStore image row.
 * We never copy image bytes ourselves - everything is referenced by [uri]
 * and all destructive actions go through MediaStore's scoped-storage APIs.
 */
data class Photo(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val dateAdded: Long,
    val size: Long,
    val bucketName: String?,
    val width: Int,
    val height: Int,
    val isFavorite: Boolean = false
)
