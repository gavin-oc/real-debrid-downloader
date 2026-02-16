package com.realdebrid.downloader.download

import com.realdebrid.downloader.data.local.DownloadEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

class DownloadTask(
    private val download: DownloadEntity,
    private val downloader: FileDownloader,
    private val outputDir: File,
    private val onProgress: (DownloadProgress) -> Unit,
    private val onComplete: (String) -> Unit,
    private val onError: (Exception) -> Unit
) {
    private var job: Job? = null
    var currentProgress: Int = download.progress
        private set

    fun start(scope: CoroutineScope) {
        val outputFile = File(outputDir, sanitizeFilename(download.filename))
        
        job = scope.launch(Dispatchers.IO) {
            downloader.download(
                url = download.downloadUrl,
                outputFile = outputFile,
                onProgress = { bytesDownloaded, totalBytes, speed ->
                    val percent = if (totalBytes > 0) {
                        ((bytesDownloaded * 100) / totalBytes).toInt().coerceIn(0, 100)
                    } else 0
                    currentProgress = percent
                    
                    onProgress(
                        DownloadProgress(
                            downloadId = download.id,
                            bytesDownloaded = bytesDownloaded,
                            totalBytes = totalBytes,
                            speed = speed,
                            percent = percent
                        )
                    )
                },
                onComplete = {
                    onComplete(outputFile.absolutePath)
                },
                onError = onError
            )
        }
    }

    fun pause() {
        downloader.pause()
        job?.cancel()
    }

    fun resume(scope: CoroutineScope) {
        downloader.resume()
        start(scope)
    }

    fun cancel() {
        downloader.cancel()
        job?.cancel()
    }

    private fun sanitizeFilename(filename: String): String {
        return filename.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }
}
