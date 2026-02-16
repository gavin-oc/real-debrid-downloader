package com.realdebrid.downloader.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey
    val id: String,
    val url: String,
    val filename: String,
    val bytesDownloaded: Long = 0
)
