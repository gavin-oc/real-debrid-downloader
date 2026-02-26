package com.realdebrid.downloader.ui.screens.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.realdebrid.downloader.data.model.Download
import com.realdebrid.downloader.data.model.User
import com.realdebrid.downloader.data.repository.DownloadRepository
import com.realdebrid.downloader.service.DownloadService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = false,
    val user: User? = null,
    val recentDownloads: List<Download> = emptyList(),
    val error: String? = null,
    val addLinkSuccess: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DownloadRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadUser()
        loadDownloads()
    }

    private fun loadUser() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.getUser()
                .onSuccess { user ->
                    _uiState.update { it.copy(user = user, error = null) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message) }
                }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private fun loadDownloads() {
        viewModelScope.launch {
            repository.getDownloads()
                .onSuccess { downloads ->
                    _uiState.update { it.copy(recentDownloads = downloads) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }

    fun addLink(link: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, addLinkSuccess = false) }

            if (link.startsWith("magnet:")) {
                addMagnet(link)
            } else {
                addDirectLink(link)
            }

            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private suspend fun addMagnet(magnet: String) {
        repository.addMagnet(magnet)
            .onSuccess { torrentId ->
                repository.selectFiles(torrentId)
                _uiState.update { it.copy(addLinkSuccess = true) }
                loadDownloads()
            }
            .onFailure { e ->
                _uiState.update { it.copy(error = "Failed to add magnet: ${e.message}") }
            }
    }

    private suspend fun addDirectLink(link: String) {
        repository.unrestrictLink(link)
            .onSuccess { response ->
                // Queue download and start it
                val entity = repository.queueDownload(link, response)
                DownloadService.startDownload(context, entity.id)
                _uiState.update { it.copy(addLinkSuccess = true) }
                loadDownloads()
            }
            .onFailure { e ->
                _uiState.update { it.copy(error = "Failed to unrestrict: ${e.message}") }
            }
    }

    fun downloadFromCache(download: Download) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // The download object from RD API already has the direct URL
            repository.unrestrictLink(download.link)
                .onSuccess { response ->
                    val entity = repository.queueDownload(download.link, response)
                    DownloadService.startDownload(context, entity.id)
                    _uiState.update { it.copy(addLinkSuccess = true) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = "Failed to start download: ${e.message}") }
                }

            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearSuccess() {
        _uiState.update { it.copy(addLinkSuccess = false) }
    }

    fun refresh() {
        loadUser()
        loadDownloads()
    }
}
