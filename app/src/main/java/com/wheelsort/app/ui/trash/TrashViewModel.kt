package com.wheelsort.app.ui.trash

import android.app.Application
import android.app.PendingIntent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wheelsort.app.data.Photo
import com.wheelsort.app.data.PhotoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TrashUiState(
    val photos: List<Photo> = emptyList(),
    val selected: Set<Long> = emptySet(),
    val isLoading: Boolean = true
)

class TrashViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PhotoRepository(application)
    private val _uiState = MutableStateFlow(TrashUiState())
    val uiState: StateFlow<TrashUiState> = _uiState.asStateFlow()

    fun refresh() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch(Dispatchers.IO) {
            val photos = repository.queryTrashedPhotos()
            _uiState.value = TrashUiState(photos = photos, isLoading = false)
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

    fun selectedUris(): List<Uri> =
        _uiState.value.photos.filter { it.id in _uiState.value.selected }.map { it.uri }

    fun buildRestoreIntent(): PendingIntent =
        repository.createTrashRequest(selectedUris(), trash = false)

    fun buildPermanentDeleteIntent(): PendingIntent =
        repository.createDeleteRequest(selectedUris())
}
