package com.realdebrid.downloader.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: Int,
    val username: String,
    val email: String,
    val points: Int = 0,
    val locale: String = "en",
    val avatar: String = "",
    val type: String = "free",
    val premium: Int = 0,
    val expiration: String = ""
)

@Serializable
data class UnrestrictResponse(
    val id: String,
    val filename: String,
    val mimeType: String = "",
    val filesize: Long,
    val link: String,
    val host: String,
    val chunks: Int = 1,
    val crc: Int = 0,
    val download: String,
    val streamable: Int = 0
)

@Serializable
data class Download(
    val id: String,
    val filename: String,
    val mimeType: String = "",
    val filesize: Long,
    val link: String,
    val host: String,
    val chunks: Int = 1,
    val download: String,
    val generated: String
)

@Serializable
data class TorrentInfo(
    val id: String,
    val filename: String,
    val hash: String = "",
    val bytes: Long = 0,
    val host: String = "",
    val split: Int = 0,
    val progress: Int = 0,
    val status: String,
    val added: String = "",
    val links: List<String> = emptyList(),
    val ended: String? = null,
    val speed: Long? = null,
    val seeders: Int? = null,
    val files: List<TorrentFile>? = null,
    @SerialName("original_filename")
    val originalFilename: String? = null
)

@Serializable
data class TorrentFile(
    val id: Int,
    val path: String,
    val bytes: Long,
    val selected: Int
)

enum class TorrentStatus(val value: String) {
    MAGNET_ERROR("magnet_error"),
    MAGNET_CONVERSION("magnet_conversion"),
    WAITING_FILES_SELECTION("waiting_files_selection"),
    QUEUED("queued"),
    DOWNLOADING("downloading"),
    DOWNLOADED("downloaded"),
    ERROR("error"),
    VIRUS("virus"),
    COMPRESSING("compressing"),
    UPLOADING("uploading"),
    DEAD("dead")
}
