package com.realdebrid.downloader.ui.debug

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.realdebrid.downloader.data.local.DownloadDao
import com.realdebrid.downloader.data.local.DownloadEntity
import com.realdebrid.downloader.data.local.DownloadStatus
import com.realdebrid.downloader.download.DownloadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named

data class DebugUiState(
    val isDownloading: Boolean = false,
    val currentDownload: DownloadEntity? = null,
    val error: String? = null
)

@HiltViewModel
class DebugDownloadViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao,
    @Named("downloader") private val httpClient: OkHttpClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(DebugUiState())
    val uiState: StateFlow<DebugUiState> = _uiState.asStateFlow()

    val downloads: Flow<List<DownloadEntity>> = downloadDao.getAllDownloads()

    private var currentDownloadId: String? = null

    init {
        viewModelScope.launch {
            while (true) {
                delay(500)
                currentDownloadId?.let { id ->
                    downloadDao.getDownloadById(id)?.let { download ->
                        _uiState.update { it.copy(currentDownload = download) }
                        if (download.status == DownloadStatus.COMPLETED || 
                            download.status == DownloadStatus.FAILED ||
                            download.status == DownloadStatus.CANCELLED) {
                            _uiState.update { it.copy(isDownloading = false) }
                            currentDownloadId = null
                        }
                    }
                }
            }
        }
    }

    fun startDownload(url: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isDownloading = true, error = null) }

                val fileInfo = fetchFileInfo(url)
                
                val download = DownloadEntity(
                    id = UUID.randomUUID().toString(),
                    url = url,
                    filename = fileInfo.filename,
                    filesize = fileInfo.size,
                    status = DownloadStatus.QUEUED
                )

                downloadDao.insert(download)
                currentDownloadId = download.id
                _uiState.update { it.copy(currentDownload = download) }

                DownloadWorker.enqueue(context, download.id)

            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isDownloading = false, 
                        error = e.message ?: "Unknown error"
                    ) 
                }
            }
        }
    }

    fun pauseDownload(downloadId: String) {
        viewModelScope.launch {
            DownloadWorker.pause(context, downloadId)
            downloadDao.updateStatus(downloadId, DownloadStatus.PAUSED)
        }
    }

    fun resumeDownload(downloadId: String) {
        viewModelScope.launch {
            downloadDao.updateStatus(downloadId, DownloadStatus.QUEUED)
            DownloadWorker.enqueue(context, downloadId)
            _uiState.update { it.copy(isDownloading = true) }
            currentDownloadId = downloadId
        }
    }

    fun cancelDownload(downloadId: String) {
        viewModelScope.launch {
            DownloadWorker.cancel(context, downloadId)
            downloadDao.updateStatus(downloadId, DownloadStatus.CANCELLED)
            _uiState.update { it.copy(isDownloading = false, currentDownload = null) }
            currentDownloadId = null
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            currentDownloadId?.let { DownloadWorker.cancel(context, it) }
            downloadDao.deleteAll()
            _uiState.update { DebugUiState() }
            currentDownloadId = null
        }
    }

    private suspend fun fetchFileInfo(url: String): FileInfo {
        val request = Request.Builder()
            .url(url)
            .head()
            .build()

        val response = httpClient.newCall(request).execute()
        
        val contentLength = response.header("Content-Length")?.toLongOrNull() ?: 0L
        val contentDisposition = response.header("Content-Disposition")
        val filename = contentDisposition?.let { 
            Regex("filename=\"?([^\"]+)\"?").find(it)?.groupValues?.get(1)
        } ?: url.substringAfterLast("/").substringBefore("?").ifEmpty { "download_${System.currentTimeMillis()}" }

        response.close()

        return FileInfo(filename, contentLength)
    }

    private data class FileInfo(val filename: String, val size: Long)
}
