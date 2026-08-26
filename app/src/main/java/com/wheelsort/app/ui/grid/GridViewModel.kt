package com.wheelsort.app.ui.grid

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

data class GridUiState(
    val photos: List<Photo> = emptyList(),
    val selected: Set<Long> = emptySet(),
    val isLoading: Boolean = true
)

class GridViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PhotoRepository(application)
    private val _uiState = MutableStateFlow(GridUiState())
    val uiState: StateFlow<GridUiState> = _uiState.asStateFlow()

    fun refresh(albumFilter: String?) {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch(Dispatchers.IO) {
            val photos = repository.queryActivePhotos(albumFilter)
            _uiState.value = _uiState.value.copy(photos = photos, isLoading = false)
        }
    }

    fun toggleSelect(id: Long) {
        val cur = _uiState.value.selected
        _uiState.value = _uiState.value.copy(selected = if (id in cur) cur - id else cur + id)
    }

    fun selectAll() {
        _uiState.value = _uiState.value.copy(selected = _uiState.value.photos.map { it.id }.toSet())
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selected = emptySet())
    }

    private fun selectedPhotos(): List<Photo> =
        _uiState.value.photos.filter { it.id in _uiState.value.selected }

    fun buildTrashIntent(): PendingIntent? {
        val uris = selectedPhotos().map { it.uri }
        if (uris.isEmpty()) return null
        return repository.createTrashRequest(uris, trash = true)
    }

    /** Call once the trash confirmation succeeds - removes the deleted items from the grid. */
    fun onDeleteConfirmed() {
        val deletedIds = _uiState.value.selected
        val remaining = _uiState.value.photos.filterNot { it.id in deletedIds }
        _uiState.value = _uiState.value.copy(photos = remaining, selected = emptySet())
    }

    fun buildFavoriteIntent(favorite: Boolean): PendingIntent? {
        val uris = selectedPhotos().map { it.uri }
        if (uris.isEmpty()) return null
        return repository.createFavoriteRequest(uris, favorite)
    }

    /** For the full-screen viewer's favorite toggle, which acts on one photo, not the selection. */
    fun buildFavoriteIntentForPhoto(photo: Photo, favorite: Boolean): PendingIntent =
        repository.createFavoriteRequest(listOf(photo.uri), favorite)

    /** Ensures this id IS selected (idempotent) - used while drag-painting a selection, where
     *  re-passing over an already-selected item should never accidentally deselect it. */
    fun ensureSelected(id: Long) {
        val cur = _uiState.value.selected
        if (id !in cur) _uiState.value = _uiState.value.copy(selected = cur + id)
    }
}
