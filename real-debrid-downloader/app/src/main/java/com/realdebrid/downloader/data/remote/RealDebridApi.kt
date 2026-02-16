package com.realdebrid.downloader.data.remote

import com.realdebrid.downloader.data.model.Download
import com.realdebrid.downloader.data.model.TorrentInfo
import com.realdebrid.downloader.data.model.UnrestrictResponse
import com.realdebrid.downloader.data.model.User
import retrofit2.http.*

interface RealDebridApi {

    @GET("user")
    suspend fun getUser(): User

    @FormUrlEncoded
    @POST("unrestrict/link")
    suspend fun unrestrictLink(
        @Field("link") link: String,
        @Field("password") password: String? = null,
        @Field("remote") remote: Int = 0
    ): UnrestrictResponse

    @GET("downloads")
    suspend fun getDownloads(
        @Query("offset") offset: Int = 0,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50
    ): List<Download>

    @DELETE("downloads/delete/{id}")
    suspend fun deleteDownload(@Path("id") id: String)

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
    )

    @DELETE("torrents/delete/{id}")
    suspend fun deleteTorrent(@Path("id") id: String)

    @GET("torrents")
    suspend fun getTorrents(
        @Query("offset") offset: Int = 0,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50,
        @Query("filter") filter: String? = null
    ): List<TorrentInfo>
}

@kotlinx.serialization.Serializable
data class AddMagnetResponse(
    val id: String,
    val uri: String
)
