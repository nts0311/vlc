package app.tek4tv.digitalsignage.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class Video(
    @Json(name = "Index") var index: String, @Json(
        name = "Time") var time: String,

    @Json(name = "MediaName") var mediaName: String = "",

    @Json(name = "AudioName") var audioName: String = " "
)