package com.wheelsort.app.ui.sort

import android.app.Application
import android.app.PendingIntent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wheelsort.app.data.Photo
import com.wheelsort.app.data.PhotoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SwipeAction { KEEP, DELETE }

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
    val sessionComplete: Boolean = false
)

/**
 * How many swipe-left decisions accumulate locally before we ask Android to actually
 * trash them. Swiping left is instant and silent; the one system confirmation dialog
 * only shows up roughly once per [BATCH_SIZE] deletes (or when you leave the screen),
 * instead of once per photo.
 */
private const val BATCH_SIZE = 10

class SortViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PhotoRepository(application)
    private val _uiState = MutableStateFlow(SortUiState())
    val uiState: StateFlow<SortUiState> = _uiState.asStateFlow()

    private val history = ArrayDeque<HistoryEntry>()
    private val pendingQueue = ArrayDeque<Photo>()
    private var lastFlushBatch: List<Photo> = emptyList()

    fun loadPhotos(albumFilter: String?, newestFirst: Boolean = true) {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch(Dispatchers.IO) {
            val photos = repository.queryActivePhotos(albumFilter, newestFirst)
            _uiState.value = SortUiState(
                photos = photos,
                isLoading = false,
                sessionComplete = photos.isEmpty()
            )
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

    /** Swipe right: photo stays in the list, we just move the pointer forward past it. */
    fun onKeep(photo: Photo) {
        history.addLast(HistoryEntry(photo, SwipeAction.KEEP))
        val s = _uiState.value
        val nextIndex = s.currentIndex + 1
        _uiState.value = s.copy(
            currentIndex = nextIndex,
            reviewedCount = s.reviewedCount + 1,
            keptCount = s.keptCount + 1,
            sessionComplete = nextIndex >= s.photos.size
        )
    }

    /** Jump forward/back by several photos at once (used for fast wheel flings). Clamped to bounds. */
    fun goToDelta(steps: Int) {
        val s = _uiState.value
        if (steps == 0 || s.photos.isEmpty()) return
        val target = (s.currentIndex + steps).coerceIn(0, s.photos.size - 1)
        _uiState.value = s.copy(currentIndex = target)
    }

    fun goToNext() = goToDelta(1)
    fun goToPrevious() = goToDelta(-1)

    /** Undo the last decision. */
    fun undoLast(): HistoryEntry? {
        val last = history.removeLastOrNull() ?: return null
        val s = _uiState.value

        when (last.action) {
            SwipeAction.KEEP -> {
                _uiState.value = s.copy(
                    currentIndex = (s.currentIndex - 1).coerceAtLeast(0),
                    reviewedCount = (s.reviewedCount - 1).coerceAtLeast(0),
                    keptCount = (s.keptCount - 1).coerceAtLeast(0),
                    sessionComplete = false
                )
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
            }
        }
        return last
    }

    /** True once enough deletes have queued up that we should flush automatically. */
    fun shouldAutoFlush(): Boolean = pendingQueue.size >= BATCH_SIZE

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
