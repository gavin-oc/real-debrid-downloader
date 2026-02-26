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
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    private val api: RealDebridApi,
    private val downloadDao: DownloadDao
) {
    // Remote API operations
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
    fun getAllDownloadsFlow(): Flow<List<DownloadEntity>> = downloadDao.getAllFlow()

    suspend fun getAllDownloads(): List<DownloadEntity> = downloadDao.getAll()

    fun getDownloadByIdFlow(id: String): Flow<DownloadEntity?> = downloadDao.getDownloadByIdFlow(id)

    suspend fun getDownloadById(id: String): DownloadEntity? = downloadDao.getDownloadById(id)

    fun getDownloadsByStatusFlow(status: DownloadStatus): Flow<List<DownloadEntity>> =
        downloadDao.getByStatusFlow(status)

    suspend fun getDownloadsByStatus(status: DownloadStatus): List<DownloadEntity> =
        downloadDao.getByStatus(status)

    suspend fun getDownloadsByStatuses(statuses: List<DownloadStatus>): List<DownloadEntity> =
        downloadDao.getByStatuses(statuses)

    suspend fun insertDownload(download: DownloadEntity) = downloadDao.insert(download)

    suspend fun updateDownload(download: DownloadEntity) = downloadDao.update(download)

    suspend fun deleteDownloadById(id: String) = downloadDao.deleteDownloadById(id)

    suspend fun updateProgress(
        id: String,
        status: DownloadStatus,
        progress: Int,
        bytesDownloaded: Long
    ) = downloadDao.updateProgress(id, status, progress, bytesDownloaded)

    suspend fun updateStatus(id: String, status: DownloadStatus) =
        downloadDao.updateStatus(id, status)

    suspend fun markCompleted(id: String, localPath: String) =
        downloadDao.markCompleted(id, localPath = localPath)

    suspend fun markFailed(id: String, errorMessage: String?) =
        downloadDao.markFailed(id, errorMessage = errorMessage)

    // Helper to create download entity from unrestrict response
    suspend fun queueDownload(
        originalUrl: String,
        unrestrictResponse: UnrestrictResponse
    ): DownloadEntity {
        val entity = DownloadEntity(
            id = UUID.randomUUID().toString(),
            filename = unrestrictResponse.filename,
            originalUrl = originalUrl,
            downloadUrl = unrestrictResponse.download,
            mimeType = unrestrictResponse.mimeType ?: "",
            fileSize = unrestrictResponse.filesize,
            status = DownloadStatus.QUEUED
        )
        downloadDao.insert(entity)
        return entity
    }

    suspend fun countByStatus(status: DownloadStatus): Int = downloadDao.countByStatus(status)

    suspend fun getQueuedDownloads(): List<DownloadEntity> =
        downloadDao.getByStatus(DownloadStatus.QUEUED)

    suspend fun getPendingDownloads(): List<DownloadEntity> =
        downloadDao.getByStatuses(listOf(DownloadStatus.QUEUED, DownloadStatus.PAUSED))
}
