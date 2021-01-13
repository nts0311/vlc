package app.tek4tv.digitalsignage.network

import app.tek4tv.digitalsignage.model.ResponseList
import retrofit2.Response
import retrofit2.http.*


interface PlaylistService {
    @Headers("Content-Type: application/json")
    @POST("api/device/imei")
    suspend fun getPlaylist(@Body body: Map<String, String>): Response<ResponseList>

    @GET
    suspend fun getAudioList(@Url url: String): Response<List<String>>
}