package app.tek4tv.digitalsignage.model

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.io.Serializable

@JsonClass(generateAdapter = true)
class ReponseHub : Serializable {
    @Json(name = "IMEI")
    var imei: String? = null

    @Json(name = "Message")
    var message: String? = null

    @Json(name = "Volume")
    var volume: String? = null
}
