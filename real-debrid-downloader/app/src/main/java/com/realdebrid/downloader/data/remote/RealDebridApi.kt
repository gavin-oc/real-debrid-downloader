package com.realdebrid.downloader.data.remote

import com.realdebrid.downloader.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface RealDebridApi {

    // User
    @GET("user")
    suspend fun getUser(): User

    // Unrestrict
    @FormUrlEncoded
    @POST("unrestrict/link")
    suspend fun unrestrictLink(
        @Field("link") link: String,
        @Field("password") password: String? = null,
        @Field("remote") remote: Int = 0
    ): UnrestrictResponse

    @FormUrlEncoded
    @POST("unrestrict/check")
    suspend fun checkLink(
        @Field("link") link: String,
        @Field("password") password: String? = null
    ): LinkCheckResponse

    @GET("unrestrict/containerFile/{id}")
    suspend fun getContainerFile(@Path("id") id: String): List<String>

    // Downloads history
    @GET("downloads")
    suspend fun getDownloads(
        @Query("offset") offset: Int = 0,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50
    ): List<Download>

    @DELETE("downloads/delete/{id}")
    suspend fun deleteDownload(@Path("id") id: String): Response<Unit>

    // Torrents
    @FormUrlEncoded
    @POST("torrents/addMagnet")
    suspend fun addMagnet(@Field("magnet") magnet: String): AddMagnetResponse

    @Multipart
    @PUT("torrents/addTorrent")
    suspend fun addTorrent(@Part torrent: okhttp3.MultipartBody.Part): AddMagnetResponse

    @GET("torrents/info/{id}")
    suspend fun getTorrentInfo(@Path("id") id: String): TorrentInfo

    @FormUrlEncoded
    @POST("torrents/selectFiles/{id}")
    suspend fun selectFiles(
        @Path("id") id: String,
        @Field("files") files: String = "all"
    ): Response<Unit>

    @DELETE("torrents/delete/{id}")
    suspend fun deleteTorrent(@Path("id") id: String): Response<Unit>

    @GET("torrents")
    suspend fun getTorrents(
        @Query("offset") offset: Int = 0,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50,
        @Query("filter") filter: String? = null
    ): List<TorrentInfo>

    @GET("torrents/availableHosts")
    suspend fun getAvailableHosts(): List<AvailableHost>

    @GET("torrents/instantAvailability/{hash}")
    suspend fun checkInstantAvailability(@Path("hash") hash: String): Map<String, InstantAvailability>

    // Hosts
    @GET("hosts")
    suspend fun getHosts(): Map<String, HostInfo>

    @GET("hosts/status")
    suspend fun getHostsStatus(): Map<String, HostStatus>

    @GET("hosts/regex")
    suspend fun getHostsRegex(): List<String>

    @GET("hosts/domains")
    suspend fun getHostsDomains(): List<String>
}

@kotlinx.serialization.Serializable
data class AddMagnetResponse(
    val id: String,
    val uri: String
)

@kotlinx.serialization.Serializable
data class LinkCheckResponse(
    val host: String,
    val link: String,
    val filename: String,
    val filesize: Long,
    val supported: Int
)

@kotlinx.serialization.Serializable
data class AvailableHost(
    val host: String,
    val max_file_size: Long = 0
)

@kotlinx.serialization.Serializable
data class InstantAvailability(
    val rd: List<Map<String, InstantFile>>? = null
)

@kotlinx.serialization.Serializable
data class InstantFile(
    val filename: String,
    val filesize: Long
)

@kotlinx.serialization.Serializable
data class HostInfo(
    val id: String,
    val name: String,
    val image: String = "",
    val image_big: String = ""
)

@kotlinx.serialization.Serializable
data class HostStatus(
    val id: String,
    val name: String,
    val image: String = "",
    val image_big: String = "",
    val supported: Int = 0,
    val status: String = "",
    val check_time: String = ""
)
