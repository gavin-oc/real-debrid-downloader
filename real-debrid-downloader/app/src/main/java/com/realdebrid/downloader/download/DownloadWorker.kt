package com.realdebrid.downloader.download

import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.realdebrid.downloader.RealDebridApp
import com.realdebrid.downloader.data.local.DownloadDao
import com.realdebrid.downloader.data.local.DownloadStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val downloadDao: DownloadDao,
    private val downloaderFactory: FileDownloader.Factory
) : CoroutineWorker(context, workerParams) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID) ?: return@withContext Result.failure()
        val download = downloadDao.getDownloadById(downloadId) ?: return@withContext Result.failure()

        setForeground(createForegroundInfo(download.filename, 0))

        val outputDir = File(context.getExternalFilesDir(null), "downloads").apply { mkdirs() }
        val outputFile = File(outputDir, sanitizeFilename(download.filename))
        val downloader = downloaderFactory.create()

        var lastProgress = 0
        var result: Result = Result.failure()

        downloader.download(
            url = download.downloadUrl,
            outputFile = outputFile,
            onProgress = { bytesDownloaded, totalBytes, speed ->
                val percent = if (totalBytes > 0) ((bytesDownloaded * 100) / totalBytes).toInt() else 0
                if (percent != lastProgress) {
                    lastProgress = percent
                    downloadDao.updateProgress(
                        id = downloadId,
                        progress = percent,
                        status = DownloadStatus.DOWNLOADING,
                        speed = speed,
                        downloadedBytes = bytesDownloaded
                    )
                    updateNotification(download.filename, percent, speed)
                    setProgressAsync(workDataOf(KEY_PROGRESS to percent))
                }
            },
            onComplete = {
                downloadDao.markCompleted(
                    id = downloadId,
                    localPath = outputFile.absolutePath
                )
                showCompletedNotification(download.filename)
                result = Result.success()
            },
            onError = { e ->
                downloadDao.markFailed(
                    id = downloadId,
                    errorMessage = e.message
                )
                result = Result.failure(workDataOf(KEY_ERROR to e.message))
            }
        )

        result
    }

    private fun createForegroundInfo(filename: String, progress: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(context, RealDebridApp.CHANNEL_DOWNLOADS)
            .setContentTitle(filename)
            .setContentText("Starting download...")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(filename: String, progress: Int, speed: Long) {
        val speedText = formatSpeed(speed)
        val notification = NotificationCompat.Builder(context, RealDebridApp.CHANNEL_DOWNLOADS)
            .setContentTitle(filename)
            .setContentText("$progress% • $speedText")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showCompletedNotification(filename: String) {
        val notification = NotificationCompat.Builder(context, RealDebridApp.CHANNEL_COMPLETED)
            .setContentTitle("Download complete")
            .setContentText(filename)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(filename.hashCode(), notification)
    }

    private fun formatSpeed(bytesPerSecond: Long): String {
        return when {
            bytesPerSecond >= 1_048_576 -> "%.1f MB/s".format(bytesPerSecond / 1_048_576.0)
            bytesPerSecond >= 1024 -> "%.1f KB/s".format(bytesPerSecond / 1024.0)
            else -> "$bytesPerSecond B/s"
        }
    }

    private fun sanitizeFilename(filename: String): String {
        return filename.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"
        private const val NOTIFICATION_ID = 1001

        fun enqueue(context: Context, downloadId: String): Operation {
            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(workDataOf(KEY_DOWNLOAD_ID to downloadId))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .addTag(downloadId)
                .build()

            return WorkManager.getInstance(context).enqueueUniqueWork(
                downloadId,
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        fun pause(context: Context, downloadId: String) {
            WorkManager.getInstance(context).cancelUniqueWork(downloadId)
        }

        fun cancel(context: Context, downloadId: String) {
            WorkManager.getInstance(context).cancelUniqueWork(downloadId)
        }
    }
}
