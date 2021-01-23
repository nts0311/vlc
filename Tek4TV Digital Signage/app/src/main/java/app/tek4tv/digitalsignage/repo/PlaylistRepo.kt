package app.tek4tv.digitalsignage.repo

import android.content.Context
import android.util.Log
import app.tek4tv.digitalsignage.model.MediaItem
import app.tek4tv.digitalsignage.network.PlaylistService
import app.tek4tv.digitalsignage.utils.*
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepo @Inject constructor(
    private val playlistService: PlaylistService,
    moshi: Moshi,
    @ApplicationContext private val appContext: Context
) {

    private val type = Types.newParameterizedType(List::class.java, MediaItem::class.java)
    private val jsonAdapter: JsonAdapter<List<MediaItem>> = moshi.adapter(type)

    var body = mapOf(
        "IMEI" to Utils.getDeviceId(appContext)!!
    )

    var broadcastList = listOf<MediaItem>()
    var scheduledList = mutableMapOf<String, List<MediaItem>>()
    var unscheduledList = mutableListOf<MediaItem>()

    suspend fun getBroadcastList(storagePath: String, needUpdate: Boolean): List<MediaItem> {

        broadcastList = if (needUpdate) {
            updatePlaylist(storagePath)
        } else {
            val result = if (isFileExisted(storagePath, PLAYLIST_FILE_NAME)) {
                Log.d("readplaylist", "local")
                readPlaylistFromFile(storagePath)
            } else {
                updatePlaylist(storagePath)
            }
            result
        }

        filterMedia(broadcastList)

        return broadcastList
    }

    private fun filterMedia(mediaList: List<MediaItem>) {
        val dividerIndices = mediaList.indices.filter {
            mediaList[it].path == "start" || mediaList[it].path == "end"
        }.chunked(2)

        val scheduledIndex = mutableListOf<Int>()

        dividerIndices.forEach {
            if (it.size == 2) {
                val startIndex = it[0]
                val endIndex = it[1]

                val key = "${mediaList[startIndex].fixTime}-${mediaList[endIndex].fixTime}"
                scheduledList[key] = mediaList.subList(startIndex + 1, endIndex)

                //adding the index of scheduled media
                scheduledIndex.addAll((startIndex..endIndex))
            }
        }

        mediaList.indices.filter { !scheduledIndex.contains(it) }.forEach {
            unscheduledList.add(mediaList[it])
        }
    }

    suspend fun updatePlaylist(storagePath: String): List<MediaItem> {
        var result = listOf<MediaItem>()

        try {
            val res = playlistService.getPlaylist(body)
            if (res.isSuccessful) {
                val resBody = res.body()
                if (resBody != null && resBody.playlists != null) {
                    result = resBody.playlists!!
                    savePlaylistToFile(result, storagePath)
                }
            }
        } catch (e: Exception) {
            Log.e("yee", e.toString())
        }

        return result
    }

    suspend fun savePlaylistToFile(playlist: List<MediaItem>, storagePath: String) {
        withContext(Dispatchers.IO)
        {


            val filePath = "$storagePath/$PLAYLIST_FILE_NAME"
            val json = jsonAdapter.toJson(playlist)
            Log.d("writefile", json)

            try {
                writeFile(filePath, json)
            } catch (e: IOException) {
                Log.e(LOG_TAG, "error writing playlist!!!")
            }
        }
    }

    private suspend fun readPlaylistFromFile(storagePath: String): List<MediaItem> {

        var result = listOf<MediaItem>()

        withContext(Dispatchers.IO)
        {
            val filePath = "$storagePath/$PLAYLIST_FILE_NAME"

            try {
                var contentFromFile = readFile(filePath)
                result = jsonAdapter.fromJson(contentFromFile) ?: listOf()
            } catch (e: IOException) {
                Log.e(LOG_TAG, "error reading playlist!!!")
            }

        }
        return result
    }

    suspend fun getAudioList(url: String): List<String> {
        var result = listOf<String>()
        try {
            val rep = playlistService.getAudioList(url)

            if (rep.isSuccessful && !rep.body().isNullOrEmpty())
                result = rep.body()!!
        } catch (e: Exception) {
            Log.e(LOG_TAG, e.message)
            e.printStackTrace()
        }

        return result
    }

    companion object {
        private const val LOG_TAG = "PlaylistRepo"
    }
}