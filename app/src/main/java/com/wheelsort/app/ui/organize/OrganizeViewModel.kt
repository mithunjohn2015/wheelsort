package com.wheelsort.app.ui.organize

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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** [folderName] is the literal yyyy-MM folder this group will be moved into, e.g. "2026-08". */
data class MonthGroup(
    val folderName: String,
    val label: String,
    val photos: List<Photo>
)

data class OrganizeUiState(
    val groups: List<MonthGroup> = emptyList(),
    val selected: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val isWorking: Boolean = false,
    val result: OrganizeResult? = null
)

data class OrganizeResult(val moved: Int, val failed: Int, val folderCount: Int)

class OrganizeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PhotoRepository(application)
    private val _uiState = MutableStateFlow(OrganizeUiState())
    val uiState: StateFlow<OrganizeUiState> = _uiState.asStateFlow()

    private var pendingGroups: List<MonthGroup> = emptyList()

    // Numeric folder name exactly as requested (yyyy-MM, sorts correctly as plain text unlike
    // MM-yyyy), plus a human-readable label for the list.
    private val folderFormat = SimpleDateFormat("yyyy-MM", Locale.US)
    private val labelFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

    fun refresh(albumFilter: String? = null) {
        _uiState.value = _uiState.value.copy(isLoading = true, result = null)
        viewModelScope.launch(Dispatchers.IO) {
            val photos = repository.queryActivePhotos(bucketName = albumFilter)
            val groups = photos
                .groupBy { folderFormat.format(Date(effectiveDate(it))) }
                .toSortedMap(compareByDescending { it })
                .map { (folderName, groupPhotos) ->
                    MonthGroup(
                        folderName = folderName,
                        label = labelFormat.format(Date(effectiveDate(groupPhotos.first()))),
                        photos = groupPhotos
                    )
                }
            _uiState.value = _uiState.value.copy(groups = groups, isLoading = false)
        }
    }

    /** Prefers the date the photo was actually taken; falls back to when it was added (e.g. screenshots). */
    private fun effectiveDate(photo: Photo): Long =
        if (photo.dateTaken > 0) photo.dateTaken else photo.dateAdded

    fun toggleGroup(folderName: String) {
        val cur = _uiState.value.selected
        _uiState.value = _uiState.value.copy(selected = if (folderName in cur) cur - folderName else cur + folderName)
    }

    fun selectAll() {
        _uiState.value = _uiState.value.copy(selected = _uiState.value.groups.map { it.folderName }.toSet())
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selected = emptySet())
    }

    /** Builds the one system consent request covering every photo in the selected month groups. */
    fun buildWriteRequest(): PendingIntent? {
        val selectedGroups = _uiState.value.groups.filter { it.folderName in _uiState.value.selected }
        val uris: List<Uri> = selectedGroups.flatMap { it.photos }.map { it.uri }
        if (uris.isEmpty()) return null
        pendingGroups = selectedGroups
        return repository.createWriteRequest(uris)
    }

    /** Call once the write-request consent succeeds - performs the actual moves. */
    fun performMove(onDone: (OrganizeResult) -> Unit) {
        val groups = pendingGroups
        _uiState.value = _uiState.value.copy(isWorking = true)
        viewModelScope.launch(Dispatchers.IO) {
            var moved = 0
            var failed = 0
            for (group in groups) {
                for (photo in group.photos) {
                    if (repository.moveToFolder(photo, group.folderName)) moved++ else failed++
                }
            }
            val result = OrganizeResult(moved = moved, failed = failed, folderCount = groups.size)
            pendingGroups = emptyList()
            _uiState.value = _uiState.value.copy(isWorking = false, selected = emptySet(), result = result)
            onDone(result)
        }
    }
}
