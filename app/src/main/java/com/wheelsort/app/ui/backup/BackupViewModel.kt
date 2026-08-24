package com.wheelsort.app.ui.backup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wheelsort.app.data.ChecksumUtils
import com.wheelsort.app.data.ImmichRepository
import com.wheelsort.app.data.Photo
import com.wheelsort.app.data.PhotoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ConnectionStatus { UNKNOWN, TESTING, SUCCESS, FAILURE }

data class BackupUiState(
    val serverUrl: String = "",
    val apiKey: String = "",
    val isConfigured: Boolean = false,
    val connectionStatus: ConnectionStatus = ConnectionStatus.UNKNOWN,
    val isChecking: Boolean = false,
    val checkedCount: Int = 0,
    val totalCount: Int = 0,
    val backedUpCount: Int = 0,
    val notBackedUp: List<Photo> = emptyList(),
    val hasResults: Boolean = false,
    val errorMessage: String? = null
)

private const val BATCH_SIZE = 50

class BackupViewModel(application: Application) : AndroidViewModel(application) {

    private val photoRepository = PhotoRepository(application)
    private val immichRepository = ImmichRepository(application)
    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    init {
        val settings = immichRepository.loadSettings()
        _uiState.value = _uiState.value.copy(
            serverUrl = settings.serverUrl,
            apiKey = settings.apiKey,
            isConfigured = settings.isConfigured
        )
    }

    fun updateServerUrl(url: String) {
        _uiState.value = _uiState.value.copy(serverUrl = url)
    }

    fun updateApiKey(key: String) {
        _uiState.value = _uiState.value.copy(apiKey = key)
    }

    fun saveAndTestConnection() {
        val url = _uiState.value.serverUrl
        val key = _uiState.value.apiKey
        immichRepository.saveSettings(url, key)
        _uiState.value = _uiState.value.copy(
            connectionStatus = ConnectionStatus.TESTING,
            isConfigured = url.isNotBlank() && key.isNotBlank(),
            errorMessage = null
        )
        viewModelScope.launch(Dispatchers.IO) {
            val settings = immichRepository.loadSettings()
            val result = immichRepository.testConnection(settings)
            _uiState.value = _uiState.value.copy(
                connectionStatus = if (result.isSuccess) ConnectionStatus.SUCCESS else ConnectionStatus.FAILURE,
                errorMessage = result.exceptionOrNull()?.message
            )
        }
    }

    fun startBackupCheck() {
        val settings = immichRepository.loadSettings()
        if (!settings.isConfigured) return

        _uiState.value = _uiState.value.copy(
            isChecking = true,
            checkedCount = 0,
            totalCount = 0,
            backedUpCount = 0,
            notBackedUp = emptyList(),
            hasResults = false,
            errorMessage = null
        )

        viewModelScope.launch(Dispatchers.IO) {
            val photos = photoRepository.queryActivePhotos()
            _uiState.value = _uiState.value.copy(totalCount = photos.size)

            val notBackedUp = mutableListOf<Photo>()
            var backedUp = 0
            var checked = 0
            val batch = mutableListOf<Pair<Photo, String>>()
            var stopped = false

            suspend fun flushBatch() {
                if (batch.isEmpty()) return
                val items = batch.map { it.first.id.toString() to it.second }
                immichRepository.checkBackupStatus(settings, items)
                    .onSuccess { map ->
                        for ((photo, _) in batch) {
                            if (map[photo.id.toString()] == true) backedUp++ else notBackedUp.add(photo)
                        }
                    }
                    .onFailure { e ->
                        _uiState.value = _uiState.value.copy(errorMessage = e.message ?: "Connection error")
                        stopped = true
                    }
                batch.clear()
            }

            for (photo in photos) {
                currentCoroutineContext().ensureActive()
                if (stopped) break
                val checksum = ChecksumUtils.sha1Hex(getApplication<Application>(), photo.uri)
                checked++
                if (checksum != null) {
                    batch.add(photo to checksum)
                    if (batch.size >= BATCH_SIZE) flushBatch()
                } else {
                    notBackedUp.add(photo) // couldn't read it - safest to flag it for review
                }
                if (checked % 15 == 0 || checked == photos.size) {
                    _uiState.value = _uiState.value.copy(
                        checkedCount = checked,
                        backedUpCount = backedUp,
                        notBackedUp = notBackedUp.toList()
                    )
                }
            }
            if (!stopped) flushBatch()

            _uiState.value = _uiState.value.copy(
                isChecking = false,
                checkedCount = checked,
                backedUpCount = backedUp,
                notBackedUp = notBackedUp.toList(),
                hasResults = true
            )
        }
    }
}
