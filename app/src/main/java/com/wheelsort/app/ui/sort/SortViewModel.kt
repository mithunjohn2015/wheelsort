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

data class HistoryEntry(val photo: Photo, val action: SwipeAction)

data class SortUiState(
    val photos: List<Photo> = emptyList(),
    val currentIndex: Int = 0,
    val reviewedCount: Int = 0,
    val keptCount: Int = 0,
    val deletedCount: Int = 0,
    val spaceFreed: Long = 0,
    val isLoading: Boolean = true,
    val sessionComplete: Boolean = false,
    val lastToast: HistoryEntry? = null
)

class SortViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PhotoRepository(application)
    private val _uiState = MutableStateFlow(SortUiState())
    val uiState: StateFlow<SortUiState> = _uiState.asStateFlow()

    private val history = ArrayDeque<HistoryEntry>()

    fun loadPhotos(albumFilter: String?) {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch(Dispatchers.IO) {
            val photos = repository.queryActivePhotos(albumFilter)
            _uiState.value = SortUiState(
                photos = photos,
                isLoading = false,
                sessionComplete = photos.isEmpty()
            )
        }
    }

    fun currentPhoto(): Photo? = _uiState.value.photos.getOrNull(_uiState.value.currentIndex)
    fun peekNextPhoto(): Photo? = _uiState.value.photos.getOrNull(_uiState.value.currentIndex + 1)
    fun peekPreviousPhoto(): Photo? = _uiState.value.photos.getOrNull(_uiState.value.currentIndex - 1)

    /** Builds the system trash confirmation intent for the given photo. Caller launches it. */
    fun buildTrashIntent(photo: Photo): PendingIntent =
        repository.createTrashRequest(listOf(photo.uri), trash = true)

    /** Call once the system trash request succeeds (activity result RESULT_OK). */
    fun onDeleteConfirmed(photo: Photo) {
        history.addLast(HistoryEntry(photo, SwipeAction.DELETE))
        val s = _uiState.value
        val nextIndex = s.currentIndex + 1
        _uiState.value = s.copy(
            currentIndex = nextIndex,
            reviewedCount = s.reviewedCount + 1,
            deletedCount = s.deletedCount + 1,
            spaceFreed = s.spaceFreed + photo.size,
            sessionComplete = nextIndex >= s.photos.size,
            lastToast = HistoryEntry(photo, SwipeAction.DELETE)
        )
    }

    fun onKeep(photo: Photo) {
        history.addLast(HistoryEntry(photo, SwipeAction.KEEP))
        val s = _uiState.value
        val nextIndex = s.currentIndex + 1
        _uiState.value = s.copy(
            currentIndex = nextIndex,
            reviewedCount = s.reviewedCount + 1,
            keptCount = s.keptCount + 1,
            sessionComplete = nextIndex >= s.photos.size,
            lastToast = HistoryEntry(photo, SwipeAction.KEEP)
        )
    }

    fun goToNext() {
        val s = _uiState.value
        if (s.currentIndex < s.photos.size - 1) _uiState.value = s.copy(currentIndex = s.currentIndex + 1)
    }

    fun goToPrevious() {
        val s = _uiState.value
        if (s.currentIndex > 0) _uiState.value = s.copy(currentIndex = s.currentIndex - 1)
    }

    /**
     * Undo the last keep/delete decision. If it was a delete, returns the photo so the
     * caller can fire a restore trash-intent; otherwise just rewinds the counters.
     */
    fun undoLast(): HistoryEntry? {
        val last = history.removeLastOrNull() ?: return null
        val s = _uiState.value
        _uiState.value = s.copy(
            currentIndex = (s.currentIndex - 1).coerceAtLeast(0),
            reviewedCount = (s.reviewedCount - 1).coerceAtLeast(0),
            keptCount = if (last.action == SwipeAction.KEEP) (s.keptCount - 1).coerceAtLeast(0) else s.keptCount,
            deletedCount = if (last.action == SwipeAction.DELETE) (s.deletedCount - 1).coerceAtLeast(0) else s.deletedCount,
            spaceFreed = if (last.action == SwipeAction.DELETE) s.spaceFreed - last.photo.size else s.spaceFreed,
            sessionComplete = false,
            lastToast = null
        )
        return last
    }

    fun buildRestoreIntent(photo: Photo): PendingIntent =
        repository.createTrashRequest(listOf(photo.uri), trash = false)
}
