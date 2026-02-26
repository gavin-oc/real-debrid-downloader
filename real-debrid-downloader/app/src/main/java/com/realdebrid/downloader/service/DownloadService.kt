package com.realdebrid.downloader.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.realdebrid.downloader.R
import com.realdebrid.downloader.RealDebridApp
import com.realdebrid.downloader.data.local.DownloadDao
import com.realdebrid.downloader.data.local.DownloadEntity
import com.realdebrid.downloader.data.local.DownloadStatus
import com.realdebrid.downloader.download.DownloadEngine
import com.realdebrid.downloader.download.DownloadResult
import com.realdebrid.downloader.download.EngineEvent
import com.realdebrid.downloader.download.EngineProgress
import com.realdebrid.downloader.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@AndroidEntryPoint
class DownloadService : Service() {

    @Inject lateinit var downloadDao: DownloadDao
    @Inject lateinit var engineFactory: DownloadEngine.Factory

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeDownloads = ConcurrentHashMap<String, ActiveDownload>()
    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    private data class ActiveDownload(
        val engine: DownloadEngine,
        val job: Job,
        val entity: DownloadEntity
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID_SERVICE, createServiceNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID)
                if (downloadId != null) {
                    serviceScope.launch { startDownload(downloadId) }
                }
            }
            ACTION_PAUSE -> {
                val downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID)
                if (downloadId != null) {
                    pauseDownload(downloadId)
                }
            }
            ACTION_RESUME -> {
                val downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID)
                if (downloadId != null) {
                    serviceScope.launch { resumeDownload(downloadId) }
                }
            }
            ACTION_CANCEL -> {
                val downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID)
                if (downloadId != null) {
                    cancelDownload(downloadId)
                }
            }
            ACTION_CANCEL_ALL -> cancelAllDownloads()
        }

        return START_STICKY
    }

    private suspend fun startDownload(downloadId: String) {
        if (activeDownloads.containsKey(downloadId)) return

        val entity = downloadDao.getDownloadById(downloadId) ?: return
        val engine = engineFactory.create()
        val outputFile = getOutputFile(entity.filename)

        // Update status to downloading
        downloadDao.updateStatus(downloadId, DownloadStatus.DOWNLOADING)

        // Create notification for this download
        updateDownloadNotification(downloadId, entity.filename, 0, "Starting...")

        val job = serviceScope.launch {
            // Collect progress updates
            launch {
                engine.progress.collectLatest { progress ->
                    handleProgress(downloadId, entity.filename, progress)
                }
            }

            // Collect events
            launch {
                engine.events.collectLatest { event ->
                    handleEvent(downloadId, entity.filename, event)
                }
            }

            // Execute download
            val result = engine.download(entity.downloadUrl, outputFile)
            handleResult(downloadId, entity.filename, outputFile, result)
        }

        activeDownloads[downloadId] = ActiveDownload(engine, job, entity)
        updateServiceNotification()
    }

    private suspend fun handleProgress(downloadId: String, filename: String, progress: EngineProgress) {
        // Update database
        downloadDao.updateProgress(
            id = downloadId,
            status = DownloadStatus.DOWNLOADING,
            progress = progress.percent,
            bytesDownloaded = progress.bytesDownloaded
        )

        // Update notification
        val text = "${progress.formatSize()} - ${progress.formatSpeed()}"
        updateDownloadNotification(downloadId, filename, progress.percent, text)
    }

    private fun handleEvent(downloadId: String, filename: String, event: EngineEvent) {
        when (event) {
            is EngineEvent.StallDetected -> {
                updateDownloadNotification(downloadId, filename, -1, "Stall detected, retrying...")
            }
            is EngineEvent.Retrying -> {
                updateDownloadNotification(downloadId, filename, -1, "Retry ${event.attempt}/${event.maxAttempts}")
            }
            else -> {}
        }
    }

    private suspend fun handleResult(downloadId: String, filename: String, outputFile: File, result: DownloadResult) {
        activeDownloads.remove(downloadId)

        when (result) {
            is DownloadResult.Success -> {
                downloadDao.markCompleted(downloadId, localPath = result.path)
                showCompletedNotification(downloadId, filename)
            }
            is DownloadResult.Error -> {
                downloadDao.markFailed(downloadId, errorMessage = result.reason)
                showFailedNotification(downloadId, filename, result.reason)
            }
            is DownloadResult.Cancelled -> {
                downloadDao.updateStatus(downloadId, DownloadStatus.CANCELLED)
                cancelDownloadNotification(downloadId)
            }
        }

        updateServiceNotification()
        checkAndStopService()
    }

    private fun pauseDownload(downloadId: String) {
        activeDownloads[downloadId]?.let { active ->
            active.engine.pause()
            serviceScope.launch {
                downloadDao.updateStatus(downloadId, DownloadStatus.PAUSED)
            }
            updateDownloadNotification(downloadId, active.entity.filename, -1, "Paused")
        }
    }

    private suspend fun resumeDownload(downloadId: String) {
        val active = activeDownloads[downloadId]
        if (active != null && active.engine.isPaused()) {
            active.engine.resume()
            downloadDao.updateStatus(downloadId, DownloadStatus.DOWNLOADING)
        } else {
            // Restart download
            startDownload(downloadId)
        }
    }

    private fun cancelDownload(downloadId: String) {
        activeDownloads[downloadId]?.let { active ->
            active.engine.cancel()
            active.job.cancel()
            activeDownloads.remove(downloadId)

            serviceScope.launch {
                val entity = downloadDao.getDownloadById(downloadId)
                entity?.localPath?.let { File(it).delete() }
                downloadDao.updateStatus(downloadId, DownloadStatus.CANCELLED)
            }

            cancelDownloadNotification(downloadId)
            updateServiceNotification()
            checkAndStopService()
        }
    }

    private fun cancelAllDownloads() {
        activeDownloads.forEach { (id, active) ->
            active.engine.cancel()
            active.job.cancel()
            cancelDownloadNotification(id)
        }
        activeDownloads.clear()

        serviceScope.launch {
            val downloading = downloadDao.getByStatus(DownloadStatus.DOWNLOADING)
            downloading.forEach { downloadDao.updateStatus(it.id, DownloadStatus.CANCELLED) }
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun checkAndStopService() {
        if (activeDownloads.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun getOutputFile(filename: String): File {
        val dir = File(getExternalFilesDir(null), "downloads")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, sanitizeFilename(filename))
    }

    private fun sanitizeFilename(filename: String): String {
        return filename.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }

    // Notifications

    private fun createServiceNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, RealDebridApp.CHANNEL_DOWNLOADS)
            .setContentTitle("Real-Debrid Downloader")
            .setContentText("${activeDownloads.size} active downloads")
            .setSmallIcon(R.drawable.ic_download)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateServiceNotification() {
        val notification = createServiceNotification()
        notificationManager.notify(NOTIFICATION_ID_SERVICE, notification)
    }

    private fun updateDownloadNotification(downloadId: String, filename: String, progress: Int, text: String) {
        val cancelIntent = Intent(this, DownloadService::class.java).apply {
            action = ACTION_CANCEL
            putExtra(EXTRA_DOWNLOAD_ID, downloadId)
        }
        val cancelPendingIntent = PendingIntent.getService(
            this, downloadId.hashCode(), cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, RealDebridApp.CHANNEL_DOWNLOADS)
            .setContentTitle(filename)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setSilent(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)

        if (progress >= 0) {
            builder.setProgress(100, progress, false)
        } else {
            builder.setProgress(0, 0, true)
        }

        notificationManager.notify(getNotificationId(downloadId), builder.build())
    }

    private fun showCompletedNotification(downloadId: String, filename: String) {
        val notification = NotificationCompat.Builder(this, RealDebridApp.CHANNEL_DOWNLOADS)
            .setContentTitle(filename)
            .setContentText("Download complete")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(getNotificationId(downloadId), notification)
    }

    private fun showFailedNotification(downloadId: String, filename: String, error: String) {
        val retryIntent = Intent(this, DownloadService::class.java).apply {
            action = ACTION_START
            putExtra(EXTRA_DOWNLOAD_ID, downloadId)
        }
        val retryPendingIntent = PendingIntent.getService(
            this, downloadId.hashCode() + 1, retryIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, RealDebridApp.CHANNEL_DOWNLOADS)
            .setContentTitle(filename)
            .setContentText("Failed: $error")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_menu_rotate, "Retry", retryPendingIntent)
            .build()

        notificationManager.notify(getNotificationId(downloadId), notification)
    }

    private fun cancelDownloadNotification(downloadId: String) {
        notificationManager.cancel(getNotificationId(downloadId))
    }

    private fun getNotificationId(downloadId: String): Int {
        return NOTIFICATION_ID_DOWNLOAD_BASE + downloadId.hashCode().and(0x7FFFFFFF) % 10000
    }

    override fun onDestroy() {
        super.onDestroy()
        activeDownloads.values.forEach {
            it.engine.cancel()
            it.job.cancel()
        }
        activeDownloads.clear()
        serviceScope.cancel()
    }

    companion object {
        const val ACTION_START = "com.realdebrid.downloader.START"
        const val ACTION_PAUSE = "com.realdebrid.downloader.PAUSE"
        const val ACTION_RESUME = "com.realdebrid.downloader.RESUME"
        const val ACTION_CANCEL = "com.realdebrid.downloader.CANCEL"
        const val ACTION_CANCEL_ALL = "com.realdebrid.downloader.CANCEL_ALL"
        const val EXTRA_DOWNLOAD_ID = "download_id"

        private const val NOTIFICATION_ID_SERVICE = 1
        private const val NOTIFICATION_ID_DOWNLOAD_BASE = 1000

        fun startDownload(context: Context, downloadId: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
            context.startForegroundService(intent)
        }

        fun pauseDownload(context: Context, downloadId: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_PAUSE
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
            context.startService(intent)
        }

        fun resumeDownload(context: Context, downloadId: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_RESUME
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
            context.startForegroundService(intent)
        }

        fun cancelDownload(context: Context, downloadId: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
            context.startService(intent)
        }
    }
}
