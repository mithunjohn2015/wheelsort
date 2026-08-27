package com.wheelsort.app.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
    val hasScanned: Boolean = false
) {
    val duplicateCount: Int get() = groups.sumOf { it.photos.size - 1 }
}

/**
 * Owns duplicate-scan state and the scan itself, using its own application-scoped coroutine
 * scope rather than a screen's viewModelScope - navigating away from the Duplicates screen
 * mid-scan used to cancel the whole thing (ViewModels are cleared when their nav backstack entry
 * is popped), forcing the user to sit and wait through however long a large library takes. A
 * plain singleton object rather than a property on the Application subclass, so it never needs
 * to hold a Context itself - callers pass one in at call time instead.
 */
object DuplicateScanManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var scanJob: Job? = null

    private val _uiState = MutableStateFlow(DuplicateUiState())
    val uiState: StateFlow<DuplicateUiState> = _uiState.asStateFlow()

    fun startScan(context: Context) {
        if (_uiState.value.isScanning) return
        scanJob?.cancel()
        _uiState.value = DuplicateUiState(isScanning = true)
        scanJob = scope.launch {
            val appContext = context.applicationContext
            val repository = PhotoRepository(appContext)
            // Videos aren't meaningfully comparable with this image-similarity approach.
            val photos = repository.queryActivePhotos().filter { !it.isVideo }
            _uiState.value = _uiState.value.copy(totalCount = photos.size)

            val hashes = LongArray(photos.size)
            for (i in photos.indices) {
                currentCoroutineContext().ensureActive()
                hashes[i] = DuplicateHashUtils.computeHash(appContext, photos[i].uri) ?: -1L
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
                    // Prioritizes actual resolution (width x height) over raw file size - two
                    // copies of the same shot can differ in file size purely from compression
                    // choices, but resolution is a more honest signal of which one is genuinely
                    // the higher-quality copy. File size is the tiebreaker for equal resolution
                    // (larger usually means less aggressive compression, so likely better quality).
                    fun pixelCount(p: Photo) = p.width.toLong() * p.height.toLong()
                    val keep = group.maxWithOrNull(compareBy({ pixelCount(it) }, { it.size }))!!
                    DuplicateGroup(
                        photos = group.sortedByDescending { pixelCount(it) },
                        keepId = keep.id
                    )
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

    fun removeDeletedFromGroups(deletedIds: Set<Long>) {
        val newGroups = _uiState.value.groups.mapNotNull { g ->
            val remaining = g.photos.filterNot { it.id in deletedIds }
            if (remaining.size > 1) g.copy(photos = remaining) else null
        }
        _uiState.value = _uiState.value.copy(groups = newGroups, selectedForDeletion = emptySet())
    }
}
