package com.wheelsort.app.data

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore

/**
 * All photo reads/writes go through MediaStore, respecting scoped storage.
 *
 * Deleting a photo does NOT touch the filesystem directly. Instead we ask the
 * system to "trash" it via [createTrashRequest] - this is the real Android
 * equivalent of moving a photo to a Trash folder: the OS marks it IS_TRASHED,
 * hides it from the gallery, and keeps it recoverable until the user (or the
 * system, after ~30 days) permanently removes it. Restoring calls the same
 * API with trash = false. Permanent deletion uses [createDeleteRequest].
 */
class PhotoRepository(private val context: Context) {

    private val collection: Uri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

    private val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.DATE_ADDED,
        MediaStore.Images.Media.SIZE,
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        MediaStore.Images.Media.WIDTH,
        MediaStore.Images.Media.HEIGHT,
        MediaStore.Images.Media.IS_FAVORITE
    )

    fun queryActivePhotos(bucketName: String? = null, newestFirst: Boolean = true): List<Photo> =
        query(trashedOnly = false, bucketName = bucketName, newestFirst = newestFirst)

    fun queryTrashedPhotos(): List<Photo> =
        query(trashedOnly = true, bucketName = null, newestFirst = true)

    fun distinctAlbums(): List<String> =
        queryActivePhotos().mapNotNull { it.bucketName }.filter { it.isNotBlank() }.distinct().sorted()

    private fun query(trashedOnly: Boolean, bucketName: String?, newestFirst: Boolean): List<Photo> {
        val photos = mutableListOf<Photo>()

        val queryArgs = Bundle().apply {
            putString(
                ContentResolver.QUERY_ARG_SQL_SORT_ORDER,
                "${MediaStore.Images.Media.DATE_ADDED} ${if (newestFirst) "DESC" else "ASC"}"
            )
            if (bucketName != null) {
                putString(
                    ContentResolver.QUERY_ARG_SQL_SELECTION,
                    "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} = ?"
                )
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf(bucketName))
            }
            putInt(
                MediaStore.QUERY_ARG_MATCH_TRASHED,
                if (trashedOnly) MediaStore.MATCH_ONLY else MediaStore.MATCH_EXCLUDE
            )
        }

        context.contentResolver.query(collection, projection, queryArgs, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val favCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.IS_FAVORITE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(collection, id)
                photos.add(
                    Photo(
                        id = id,
                        uri = uri,
                        displayName = cursor.getString(nameCol) ?: "",
                        dateAdded = cursor.getLong(dateCol) * 1000L,
                        size = cursor.getLong(sizeCol),
                        bucketName = cursor.getString(bucketCol),
                        width = cursor.getInt(widthCol),
                        height = cursor.getInt(heightCol),
                        isFavorite = cursor.getInt(favCol) == 1
                    )
                )
            }
        }
        return photos
    }

    /** trash = true moves to Trash, trash = false restores. */
    fun createTrashRequest(uris: List<Uri>, trash: Boolean): PendingIntent =
        MediaStore.createTrashRequest(context.contentResolver, uris, trash)

    fun createDeleteRequest(uris: List<Uri>): PendingIntent =
        MediaStore.createDeleteRequest(context.contentResolver, uris)

    fun createFavoriteRequest(uris: List<Uri>, favorite: Boolean): PendingIntent =
        MediaStore.createFavoriteRequest(context.contentResolver, uris, favorite)
}
