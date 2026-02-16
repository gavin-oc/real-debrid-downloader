package com.realdebrid.downloader.download

import android.content.Context
import com.realdebrid.downloader.data.local.DownloadDao
import com.realdebrid.downloader.data.local.DownloadEntity
import com.realdebrid.downloader.data.local.DownloadStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao,
    private val downloaderFactory: FileDownloader.Factory
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeDownloads = ConcurrentHashMap<String, DownloadTask>()
    
    private val _downloadProgress = MutableSharedFlow<DownloadProgress>(replay = 1)
    val downloadProgress: SharedFlow<DownloadProgress> = _downloadProgress.asSharedFlow()

    fun startDownload(download: DownloadEntity) {
        if (activeDownloads.containsKey(download.id)) return
        
        val task = DownloadTask(
            download = download,
            downloader = downloaderFactory.create(),
            outputDir = getDownloadDirectory(),
            onProgress = { progress ->
                scope.launch {
                    _downloadProgress.emit(progress)
                    downloadDao.updateProgress(
                        progress.downloadId,
                        DownloadStatus.DOWNLOADING,
                        progress.percent
                    )
                }
            },
            onComplete = { path ->
                scope.launch {
                    downloadDao.markCompleted(download.id, DownloadStatus.COMPLETED, System.currentTimeMillis(), path)
                    activeDownloads.remove(download.id)
                }
            },
            onError = { error ->
                scope.launch {
                    downloadDao.updateProgress(download.id, DownloadStatus.FAILED, 0)
                    activeDownloads.remove(download.id)
                }
            }
        )
        
        activeDownloads[download.id] = task
        task.start(scope)
    }

    fun pauseDownload(downloadId: String) {
        activeDownloads[downloadId]?.pause()
        activeDownloads.remove(downloadId)
        scope.launch {
            downloadDao.updateProgress(downloadId, DownloadStatus.PAUSED, 
                activeDownloads[downloadId]?.currentProgress ?: 0)
        }
    }

    fun resumeDownload(download: DownloadEntity) {
        startDownload(download)
    }

    fun cancelDownload(downloadId: String) {
        activeDownloads[downloadId]?.cancel()
        activeDownloads.remove(downloadId)
        scope.launch {
            val download = downloadDao.getDownloadById(downloadId)
            download?.localPath?.let { File(it).delete() }
            downloadDao.deleteDownloadById(downloadId)
        }
    }

    fun isDownloading(downloadId: String): Boolean = activeDownloads.containsKey(downloadId)

    private fun getDownloadDirectory(): File {
        val dir = File(context.getExternalFilesDir(null), "downloads")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun shutdown() {
        activeDownloads.values.forEach { it.cancel() }
        activeDownloads.clear()
        scope.cancel()
    }
}

data class DownloadProgress(
    val downloadId: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val speed: Long,
    val percent: Int
)
