package com.wheelsort.app.ui.duplicates

import android.app.Application
import android.app.PendingIntent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wheelsort.app.data.DuplicateHashUtils
import com.wheelsort.app.data.Photo
import com.wheelsort.app.data.PhotoRepository
import com.wheelsort.app.data.UnionFind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Two hashes differing by this many bits (out of 64) or fewer are treated as the same photo. */
private const val HAMMING_THRESHOLD = 6

data class DuplicateGroup(val photos: List<Photo>, val keepId: Long)

data class DuplicateUiState(
    val isScanning: Boolean = false,
    val scannedCount: Int = 0,
    val totalCount: Int = 0,
    val groups: List<DuplicateGroup> = emptyList(),
    val selectedForDeletion: Set<Long> = emptySet(),
    val hasScanned: Boolean = false,
    val isWorking: Boolean = false
)

class DuplicateViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PhotoRepository(application)
    private val _uiState = MutableStateFlow(DuplicateUiState())
    val uiState: StateFlow<DuplicateUiState> = _uiState.asStateFlow()

    fun startScan() {
        _uiState.value = DuplicateUiState(isScanning = true)
        viewModelScope.launch(Dispatchers.IO) {
            // Videos aren't meaningfully comparable with this image-similarity approach.
            val photos = repository.queryActivePhotos().filter { !it.isVideo }
            _uiState.value = _uiState.value.copy(totalCount = photos.size)

            val hashes = LongArray(photos.size)
            for (i in photos.indices) {
                currentCoroutineContext().ensureActive()
                hashes[i] = DuplicateHashUtils.computeHash(getApplication<Application>(), photos[i].uri) ?: -1L
                if (i % 20 == 0 || i == photos.size - 1) {
                    _uiState.value = _uiState.value.copy(scannedCount = i + 1)
                }
            }

            val uf = UnionFind(photos.size)
            for (i in photos.indices) {
                if (hashes[i] == -1L) continue
                for (j in i + 1 until photos.size) {
                    if (hashes[j] == -1L) continue
                    if (DuplicateHashUtils.hammingDistance(hashes[i], hashes[j]) <= HAMMING_THRESHOLD) {
                        uf.union(i, j)
                    }
                }
                currentCoroutineContext().ensureActive()
            }

            val clusters = mutableMapOf<Int, MutableList<Photo>>()
            for (i in photos.indices) {
                if (hashes[i] == -1L) continue
                clusters.getOrPut(uf.find(i)) { mutableListOf() }.add(photos[i])
            }

            val groups = clusters.values
                .filter { it.size > 1 }
                .map { group ->
                    val keep = group.maxByOrNull { it.size }!!
                    DuplicateGroup(photos = group.sortedByDescending { it.size }, keepId = keep.id)
                }
                .sortedByDescending { it.photos.size }

            val defaultSelected = groups
                .flatMap { g -> g.photos.filter { it.id != g.keepId }.map { it.id } }
                .toSet()

            _uiState.value = _uiState.value.copy(
                isScanning = false,
                hasScanned = true,
                groups = groups,
                selectedForDeletion = defaultSelected
            )
        }
    }

    fun toggleSelection(photoId: Long) {
        val cur = _uiState.value.selectedForDeletion
        _uiState.value = _uiState.value.copy(
            selectedForDeletion = if (photoId in cur) cur - photoId else cur + photoId
        )
    }

    fun buildDeleteIntent(): PendingIntent? {
        val allPhotos = _uiState.value.groups.flatMap { it.photos }
        val uris = allPhotos.filter { it.id in _uiState.value.selectedForDeletion }.map { it.uri }
        if (uris.isEmpty()) return null
        return repository.createTrashRequest(uris, trash = true)
    }

    fun onDeleteConfirmed() {
        val deletedIds = _uiState.value.selectedForDeletion
        val newGroups = _uiState.value.groups.mapNotNull { g ->
            val remaining = g.photos.filterNot { it.id in deletedIds }
            if (remaining.size > 1) g.copy(photos = remaining) else null
        }
        _uiState.value = _uiState.value.copy(groups = newGroups, selectedForDeletion = emptySet())
    }
}
