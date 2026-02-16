package com.realdebrid.downloader.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.realdebrid.downloader.data.local.DownloadEntity
import com.realdebrid.downloader.data.model.Download
import com.realdebrid.downloader.data.model.User
import com.realdebrid.downloader.data.repository.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
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
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            if (link.startsWith("magnet:")) {
                repository.addMagnet(link)
                    .onSuccess { torrentId ->
                        repository.selectFiles(torrentId)
                        loadDownloads()
                    }
                    .onFailure { e ->
                        _uiState.update { it.copy(error = e.message) }
                    }
            } else {
                repository.unrestrictLink(link)
                    .onSuccess { response ->
                        val entity = DownloadEntity(
                            id = response.id,
                            url = response.download,
                            filename = response.filename,
                            filesize = response.filesize
                        )
                        repository.saveLocalDownload(entity)
                        loadDownloads()
                    }
                    .onFailure { e ->
                        _uiState.update { it.copy(error = e.message) }
                    }
            }
            
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun refresh() {
        loadUser()
        loadDownloads()
    }
}
