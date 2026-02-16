package com.realdebrid.downloader.download

sealed class DownloadState {
    data object Idle : DownloadState()
    data object Queued : DownloadState()
    data class Downloading(
        val progress: Int,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val speed: Long
    ) : DownloadState()
    data object Paused : DownloadState()
    data object Completed : DownloadState()
    data class Failed(val error: String) : DownloadState()
}

fun DownloadState.Downloading.formatProgress(): String {
    return "$progress%"
}

fun DownloadState.Downloading.formatSpeed(): String {
    return when {
        speed >= 1_048_576 -> "%.1f MB/s".format(speed / 1_048_576.0)
        speed >= 1024 -> "%.1f KB/s".format(speed / 1024.0)
        else -> "$speed B/s"
    }
}

fun DownloadState.Downloading.formatSize(): String {
    val downloaded = formatBytes(bytesDownloaded)
    val total = formatBytes(totalBytes)
    return "$downloaded / $total"
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824 -> "%.2f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> "%.2f MB".format(bytes / 1_048_576.0)
        bytes >= 1024 -> "%.2f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}
