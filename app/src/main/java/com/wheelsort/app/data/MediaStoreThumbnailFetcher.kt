package com.wheelsort.app.data

import android.content.Context
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
        val bitmap = context.contentResolver.loadThumbnail(uri, target, null)
        return DrawableResult(
            drawable = BitmapDrawable(context.resources, bitmap),
            isSampled = true,
            dataSource = DataSource.DISK
        )
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
