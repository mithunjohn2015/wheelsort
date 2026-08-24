package com.wheelsort.app.data

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore

/**
 * All photo/video reads/writes go through MediaStore, respecting scoped storage.
 *
 * Deleting an item does NOT touch the filesystem directly. Instead we ask the
 * system to "trash" it via [createTrashRequest] - this is the real Android
 * equivalent of moving it to a Trash folder: the OS marks it IS_TRASHED,
 * hides it from the gallery, and keeps it recoverable until the user (or the
 * system, after ~30 days) permanently removes it. Restoring calls the same
 * API with trash = false. Permanent deletion uses [createDeleteRequest].
 *
 * Moving an item into a dated folder works the same way: we don't touch files
 * directly, we ask for write access via [createWriteRequest], then update the
 * MediaStore row's RELATIVE_PATH via [moveToFolder] - the system physically
 * relocates the file on disk to match.
 *
 * Images and videos live in separate MediaStore collections but are merged into
 * one [Photo] list here, since every operation above works identically on both -
 * it only needs the item's Uri, which already encodes which collection it's from.
 */
class PhotoRepository(private val context: Context) {

    private val imagesCollection: Uri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    private val videoCollection: Uri = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

    private val imageProjection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.DATE_ADDED,
        MediaStore.Images.Media.DATE_TAKEN,
        MediaStore.Images.Media.SIZE,
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        MediaStore.Images.Media.WIDTH,
        MediaStore.Images.Media.HEIGHT,
        MediaStore.Images.Media.IS_FAVORITE
    )

    private val videoProjection = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.DATE_ADDED,
        MediaStore.Video.Media.DATE_TAKEN,
        MediaStore.Video.Media.SIZE,
        MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
        MediaStore.Video.Media.WIDTH,
        MediaStore.Video.Media.HEIGHT,
        MediaStore.Video.Media.IS_FAVORITE,
        MediaStore.Video.Media.DURATION
    )

    fun queryActivePhotos(bucketName: String? = null, newestFirst: Boolean = true): List<Photo> =
        query(trashedOnly = false, bucketName = bucketName, newestFirst = newestFirst)

    fun queryTrashedPhotos(): List<Photo> =
        query(trashedOnly = true, bucketName = null, newestFirst = true)

    fun distinctAlbums(): List<String> =
        queryActivePhotos().mapNotNull { it.bucketName }.filter { it.isNotBlank() }.distinct().sorted()

    private fun query(trashedOnly: Boolean, bucketName: String?, newestFirst: Boolean): List<Photo> {
        val images = queryCollection(imagesCollection, imageProjection, isVideo = false, trashedOnly, bucketName)
        val videos = queryCollection(videoCollection, videoProjection, isVideo = true, trashedOnly, bucketName)
        val merged = images + videos
        return if (newestFirst) merged.sortedByDescending { it.dateAdded } else merged.sortedBy { it.dateAdded }
    }

    private fun queryCollection(
        collection: Uri,
        projection: Array<String>,
        isVideo: Boolean,
        trashedOnly: Boolean,
        bucketName: String?
    ): List<Photo> {
        val items = mutableListOf<Photo>()
        // MediaColumns constants (RELATIVE_PATH, and the ones below) are shared across Images/Video,
        // so it's safe to always reference them via the base MediaColumns interface for clarity.
        val idCol = MediaStore.MediaColumns._ID
        val nameCol = MediaStore.MediaColumns.DISPLAY_NAME
        val dateAddedCol = MediaStore.MediaColumns.DATE_ADDED
        val dateTakenCol = MediaStore.MediaColumns.DATE_TAKEN
        val sizeCol = MediaStore.MediaColumns.SIZE
        val bucketCol = MediaStore.MediaColumns.BUCKET_DISPLAY_NAME
        val widthCol = MediaStore.MediaColumns.WIDTH
        val heightCol = MediaStore.MediaColumns.HEIGHT
        val favCol = MediaStore.MediaColumns.IS_FAVORITE

        val queryArgs = Bundle().apply {
            // Sort order here doesn't matter for correctness - images and videos are merged and
            // re-sorted by the actual requested direction afterward in query().
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "$dateAddedCol DESC")
            if (bucketName != null) {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, "$bucketCol = ?")
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf(bucketName))
            }
            putInt(
                MediaStore.QUERY_ARG_MATCH_TRASHED,
                if (trashedOnly) MediaStore.MATCH_ONLY else MediaStore.MATCH_EXCLUDE
            )
        }

        try {
            context.contentResolver.query(collection, projection, queryArgs, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(idCol)
                val nameIdx = cursor.getColumnIndexOrThrow(nameCol)
                val dateIdx = cursor.getColumnIndexOrThrow(dateAddedCol)
                val dateTakenIdx = cursor.getColumnIndexOrThrow(dateTakenCol)
                val sizeIdx = cursor.getColumnIndexOrThrow(sizeCol)
                val bucketIdx = cursor.getColumnIndexOrThrow(bucketCol)
                val widthIdx = cursor.getColumnIndexOrThrow(widthCol)
                val heightIdx = cursor.getColumnIndexOrThrow(heightCol)
                val favIdx = cursor.getColumnIndexOrThrow(favCol)
                val durationIdx = if (isVideo) cursor.getColumnIndex(MediaStore.Video.Media.DURATION) else -1

                while (cursor.moveToNext()) {
                    val rawId = cursor.getLong(idIdx)
                    val uri = ContentUris.withAppendedId(collection, rawId)
                    items.add(
                        Photo(
                            // Images and videos each auto-increment their own _ID independently,
                            // so raw IDs can collide across collections - negate video IDs to keep
                            // every Photo.id unique for selection sets / LazyColumn keys / etc.
                            // The real Uri (built from rawId above) is unaffected and always correct.
                            id = if (isVideo) -rawId else rawId,
                            uri = uri,
                            displayName = cursor.getString(nameIdx) ?: "",
                            dateAdded = cursor.getLong(dateIdx) * 1000L,
                            dateTaken = cursor.getLong(dateTakenIdx),
                            size = cursor.getLong(sizeIdx),
                            bucketName = cursor.getString(bucketIdx),
                            width = cursor.getInt(widthIdx),
                            height = cursor.getInt(heightIdx),
                            isFavorite = cursor.getInt(favIdx) == 1,
                            isVideo = isVideo,
                            durationMs = if (durationIdx >= 0) cursor.getLong(durationIdx) else 0
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // If video querying fails on some device/config, fail soft - images still work fine.
        }
        return items
    }

    /** trash = true moves to Trash, trash = false restores. */
    fun createTrashRequest(uris: List<Uri>, trash: Boolean): PendingIntent =
        MediaStore.createTrashRequest(context.contentResolver, uris, trash)

    fun createDeleteRequest(uris: List<Uri>): PendingIntent =
        MediaStore.createDeleteRequest(context.contentResolver, uris)

    fun createFavoriteRequest(uris: List<Uri>, favorite: Boolean): PendingIntent =
        MediaStore.createFavoriteRequest(context.contentResolver, uris, favorite)

    /** One-time consent to modify media this app doesn't own - required before [moveToFolder] will succeed. */
    fun createWriteRequest(uris: List<Uri>): PendingIntent =
        MediaStore.createWriteRequest(context.contentResolver, uris)

    /**
     * Physically relocates an item into Pictures/[folderName]/ (or Movies/[folderName]/ for
     * videos) by updating its RELATIVE_PATH. Must be called after the corresponding
     * [createWriteRequest] has been granted. Returns true on success.
     */
    fun moveToFolder(photo: Photo, folderName: String): Boolean {
        return try {
            val basePath = if (photo.isVideo) "Movies" else "Pictures"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$basePath/$folderName/")
            }
            context.contentResolver.update(photo.uri, values, null, null) > 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Asks the OS to generate/cache a thumbnail for this item, discarding the result. The first
     * time any app requests a given item's thumbnail, Android has to decode and downsample the
     * original file, which is the actual slow part - once that's done, every future request
     * (from us or anyone else) for that item is fast, regardless of Coil's own memory cache
     * state. Calling this ahead of time across the whole list is what makes scrolling to a
     * photo you haven't visited yet feel instant instead of only the first handful.
     */
    fun warmThumbnail(photo: Photo, sizePx: Int) {
        try {
            context.contentResolver.loadThumbnail(photo.uri, android.util.Size(sizePx, sizePx), null)
        } catch (_: Exception) {
            // best-effort - a failure here just means this one item decodes normally when viewed
        }
    }
}
