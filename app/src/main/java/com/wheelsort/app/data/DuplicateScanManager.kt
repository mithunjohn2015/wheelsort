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
    val hasScanned: Boolean = false,
    val wasStoppedEarly: Boolean = false,
    /** null = whole library, otherwise the specific album/folder this scan was scoped to. */
    val scopeAlbum: String? = null
) {
    val duplicateCount: Int get() = groups.sumOf { it.photos.size - 1 }
}

/**
 * Owns duplicate-scan state and the scan itself, using its own application-scoped coroutine
 * scope rather than a screen's viewModelScope - navigating away from the Duplicates screen
 * mid-scan used to cancel the whole thing. A plain singleton object rather than a property on
 * the Application subclass, so it never needs to hold a Context itself - callers pass one in at
 * call time instead.
 *
 * Also tracks, persistently, which photo ids and which whole albums have been analyzed across
 * every scan ever run (not just the most recent one) - this backs both the folder picker's
 * checkmarks and Stats' overall "% analyzed for duplicates".
 */
object DuplicateScanManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var scanJob: Job? = null
    @Volatile private var stopRequested = false

    private val _uiState = MutableStateFlow(DuplicateUiState())
    val uiState: StateFlow<DuplicateUiState> = _uiState.asStateFlow()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("duplicate_scan_tracker", Context.MODE_PRIVATE)

    fun analyzedAlbums(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_ANALYZED_ALBUMS, emptySet()) ?: emptySet()

    /** All photo ids ever covered by a completed (not stopped-early) scan, across every scan
     *  run - used for Stats' overall "% analyzed for duplicates". */
    fun analyzedPhotoIds(context: Context): Set<Long> =
        (prefs(context).getStringSet(KEY_ANALYZED_IDS, emptySet()) ?: emptySet())
            .mapNotNull { it.toLongOrNull() }
            .toSet()

    private fun markAnalyzed(context: Context, album: String?, photoIds: Collection<Long>) {
        val p = prefs(context)
        val editor = p.edit()
        if (album != null) {
            val albums = HashSet(p.getStringSet(KEY_ANALYZED_ALBUMS, emptySet()) ?: emptySet())
            albums.add(album)
            editor.putStringSet(KEY_ANALYZED_ALBUMS, albums)
        }
        val ids = HashSet(p.getStringSet(KEY_ANALYZED_IDS, emptySet()) ?: emptySet())
        photoIds.forEach { ids.add(it.toString()) }
        editor.putStringSet(KEY_ANALYZED_IDS, ids)
        editor.apply()
    }

    /** [album] null scans the whole library; otherwise scoped to that one folder. */
    fun startScan(context: Context, album: String? = null) {
        if (_uiState.value.isScanning) return
        scanJob?.cancel()
        stopRequested = false
        _uiState.value = DuplicateUiState(isScanning = true, scopeAlbum = album)
        scanJob = scope.launch {
            val appContext = context.applicationContext
            val repository = PhotoRepository(appContext)
            // Videos aren't meaningfully comparable with this image-similarity approach.
            val photos = repository.queryActivePhotos(bucketName = album).filter { !it.isVideo }
            _uiState.value = _uiState.value.copy(totalCount = photos.size)

            val hashes = LongArray(photos.size)
            var scannedUpTo = 0
            for (i in photos.indices) {
                if (stopRequested) break
                currentCoroutineContext().ensureActive()
                hashes[i] = DuplicateHashUtils.computeHash(appContext, photos[i].uri) ?: -1L
                scannedUpTo = i + 1
                if (i % 20 == 0 || i == photos.size - 1) {
                    _uiState.value = _uiState.value.copy(scannedCount = i + 1)
                }
            }

            val uf = UnionFind(scannedUpTo)
            for (i in 0 until scannedUpTo) {
                if (hashes[i] == -1L) continue
                for (j in i + 1 until scannedUpTo) {
                    if (hashes[j] == -1L) continue
                    if (DuplicateHashUtils.hammingDistance(hashes[i], hashes[j]) <= HAMMING_THRESHOLD) {
                        uf.union(i, j)
                    }
                }
                currentCoroutineContext().ensureActive()
            }

            val clusters = mutableMapOf<Int, MutableList<Photo>>()
            for (i in 0 until scannedUpTo) {
                if (hashes[i] == -1L) continue
                clusters.getOrPut(uf.find(i)) { mutableListOf() }.add(photos[i])
            }

            val groups = clusters.values
                .filter { it.size > 1 }
                .map { group ->
                    // Prioritizes actual resolution over raw file size - a more honest quality
                    // signal since file size varies with compression at the same resolution.
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

            // Only marks as analyzed if it actually finished - a scan stopped partway through
            // shouldn't claim the whole folder is done, only whatever it genuinely covered.
            if (!stopRequested) {
                markAnalyzed(appContext, album, photos.map { it.id })
            } else if (scannedUpTo > 0) {
                markAnalyzed(appContext, null, photos.take(scannedUpTo).map { it.id })
            }

            _uiState.value = _uiState.value.copy(
                isScanning = false,
                hasScanned = true,
                groups = groups,
                selectedForDeletion = defaultSelected,
                wasStoppedEarly = stopRequested
            )
            stopRequested = false
        }
    }

    /** Stops the current scan after whatever's already been hashed, then immediately clusters
     *  and shows results for that partial coverage - not a hard cancel with nothing to show. */
    fun stopScan() {
        stopRequested = true
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

    private const val KEY_ANALYZED_ALBUMS = "analyzed_albums"
    private const val KEY_ANALYZED_IDS = "analyzed_ids"
}
