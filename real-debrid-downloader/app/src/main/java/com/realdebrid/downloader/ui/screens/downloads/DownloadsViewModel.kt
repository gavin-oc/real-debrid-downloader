package com.realdebrid.downloader.ui.screens.downloads

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.realdebrid.downloader.data.local.DownloadEntity
import com.realdebrid.downloader.data.local.DownloadStatus
import com.realdebrid.downloader.data.repository.DownloadRepository
import com.realdebrid.downloader.service.DownloadService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DownloadRepository
) : ViewModel() {

    val downloads: StateFlow<List<DownloadEntity>> = repository.getAllDownloadsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    init {
        observeDownloads()
    }

    private fun observeDownloads() {
        viewModelScope.launch {
            repository.getAllDownloadsFlow().collect { downloads ->
                val activeCount = downloads.count { it.status == DownloadStatus.DOWNLOADING }
                val completedCount = downloads.count { it.status == DownloadStatus.COMPLETED }
                val failedCount = downloads.count { it.status == DownloadStatus.FAILED }

                _uiState.update {
                    it.copy(
                        activeCount = activeCount,
                        completedCount = completedCount,
                        failedCount = failedCount,
                        isEmpty = downloads.isEmpty()
                    )
                }
            }
        }
    }

    fun startDownload(download: DownloadEntity) {
        DownloadService.startDownload(context, download.id)
    }

    fun pauseDownload(download: DownloadEntity) {
        DownloadService.pauseDownload(context, download.id)
    }

    fun resumeDownload(download: DownloadEntity) {
        DownloadService.resumeDownload(context, download.id)
    }

    fun cancelDownload(download: DownloadEntity) {
        DownloadService.cancelDownload(context, download.id)
    }

    fun deleteDownload(download: DownloadEntity) {
        DownloadService.deleteDownload(context, download.id)
    }

    fun retryDownload(download: DownloadEntity) {
        viewModelScope.launch {
            // Reset status and retry
            repository.updateStatus(download.id, DownloadStatus.QUEUED)
            DownloadService.startDownload(context, download.id)
        }
    }

    fun clearCompleted() {
        viewModelScope.launch {
            val completed = repository.getDownloadsByStatus(DownloadStatus.COMPLETED)
            completed.forEach { DownloadService.deleteDownload(context, it.id) }
        }
    }

    fun clearFailed() {
        viewModelScope.launch {
            val failed = repository.getDownloadsByStatus(DownloadStatus.FAILED)
            failed.forEach { DownloadService.deleteDownload(context, it.id) }
        }
    }

    fun clearAll() {
        DownloadService.deleteAllDownloads(context)
    }

    fun retryAllFailed() {
        viewModelScope.launch {
            val failed = repository.getDownloadsByStatus(DownloadStatus.FAILED)
            failed.forEach { download ->
                repository.updateStatus(download.id, DownloadStatus.QUEUED)
                DownloadService.startDownload(context, download.id)
            }
        }
    }
}

data class DownloadsUiState(
    val activeCount: Int = 0,
    val completedCount: Int = 0,
    val failedCount: Int = 0,
    val isEmpty: Boolean = true
)
