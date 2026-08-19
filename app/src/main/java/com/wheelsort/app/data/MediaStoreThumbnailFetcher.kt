package com.wheelsort.app.data

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.util.Size as AndroidSize
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import coil.size.Dimension
import coil.size.Size as CoilSize

/**
 * MediaStore already keeps a fast on-disk thumbnail cache for every image (used by the system
 * Gallery/Photos picker). ContentResolver.loadThumbnail() reads straight from that cache instead
 * of Coil decoding the full-resolution JPEG from scratch on every request - this is what actually
 * removes the lag when spinning quickly through the wheel, rather than just bounding the decode
 * size of a full decode.
 */
class MediaStoreThumbnailFetcher(
    private val context: Context,
    private val uri: Uri,
    private val size: CoilSize
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val target = resolveTargetSize()
        val bitmap = try {
            context.contentResolver.loadThumbnail(uri, target, null)
        } catch (_: Exception) {
            // Some OEM MediaProvider implementations or unusual files can fail loadThumbnail -
            // fall back to a manually downsampled decode instead of just failing the load.
            decodeDownsampled(target) ?: throw IllegalStateException("Could not load $uri")
        }
        return DrawableResult(
            drawable = BitmapDrawable(context.resources, bitmap),
            isSampled = true,
            dataSource = DataSource.DISK
        )
    }

    private fun decodeDownsampled(target: AndroidSize): android.graphics.Bitmap? {
        return context.contentResolver.openInputStream(uri)?.use { input ->
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(input, null, bounds)
            val sample = calculateSampleSize(bounds.outWidth, bounds.outHeight, target.width, target.height)
            context.contentResolver.openInputStream(uri)?.use { second ->
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeStream(second, null, opts)
            }
        }
    }

    private fun calculateSampleSize(rawW: Int, rawH: Int, targetW: Int, targetH: Int): Int {
        var sample = 1
        var w = rawW
        var h = rawH
        while (w / 2 >= targetW && h / 2 >= targetH) {
            sample *= 2
            w /= 2
            h /= 2
        }
        return sample
    }

    private fun resolveTargetSize(): AndroidSize {
        val w = (size.width as? Dimension.Pixels)?.px ?: DEFAULT_PX
        val h = (size.height as? Dimension.Pixels)?.px ?: DEFAULT_PX
        return AndroidSize(w.coerceAtLeast(64), h.coerceAtLeast(64))
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.scheme != "content" || data.authority != "media") return null
            return MediaStoreThumbnailFetcher(options.context, data, options.size)
        }
    }

    private companion object {
        const val DEFAULT_PX = 720
    }
}
