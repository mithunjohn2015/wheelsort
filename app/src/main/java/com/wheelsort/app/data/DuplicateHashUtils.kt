package com.wheelsort.app.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri

/**
 * A difference-hash (dHash): downsample to a tiny grayscale grid and record whether each pixel
 * is brighter than its neighbor. Two images that look alike produce hashes that differ in only
 * a few bits, even if they're not byte-identical (e.g. two shots from the same burst) - which is
 * what makes this useful for finding near-duplicates, not just exact copies.
 */
object DuplicateHashUtils {

    fun computeHash(context: Context, uri: Uri): Long? {
        return try {
            val thumb = context.contentResolver.loadThumbnail(uri, android.util.Size(32, 32), null)
            dHash(thumb)
        } catch (_: Exception) {
            null
        }
    }

    private fun dHash(bitmap: Bitmap): Long {
        val resized = Bitmap.createScaledBitmap(bitmap, 9, 8, true)
        var hash = 0L
        var bit = 0
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val left = gray(resized.getPixel(x, y))
                val right = gray(resized.getPixel(x + 1, y))
                if (left > right) hash = hash or (1L shl bit)
                bit++
            }
        }
        return hash
    }

    private fun gray(pixel: Int): Int {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return (r + g + b) / 3
    }

    fun hammingDistance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)
}

/** Simple disjoint-set for clustering photos whose hashes are close together. */
class UnionFind(n: Int) {
    private val parent = IntArray(n) { it }

    fun find(x: Int): Int {
        var root = x
        while (parent[root] != root) root = parent[root]
        var cur = x
        while (parent[cur] != root) {
            val next = parent[cur]
            parent[cur] = root
            cur = next
        }
        return root
    }

    fun union(a: Int, b: Int) {
        val ra = find(a)
        val rb = find(b)
        if (ra != rb) parent[ra] = rb
    }
}
