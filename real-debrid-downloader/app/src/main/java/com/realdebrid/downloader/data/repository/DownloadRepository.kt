package com.realdebrid.downloader.data.repository

import com.realdebrid.downloader.data.local.DownloadDao
import com.realdebrid.downloader.data.local.DownloadEntity
import com.realdebrid.downloader.data.local.DownloadStatus
import com.realdebrid.downloader.data.model.Download
import com.realdebrid.downloader.data.model.TorrentInfo
import com.realdebrid.downloader.data.model.UnrestrictResponse
import com.realdebrid.downloader.data.model.User
import com.realdebrid.downloader.data.remote.RealDebridApi
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    private val api: RealDebridApi,
    private val downloadDao: DownloadDao
) {
    suspend fun getUser(): Result<User> = runCatching {
        api.getUser()
    }

    suspend fun unrestrictLink(link: String, password: String? = null): Result<UnrestrictResponse> = runCatching {
        api.unrestrictLink(link, password)
    }

    suspend fun getDownloads(page: Int = 1, limit: Int = 50): Result<List<Download>> = runCatching {
        api.getDownloads(page = page, limit = limit)
    }

    suspend fun deleteDownload(id: String): Result<Unit> = runCatching {
        api.deleteDownload(id)
    }

    suspend fun addMagnet(magnet: String): Result<String> = runCatching {
        val response = api.addMagnet(magnet)
        response.id
    }

    suspend fun getTorrentInfo(id: String): Result<TorrentInfo> = runCatching {
        api.getTorrentInfo(id)
    }

    suspend fun selectFiles(id: String, files: String = "all"): Result<Unit> = runCatching {
        api.selectFiles(id, files)
    }

    suspend fun deleteTorrent(id: String): Result<Unit> = runCatching {
        api.deleteTorrent(id)
    }

    suspend fun getTorrents(page: Int = 1, limit: Int = 50): Result<List<TorrentInfo>> = runCatching {
        api.getTorrents(page = page, limit = limit)
    }

    // Local database operations
    fun getLocalDownloads(): Flow<List<DownloadEntity>> = downloadDao.getAllDownloads()

    fun getLocalDownloadsByStatus(status: DownloadStatus): Flow<List<DownloadEntity>> =
        downloadDao.getDownloadsByStatus(status)

    suspend fun saveLocalDownload(download: DownloadEntity) = downloadDao.insert(download)

    suspend fun updateLocalDownload(download: DownloadEntity) = downloadDao.update(download)

    suspend fun deleteLocalDownload(id: String) = downloadDao.deleteById(id)

    suspend fun updateProgress(id: String, status: DownloadStatus, progress: Int, speed: Long = 0, downloadedBytes: Long = 0) =
        downloadDao.updateProgress(id, progress, status, speed, downloadedBytes)

    suspend fun markCompleted(id: String, localPath: String) =
        downloadDao.markCompleted(id = id, localPath = localPath)

    suspend fun markFailed(id: String, error: String?) =
        downloadDao.markFailed(id = id, errorMessage = error)
}
