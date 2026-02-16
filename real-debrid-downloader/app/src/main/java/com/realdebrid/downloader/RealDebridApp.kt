package com.realdebrid.downloader

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class RealDebridApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val downloadChannel = NotificationChannel(
            CHANNEL_DOWNLOADS,
            "Downloads",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Download progress notifications"
        }

        val completedChannel = NotificationChannel(
            CHANNEL_COMPLETED,
            "Completed",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Download completed notifications"
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannels(listOf(downloadChannel, completedChannel))
    }

    companion object {
        const val CHANNEL_DOWNLOADS = "downloads"
        const val CHANNEL_COMPLETED = "completed"
    }
}
