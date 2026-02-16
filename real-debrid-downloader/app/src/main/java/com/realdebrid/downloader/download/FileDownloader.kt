package com.realdebrid.downloader.download

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

class FileDownloader @Inject constructor(
    private val client: OkHttpClient
) {
    private val isPaused = AtomicBoolean(false)
    private val isCancelled = AtomicBoolean(false)
    private val downloadedBytes = AtomicLong(0)

    fun download(
        url: String,
        outputFile: File,
        onProgress: (bytesDownloaded: Long, totalBytes: Long, speed: Long) -> Unit,
        onComplete: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        try {
            val existingBytes = if (outputFile.exists()) outputFile.length() else 0L
            downloadedBytes.set(existingBytes)

            val request = Request.Builder()
                .url(url)
                .apply {
                    if (existingBytes > 0) {
                        addHeader("Range", "bytes=$existingBytes-")
                    }
                }
                .build()

            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful && response.code != 206) {
                onError(Exception("HTTP ${response.code}: ${response.message}"))
                return
            }

            val contentLength = response.header("Content-Length")?.toLongOrNull() ?: -1L
            val totalBytes = if (response.code == 206) {
                response.header("Content-Range")?.let { range ->
                    range.substringAfter("/").toLongOrNull()
                } ?: (contentLength + existingBytes)
            } else {
                contentLength
            }

            val body = response.body ?: run {
                onError(Exception("Empty response body"))
                return
            }

            val raf = RandomAccessFile(outputFile, "rw")
            raf.seek(existingBytes)

            body.byteStream().use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                var lastProgressTime = System.currentTimeMillis()
                var bytesAtLastProgress = existingBytes

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    if (isCancelled.get()) {
                        raf.close()
                        return
                    }

                    while (isPaused.get() && !isCancelled.get()) {
                        Thread.sleep(100)
                    }

                    if (isCancelled.get()) {
                        raf.close()
                        return
                    }

                    raf.write(buffer, 0, bytesRead)
                    downloadedBytes.addAndGet(bytesRead.toLong())

                    val now = System.currentTimeMillis()
                    val elapsed = now - lastProgressTime
                    if (elapsed >= PROGRESS_INTERVAL_MS) {
                        val currentBytes = downloadedBytes.get()
                        val speed = ((currentBytes - bytesAtLastProgress) * 1000) / elapsed
                        onProgress(currentBytes, totalBytes, speed)
                        lastProgressTime = now
                        bytesAtLastProgress = currentBytes
                    }
                }

                raf.close()

                if (!isCancelled.get()) {
                    onProgress(downloadedBytes.get(), totalBytes, 0)
                    onComplete()
                }
            }
        } catch (e: Exception) {
            if (!isCancelled.get()) {
                onError(e)
            }
        }
    }

    fun pause() {
        isPaused.set(true)
    }

    fun resume() {
        isPaused.set(false)
    }

    fun cancel() {
        isCancelled.set(true)
        isPaused.set(false)
    }

    fun getDownloadedBytes(): Long = downloadedBytes.get()

    class Factory @Inject constructor(private val client: OkHttpClient) {
        fun create(): FileDownloader = FileDownloader(client)
    }

    companion object {
        private const val BUFFER_SIZE = 8 * 1024
        private const val PROGRESS_INTERVAL_MS = 250
    }
}
