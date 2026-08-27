package com.wheelsort.app.data

import android.net.Uri

/**
 * Lightweight representation of a MediaStore row - image OR video. Kept as a single type rather
 * than splitting into separate Photo/Video classes because every downstream operation (trash,
 * restore, favorite, move-to-folder, thumbnail loading) is already collection-agnostic - it just
 * operates on a Uri, and that Uri already encodes which MediaStore collection it came from.
 * We never copy media bytes ourselves - everything is referenced by [uri] and all destructive
 * actions go through MediaStore's scoped-storage APIs.
 */
data class Photo(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val dateAdded: Long,
    val dateTaken: Long,
    /** File modification time (ms) - a third sort fallback for libraries where dateTaken is
     *  missing entirely (common for screenshots, downloads, and received media with no EXIF). */
    val dateModified: Long,
    val size: Long,
    val bucketName: String?,
    val width: Int,
    val height: Int,
    val isFavorite: Boolean = false,
    val isVideo: Boolean = false,
    val durationMs: Long = 0
)
