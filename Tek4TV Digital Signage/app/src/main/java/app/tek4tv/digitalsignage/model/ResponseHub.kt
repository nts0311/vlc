package app.tek4tv.digitalsignage.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.io.Serializable

@JsonClass(generateAdapter = true)
class ResponseHub : Serializable {
    @Json(name = "IMEI")
    var imei: String? = null

    @Json(name = "Message")
    var message: String? = null

    @Json(name = "Volume")
    var volume: String? = null

    @Json(name = "Start")
    var start: String? = null
}
