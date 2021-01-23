package app.tek4tv.digitalsignage.model

import com.squareup.moshi.Json

data class DirectMessage(
    @Json(name = "IMEI")
    var imei: String,

    @Json(name = "Message")
    var message: String
)
