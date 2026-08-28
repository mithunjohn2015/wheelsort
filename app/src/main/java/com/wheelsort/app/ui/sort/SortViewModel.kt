package com.wheelsort.app.ui.sort

import android.app.Application
import android.app.PendingIntent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wheelsort.app.data.Photo
import com.wheelsort.app.data.PhotoRepository
import com.wheelsort.app.data.ReviewTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import kotlin.math.abs

enum class SwipeAction { KEEP, DELETE }

/** Which media types to include - lets the wheel be scoped to just photos or just videos. */
enum class MediaTypeFilter { ALL, PHOTOS_ONLY, VIDEOS_ONLY }

/** Sort order and content filters for a review session - now owned by the wheel screen itself
 *  rather than chosen once on Home before starting, so they can be changed mid-session. */
data class SortFilters(
    val newestFirst: Boolean = true,
    val mediaType: MediaTypeFilter = MediaTypeFilter.ALL,
    val favoritesOnly: Boolean = false
)

/**
 * [flushed] = true once this delete has actually been sent to (and accepted by) MediaStore's trash.
 * [removedAtIndex] = list position the photo was removed from, so undo can splice it back exactly
 * where it was. Only meaningful for DELETE entries, and only valid for the most recent one (undo
 * only ever pops the top of the stack, so earlier indices are never invalidated by later actions).
 */
data class HistoryEntry(
    val photo: Photo,
    val action: SwipeAction,
    val flushed: Boolean = true,
    val removedAtIndex: Int = -1
)

data class SortUiState(
    val photos: List<Photo> = emptyList(),
    val currentIndex: Int = 0,
    val reviewedCount: Int = 0,
    val keptCount: Int = 0,
    val deletedCount: Int = 0,
    val spaceFreed: Long = 0,
    val pendingDeleteCount: Int = 0,
    val isLoading: Boolean = true,
    val sessionComplete: Boolean = false,
    val filters: SortFilters = SortFilters()
)

/** Request size used for warming the cache - must exactly match PHOTO_REQUEST_PX in SortScreen.kt,
 *  or the warm pass caches a differently-sized bitmap than the wheel actually requests, which is
 *  a cache miss wearing a disguise. */
private const val WARM_SIZE_PX = 1600

/** How many photos ahead to eagerly warm - covers a very long browsing session for most libraries. */
private const val WARM_CAP = 900

/** Small pause between warms so foreground image loads always win contention for disk/CPU. */
private const val WARM_PAUSE_MS = 12L

/** How far you can drift from the current warm-up focus before it re-centers on your actual position. */
private const val REANCHOR_DISTANCE = 40

class SortViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PhotoRepository(application)
    private val reviewTracker = ReviewTracker(application)
    private val _uiState = MutableStateFlow(SortUiState())
    val uiState: StateFlow<SortUiState> = _uiState.asStateFlow()

    private val history = ArrayDeque<HistoryEntry>()
    private val pendingQueue = ArrayDeque<Photo>()
    private var lastFlushBatch: List<Photo> = emptyList()
    private var warmAnchor = 0
    private var currentAlbumFilter: String? = null

    /**
     * One-shot events for PROGRAMMATIC scroll requests (undo restoring a photo, etc.) - the wheel
     * itself now owns scroll position via its own list state, driven by the user's own gestures;
     * this is only for the cases where the ViewModel needs to move it without a gesture involved.
     */
    private val _scrollToIndex = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val scrollToIndex: SharedFlow<Int> = _scrollToIndex.asSharedFlow()

    /** Dedicated single thread for cache warming so it can never starve foreground image loads. */
    private val warmDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "thumb-warm").apply { priority = Thread.MIN_PRIORITY }
    }.asCoroutineDispatcher()
    private var warmJob: Job? = null

    override fun onCleared() {
        super.onCleared()
        warmJob?.cancel()
        warmDispatcher.close()
    }

    fun loadPhotos(albumFilter: String?, filters: SortFilters = SortFilters()) {
        currentAlbumFilter = albumFilter
        _uiState.value = _uiState.value.copy(isLoading = true, filters = filters)
        warmJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            var photos = repository.queryActivePhotos(albumFilter, filters.newestFirst)
            photos = when (filters.mediaType) {
                MediaTypeFilter.ALL -> photos
                MediaTypeFilter.PHOTOS_ONLY -> photos.filterNot { it.isVideo }
                MediaTypeFilter.VIDEOS_ONLY -> photos.filter { it.isVideo }
            }
            if (filters.favoritesOnly) {
                photos = photos.filter { it.isFavorite }
            }
            _uiState.value = SortUiState(
                photos = photos,
                isLoading = false,
                sessionComplete = photos.isEmpty(),
                filters = filters
            )
            warmAnchor = 0
            startWarming(photos, anchor = 0)
        }
    }

    /** Changes sort/filter settings mid-session and reloads - the wheel screen now owns this
     *  directly rather than it being a one-time choice made on Home before starting. */
    fun updateFilters(transform: (SortFilters) -> SortFilters) {
        val newFilters = transform(_uiState.value.filters)
        loadPhotos(currentAlbumFilter, newFilters)
    }

    /**
     * Runs quietly in the background after the wheel is already showing photos. The expensive part
     * of loading a photo is Android generating its thumbnail the first time anyone asks for it -
     * once that's done it's cheap forever.
     *
     * Deliberately runs on its own SINGLE-threaded dispatcher rather than Dispatchers.IO. The
     * previous version queued hundreds of jobs onto the shared IO pool at once, which starved the
     * very thing the user is waiting on - Coil trying to load the photo currently on screen - and
     * produced exactly the recurring ~0.7s stalls seen while scrolling. One dedicated thread plus
     * a small yield between items means warming can never outcompete a foreground load.
     */
    private fun startWarming(photos: List<Photo>, anchor: Int) {
        warmJob?.cancel()
        warmJob = viewModelScope.launch(warmDispatcher) {
            val cap = photos.size.coerceAtMost(WARM_CAP)
            val safeAnchor = anchor.coerceIn(0, (photos.size - 1).coerceAtLeast(0))
            val order = buildList {
                for (d in 0 until cap) {
                    val forward = safeAnchor + d
                    if (forward < photos.size) add(forward)
                    val backward = safeAnchor - d - 1
                    if (backward >= 0) add(backward)
                    if (size >= cap) break
                }
            }
            for (index in order) {
                currentCoroutineContext().ensureActive()
                repository.warmThumbnail(photos[index], WARM_SIZE_PX)
                // Give any foreground image load a chance to grab the thread first.
                delay(WARM_PAUSE_MS)
            }
        }
    }

    /**
     * Swipe left: instant, local-only, and the photo is actually removed from the browsing
     * list right away - scrolling back up will never show it again or let you re-queue it.
     */
    fun queueDelete(photo: Photo) {
        val s = _uiState.value
        val idx = s.photos.indexOfFirst { it.id == photo.id }
        if (idx == -1) return

        reviewTracker.markReviewed(photo.id)
        val newPhotos = s.photos.toMutableList().also { it.removeAt(idx) }
        history.addLast(HistoryEntry(photo, SwipeAction.DELETE, flushed = false, removedAtIndex = idx))
        pendingQueue.addLast(photo)

        val newIndex = idx.coerceAtMost(newPhotos.size)
        _uiState.value = s.copy(
            photos = newPhotos,
            currentIndex = newIndex,
            reviewedCount = s.reviewedCount + 1,
            deletedCount = s.deletedCount + 1,
            spaceFreed = s.spaceFreed + photo.size,
            pendingDeleteCount = pendingQueue.size,
            sessionComplete = newPhotos.isEmpty() || newIndex >= newPhotos.size
        )
    }

    /** Swipe right: photo stays in the list, we advance the pointer to just past wherever it was. */
    fun onKeep(photo: Photo) {
        val s = _uiState.value
        val idx = s.photos.indexOfFirst { it.id == photo.id }
        if (idx == -1) return

        reviewTracker.markReviewed(photo.id)
        history.addLast(HistoryEntry(photo, SwipeAction.KEEP))
        val nextIndex = (idx + 1).coerceAtMost(s.photos.size)
        _uiState.value = s.copy(
            currentIndex = nextIndex,
            reviewedCount = s.reviewedCount + 1,
            keptCount = s.keptCount + 1,
            sessionComplete = nextIndex >= s.photos.size
        )
    }

    /**
     * Called continuously as the wheel scrolls (the wheel itself now owns the actual scroll
     * position - this just keeps the ViewModel's notion of "where you are" in sync for the
     * progress counter, warming, and swipe-target lookups).
     */
    fun setCurrentIndex(index: Int) {
        val s = _uiState.value
        if (s.photos.isEmpty()) return
        val clamped = index.coerceIn(0, s.photos.size - 1)
        if (clamped != s.currentIndex) {
            _uiState.value = s.copy(currentIndex = clamped)
        }
        maybeReanchorWarming(clamped, s.photos)
    }

    /**
     * Warming starts from wherever you are when the screen opens and works outward from there.
     * On a very large library, fast scrolling can carry you far past that original starting
     * point before it's caught up - so once you've drifted more than [REANCHOR_DISTANCE] photos
     * away from where warming is currently focused, restart it centered on where you actually
     * are now instead of continuing to (slowly) work toward you from the old anchor.
     */
    private fun maybeReanchorWarming(currentIndex: Int, photos: List<Photo>) {
        if (abs(currentIndex - warmAnchor) > REANCHOR_DISTANCE) {
            warmAnchor = currentIndex
            startWarming(photos, anchor = currentIndex)
        }
    }

    /** Undo the last decision. */
    fun undoLast(): HistoryEntry? {
        val last = history.removeLastOrNull() ?: return null
        val s = _uiState.value

        when (last.action) {
            SwipeAction.KEEP -> {
                val restoreIndex = (s.currentIndex - 1).coerceAtLeast(0)
                _uiState.value = s.copy(
                    currentIndex = restoreIndex,
                    reviewedCount = (s.reviewedCount - 1).coerceAtLeast(0),
                    keptCount = (s.keptCount - 1).coerceAtLeast(0),
                    sessionComplete = false
                )
                _scrollToIndex.tryEmit(restoreIndex)
            }
            SwipeAction.DELETE -> {
                // Splice the photo back into the list exactly where it was removed from.
                val restoreIndex = last.removedAtIndex.coerceIn(0, s.photos.size)
                val newPhotos = s.photos.toMutableList().apply { add(restoreIndex, last.photo) }
                _uiState.value = s.copy(
                    photos = newPhotos,
                    currentIndex = restoreIndex,
                    reviewedCount = (s.reviewedCount - 1).coerceAtLeast(0),
                    deletedCount = (s.deletedCount - 1).coerceAtLeast(0),
                    spaceFreed = s.spaceFreed - last.photo.size,
                    sessionComplete = false
                )
                if (!last.flushed) {
                    // Never actually sent to MediaStore - just drop it from the queue, no system call needed.
                    pendingQueue.remove(last.photo)
                    _uiState.value = _uiState.value.copy(pendingDeleteCount = pendingQueue.size)
                }
                _scrollToIndex.tryEmit(restoreIndex)
            }
        }
        return last
    }

    /** Builds the one system confirmation for everything queued so far, or null if nothing to flush. */
    fun buildFlushIntent(): PendingIntent? {
        if (pendingQueue.isEmpty()) return null
        lastFlushBatch = pendingQueue.toList()
        return repository.createTrashRequest(lastFlushBatch.map { it.uri }, trash = true)
    }

    /** Call after the flush confirmation intent returns. */
    fun onFlushResult(success: Boolean) {
        if (success) {
            pendingQueue.removeAll(lastFlushBatch)
            for (i in history.indices) {
                val e = history.elementAt(i)
                if (!e.flushed && e.action == SwipeAction.DELETE && lastFlushBatch.any { it.id == e.photo.id }) {
                    history[i] = e.copy(flushed = true)
                }
            }
        }
        // if cancelled, items simply stay in pendingQueue and are retried on the next flush
        _uiState.value = _uiState.value.copy(pendingDeleteCount = pendingQueue.size)
        lastFlushBatch = emptyList()
    }

    fun buildRestoreIntent(photo: Photo): PendingIntent =
        repository.createTrashRequest(listOf(photo.uri), trash = false)

    fun buildFavoriteIntent(photo: Photo, favorite: Boolean): PendingIntent =
        repository.createFavoriteRequest(listOf(photo.uri), favorite)
}
