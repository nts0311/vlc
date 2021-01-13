package app.tek4tv.digitalsignage.model

import android.net.Uri
import android.util.Log
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import java.io.File
import java.io.Serializable

@JsonClass(generateAdapter = true)
data class MediaItem(
    @Json(name = "Index")
    var index: String? = null,

    @Json(name = "Name")
    var name: String? = null,

    @Json(name = "ID")
    var id: Long = 0,

    @Json(name = "Duration")
    var duration: String? = null,

    @Json(name = "Path")
    var path: String = "",

    @Json(name = "Start")
    var start: String? = null,

    @Json(name = "End")
    var end: String? = null,

    @Json(name = "Edit")
    var edit: Boolean = false,

    @Json(name = "Category")
    var category: Category? = null,

    var isCheck: Boolean = false,

    @Json(name = "FixTime")
    var fixTime: String = "",

    var played: Boolean = false,

    var lastPlay: Long = -1L
) : Serializable {

    var pathBackup: String = ""
        set(value) {
            if (value.startsWith("http"))
                field = value
        }

    init {
        pathBackup = path
    }

    fun getFileName() = if (path.startsWith("http")) File(Uri.parse(path).path).name
    else File("file://$path").name

    fun getMediaType(): MediaType {
        val mediaName = getFileName()

        val audioExt = listOf(".mp3")
        val videoExt = listOf(".mp4")
        val imageExt = listOf(".jpg", ".png", ".gif")

        val isAudio = audioExt.any { mediaName.endsWith(it) }
        val isVideo = videoExt.any { mediaName.endsWith(it) }
        val isImage = imageExt.any { mediaName.endsWith(it) }

        return when {
            isAudio -> MediaType.AUDIO
            isVideo -> MediaType.VIDEO
            isImage -> MediaType.IMAGE
            else -> MediaType.UNKNOWN
        }
    }

    fun getVlcMedia(mLibVLC: LibVLC): Media {
        return if (path.isNotEmpty() && File(path).exists()) {
            Log.d("link", "local: $path")
            Media(mLibVLC, Uri.parse("file://$path"))
        } else {
            Log.d("link", "online: $pathBackup")
            Media(mLibVLC, Uri.parse(pathBackup))
        }
    }
}

enum class MediaType {
    IMAGE,
    VIDEO,
    AUDIO,
    UNKNOWN
}


fun getDurationInSecond(duration: String): Long {
    return try {
        (duration.split(":").mapIndexed { index, s ->
            when (index) {
                0 -> s.toInt() * 3600
                1 -> s.toInt() * 60
                2 -> s.toInt()
                else -> 0
            }
        }
            .fold(0L) { acc, it -> acc + it })
    } catch (e: Exception) {
        0
    }
}

@JsonClass(generateAdapter = true)
data class Category(

    @Json(name = "ID")
    var id: Long = 0,

    @Json(name = "Name")
    var name: String? = null
)


class ResponseList {
    @Json(name = "Playlist")
    var playlists: List<MediaItem>? = null
}
