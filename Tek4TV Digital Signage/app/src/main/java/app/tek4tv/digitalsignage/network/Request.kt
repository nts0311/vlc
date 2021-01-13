package app.tek4tv.digitalsignage.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Request(@Json(name = "IMEI") val IMEI: String)