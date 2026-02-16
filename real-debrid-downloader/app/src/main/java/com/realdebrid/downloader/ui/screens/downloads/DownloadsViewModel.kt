package com.realdebrid.downloader.ui.screens.downloads

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.realdebrid.downloader.data.local.DownloadEntity
import com.realdebrid.downloader.data.local.DownloadStatus
import com.realdebrid.downloader.data.repository.DownloadRepository
import com.realdebrid.downloader.download.DownloadManager
import com.realdebrid.downloader.download.DownloadProgress
import com.realdebrid.downloader.download.DownloadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DownloadRepository,
    private val downloadManager: DownloadManager
) : ViewModel() {

    val downloads: Flow<List<DownloadEntity>> = repository.getLocalDownloads()

    private val _progressMap = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val progressMap: StateFlow<Map<String, DownloadProgress>> = _progressMap.asStateFlow()

    init {
        viewModelScope.launch {
            downloadManager.downloadProgress.collect { progress ->
                _progressMap.value = _progressMap.value + (progress.downloadId to progress)
            }
        }
    }

    fun startDownload(download: DownloadEntity) {
        DownloadWorker.enqueue(context, download.id)
    }

    fun pauseDownload(download: DownloadEntity) {
        DownloadWorker.pause(context, download.id)
        viewModelScope.launch {
            repository.updateProgress(download.id, DownloadStatus.PAUSED, download.progress)
        }
    }

    fun deleteDownload(download: DownloadEntity) {
        DownloadWorker.cancel(context, download.id)
        viewModelScope.launch {
            repository.deleteLocalDownload(download.id)
        }
    }
}
