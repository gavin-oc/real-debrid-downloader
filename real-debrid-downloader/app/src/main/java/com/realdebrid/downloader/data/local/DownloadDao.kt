package com.realdebrid.downloader.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: DownloadEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(downloads: List<DownloadEntity>)

    @Update
    suspend fun update(download: DownloadEntity)

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun getAllFlow(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    suspend fun getAll(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getDownloadById(id: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE id = :id")
    fun getDownloadByIdFlow(id: String): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads WHERE status = :status ORDER BY createdAt ASC")
    suspend fun getByStatus(status: DownloadStatus): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE status IN (:statuses) ORDER BY createdAt ASC")
    suspend fun getByStatuses(statuses: List<DownloadStatus>): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE status = :status ORDER BY createdAt ASC")
    fun getByStatusFlow(status: DownloadStatus): Flow<List<DownloadEntity>>

    @Query("""
        UPDATE downloads
        SET status = :status,
            progress = :progress,
            bytesDownloaded = :bytesDownloaded,
            updatedAt = :updatedAt
        WHERE id = :id AND status = 'DOWNLOADING'
    """)
    suspend fun updateProgress(
        id: String,
        status: DownloadStatus,
        progress: Int,
        bytesDownloaded: Long,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("""
        UPDATE downloads
        SET status = :status,
            progress = 100,
            localPath = :localPath,
            completedAt = :completedAt,
            updatedAt = :updatedAt
        WHERE id = :id
    """)
    suspend fun markCompleted(
        id: String,
        status: DownloadStatus = DownloadStatus.COMPLETED,
        localPath: String,
        completedAt: Long = System.currentTimeMillis(),
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("""
        UPDATE downloads
        SET status = :status,
            errorMessage = :errorMessage,
            retryCount = retryCount + 1,
            updatedAt = :updatedAt
        WHERE id = :id
    """)
    suspend fun markFailed(
        id: String,
        status: DownloadStatus = DownloadStatus.FAILED,
        errorMessage: String?,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE downloads SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(
        id: String,
        status: DownloadStatus,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE downloads SET fileSize = :fileSize, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateFileSize(
        id: String,
        fileSize: Long,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteDownloadById(id: String)

    @Query("DELETE FROM downloads WHERE status = :status")
    suspend fun deleteByStatus(status: DownloadStatus)

    @Query("DELETE FROM downloads")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM downloads WHERE status = :status")
    suspend fun countByStatus(status: DownloadStatus): Int

    @Query("SELECT COUNT(*) FROM downloads")
    suspend fun count(): Int
}
