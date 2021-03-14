package app.tek4tv.digitalsignage.model

/*import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName*/
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class PingHubRequest {
    @Json(name = "ConnectionId")
   
     var connectionId: String? = null

    @Json(name = "IMEI")
   
     var imei: String? = null

    @Json(name = "StartTime")
   
     var startTine: String? = null

    @Json(name = "Status")
   
     var status: String? = null

    @Json(name = "Volume")
   
     var volume: String? = null

    @Json(name = "Message")
   
     var message: String? = null

    @Json(name = "Video")
   
     var video: String? = null
}