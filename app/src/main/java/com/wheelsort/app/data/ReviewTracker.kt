package com.wheelsort.app.data

import android.content.Context

/**
 * Tracks which photos have actually been decided on (kept or deleted via a swipe) - separate
 * from merely having scrolled past them in the wheel. Flicking through the wheel without acting
 * on a photo should never count as "sorted"; only an explicit keep/delete does.
 */
class ReviewTracker(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("review_tracker", Context.MODE_PRIVATE)

    fun markReviewed(photoId: Long) {
        val current = prefs.getStringSet(KEY_REVIEWED, emptySet()) ?: emptySet()
        val idStr = photoId.toString()
        if (idStr in current) return
        // getStringSet's own docs warn against mutating the returned instance directly - always
        // work on a fresh copy before writing back.
        val updated = HashSet(current)
        updated.add(idStr)
        prefs.edit().putStringSet(KEY_REVIEWED, updated).apply()
    }

    /** All reviewed photo ids, loaded once - callers doing per-folder counts should call this
     *  once and reuse the resulting set, rather than querying per-photo repeatedly. */
    fun reviewedIds(): Set<Long> =
        (prefs.getStringSet(KEY_REVIEWED, emptySet()) ?: emptySet())
            .mapNotNull { it.toLongOrNull() }
            .toSet()

    private companion object {
        const val KEY_REVIEWED = "reviewed_ids"
    }
}
