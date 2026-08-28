package com.wheelsort.app.ui.duplicates

import android.app.Application
import android.app.PendingIntent
import androidx.lifecycle.AndroidViewModel
import com.wheelsort.app.data.DuplicateScanManager
import com.wheelsort.app.data.DuplicateUiState
import com.wheelsort.app.data.PhotoRepository
import kotlinx.coroutines.flow.StateFlow

class DuplicateViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PhotoRepository(application)

    // Delegates entirely to the application-scoped manager rather than owning scan state here -
    // this ViewModel (and its scope) gets cleared whenever the Duplicates screen is left, but the
    // scan itself needs to keep running regardless, so it can't live here.
    val uiState: StateFlow<DuplicateUiState> = DuplicateScanManager.uiState

    fun startScan(album: String? = null) {
        DuplicateScanManager.startScan(getApplication(), album)
    }

    fun stopScan() {
        DuplicateScanManager.stopScan()
    }

    fun analyzedAlbums(): Set<String> = DuplicateScanManager.analyzedAlbums(getApplication())

    fun toggleSelection(photoId: Long) {
        DuplicateScanManager.toggleSelection(photoId)
    }

    fun buildDeleteIntent(): PendingIntent? {
        val allPhotos = uiState.value.groups.flatMap { it.photos }
        val uris = allPhotos.filter { it.id in uiState.value.selectedForDeletion }.map { it.uri }
        if (uris.isEmpty()) return null
        return repository.createTrashRequest(uris, trash = true)
    }

    fun onDeleteConfirmed() {
        DuplicateScanManager.removeDeletedFromGroups(uiState.value.selectedForDeletion)
    }
}
