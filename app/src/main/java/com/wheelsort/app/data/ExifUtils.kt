package com.wheelsort.app.data

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PhotoDetails(
    val dateTaken: String? = null,
    val cameraModel: String? = null,
    val exposureTime: String? = null,
    val fNumber: String? = null,
    val iso: String? = null,
    val latLong: DoubleArray? = null
)

object ExifUtils {

    suspend fun read(context: Context, uri: Uri): PhotoDetails = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                PhotoDetails(
                    dateTaken = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                        ?: exif.getAttribute(ExifInterface.TAG_DATETIME),
                    cameraModel = listOfNotNull(
                        exif.getAttribute(ExifInterface.TAG_MAKE),
                        exif.getAttribute(ExifInterface.TAG_MODEL)
                    ).joinToString(" ").ifBlank { null },
                    exposureTime = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.let { "$it s" },
                    fNumber = exif.getAttribute(ExifInterface.TAG_F_NUMBER)?.let { "f/$it" },
                    iso = exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)?.let { "ISO $it" },
                    latLong = exif.latLong
                )
            } ?: PhotoDetails()
        } catch (_: Exception) {
            PhotoDetails()
        }
    }
}
