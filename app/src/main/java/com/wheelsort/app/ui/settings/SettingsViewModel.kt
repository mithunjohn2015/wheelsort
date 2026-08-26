package com.wheelsort.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.wheelsort.app.data.WheelSettings
import com.wheelsort.app.data.WheelSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WheelSettingsRepository(application)
    private val _settings = MutableStateFlow(repository.load())
    val settings: StateFlow<WheelSettings> = _settings.asStateFlow()

    fun update(transform: (WheelSettings) -> WheelSettings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        repository.save(updated)
    }

    fun resetToDefaults() {
        repository.reset()
        _settings.value = WheelSettings.DEFAULT
    }
}
