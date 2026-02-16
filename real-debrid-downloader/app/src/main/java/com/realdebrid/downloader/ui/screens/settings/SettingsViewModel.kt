package com.realdebrid.downloader.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.realdebrid.downloader.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val apiToken: Flow<String> = settingsRepository.apiToken
    val downloadPath: Flow<String> = settingsRepository.downloadPath

    fun saveApiToken(token: String) {
        viewModelScope.launch {
            settingsRepository.setApiToken(token)
        }
    }

    fun saveDownloadPath(path: String) {
        viewModelScope.launch {
            settingsRepository.setDownloadPath(path)
        }
    }

    fun clearData() {
        viewModelScope.launch {
            settingsRepository.clearAll()
        }
    }
}
