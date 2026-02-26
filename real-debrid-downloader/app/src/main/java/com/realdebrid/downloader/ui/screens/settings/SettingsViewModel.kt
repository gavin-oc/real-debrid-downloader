package com.realdebrid.downloader.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.realdebrid.downloader.data.local.DownloadDao
import com.realdebrid.downloader.data.repository.AppSettings
import com.realdebrid.downloader.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val isLoading: Boolean = true,
    val tokenSaved: Boolean = false,
    val dataCleared: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val downloadDao: DownloadDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val apiToken: Flow<String> = settingsRepository.apiToken
    val downloadPath: Flow<String> = settingsRepository.downloadPath

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings, isLoading = false) }
            }
        }
    }

    fun saveApiToken(token: String) {
        viewModelScope.launch {
            settingsRepository.setApiToken(token)
            _uiState.update { it.copy(tokenSaved = true) }
        }
    }

    fun clearTokenSaved() {
        _uiState.update { it.copy(tokenSaved = false) }
    }

    fun saveDownloadPath(path: String) {
        viewModelScope.launch {
            settingsRepository.setDownloadPath(path)
        }
    }

    fun setMaxConcurrentDownloads(max: Int) {
        viewModelScope.launch {
            settingsRepository.setMaxConcurrentDownloads(max)
        }
    }

    fun setWifiOnly(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setWifiOnly(enabled)
        }
    }

    fun setAutoStartDownloads(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoStartDownloads(enabled)
        }
    }

    fun setShowCompletedNotification(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowCompletedNotification(enabled)
        }
    }

    fun setShowFailedNotification(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowFailedNotification(enabled)
        }
    }

    fun setVibrateOnComplete(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setVibrateOnComplete(enabled)
        }
    }

    fun setDarkMode(mode: String) {
        viewModelScope.launch {
            settingsRepository.setDarkMode(mode)
        }
    }

    fun clearData() {
        viewModelScope.launch {
            settingsRepository.clearAll()
            downloadDao.deleteAll()
            _uiState.update { it.copy(dataCleared = true) }
        }
    }

    fun clearDataCleared() {
        _uiState.update { it.copy(dataCleared = false) }
    }
}
