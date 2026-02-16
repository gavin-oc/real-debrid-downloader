package com.realdebrid.downloader.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.realdebrid.downloader.R
import com.realdebrid.downloader.RealDebridApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DownloadService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startDownload(intent)
            ACTION_PAUSE -> pauseDownload(intent)
            ACTION_CANCEL -> cancelDownload(intent)
        }
        return START_NOT_STICKY
    }

    private fun startDownload(intent: Intent) {
        val downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID) ?: return
        val url = intent.getStringExtra(EXTRA_URL) ?: return
        val filename = intent.getStringExtra(EXTRA_FILENAME) ?: "Download"

        val notification = NotificationCompat.Builder(this, RealDebridApp.CHANNEL_DOWNLOADS)
            .setContentTitle(filename)
            .setContentText("Downloading...")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, 0, false)
            .setOngoing(true)
            .build()

        startForeground(downloadId.hashCode(), notification)
        
        // TODO: Implement actual download logic with OkHttp
    }

    private fun pauseDownload(intent: Intent) {
        val downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID) ?: return
        // TODO: Pause download
    }

    private fun cancelDownload(intent: Intent) {
        val downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID) ?: return
        // TODO: Cancel download
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val ACTION_START = "com.realdebrid.downloader.START"
        const val ACTION_PAUSE = "com.realdebrid.downloader.PAUSE"
        const val ACTION_CANCEL = "com.realdebrid.downloader.CANCEL"
        const val EXTRA_DOWNLOAD_ID = "download_id"
        const val EXTRA_URL = "url"
        const val EXTRA_FILENAME = "filename"
    }
}
