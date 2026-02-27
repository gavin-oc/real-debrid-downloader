package com.realdebrid.downloader.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey
    val id: String,
    val filename: String,
    val originalUrl: String,           // Original link/magnet
    val downloadUrl: String,           // Unrestricted direct URL from RD
    val mimeType: String = "",
    val fileSize: Long = 0,            // Total bytes
    val bytesDownloaded: Long = 0,
    val progress: Int = 0,             // 0-100
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val localPath: String? = null,     // Final file path when completed
    val errorMessage: String? = null,
    val retryCount: Int = 0,
    val rdDownloadId: String? = null,   // RD download ID for re-unrestricting expired CDN URLs
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

class Converters {
    @TypeConverter
    fun fromDownloadStatus(status: DownloadStatus): String = status.name

    @TypeConverter
    fun toDownloadStatus(value: String): DownloadStatus =
        DownloadStatus.valueOf(value)
}
