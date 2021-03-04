package app.tek4tv.digitalsignage.repo

import android.content.Context
import android.util.Log
import app.tek4tv.digitalsignage.model.MediaItem
import app.tek4tv.digitalsignage.model.MediaType
import app.tek4tv.digitalsignage.network.PlaylistService
import app.tek4tv.digitalsignage.utils.*
import com.downloader.Error
import com.downloader.OnDownloadListener
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepo @Inject constructor(
    private val playlistService: PlaylistService,
    moshi: Moshi,
    @ApplicationContext private val appContext: Context
) : BaseRepo<MediaItem>(fileStoragePath = "${appContext.filesDir.path}/$PLAYLIST_FILE_NAME") {

    override val jsonAdapter: JsonAdapter<List<MediaItem>> =
        moshi.adapter(Types.newParameterizedType(List::class.java, MediaItem::class.java))

    var body = mapOf("IMEI" to Utils.getDeviceId(appContext)!!)

    var broadcastList = mutableListOf<MediaItem>()
    var scheduledList = LinkedHashMap<String, List<MediaItem>>()
    var dividers = LinkedHashMap<String, List<MediaItem>>()
    var unscheduledList = mutableListOf<MediaItem>()

    private val downloadHelper = DownloadHelper.getInstance(coroutineScope)

    private val mediaStoragePath = appContext.filesDir.path

    private val imageResize = ImageResize(coroutineScope)

    suspend fun getBroadcastList(needUpdate: Boolean): List<MediaItem> {
        scheduledList.clear()
        unscheduledList.clear()

        val res = if (needUpdate) {
            updatePlaylist()
        } else {
            val result = if (isFileExisted(mediaStoragePath, PLAYLIST_FILE_NAME)) {
                Log.d("readplaylist", "local")
                readItemsFromFile()
            } else {
                updatePlaylist()
            }
            result
        }

        broadcastList.clear()
        broadcastList.addAll(res)

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

                dividers[key] = listOf(broadcastList[startIndex], broadcastList[endIndex])

                //adding the index of scheduled media
                scheduledIndex.addAll((startIndex..endIndex))
            }
        }

        mediaList.indices.filter { !scheduledIndex.contains(it) }.forEach {
            unscheduledList.add(mediaList[it])
        }
    }

    suspend fun updatePlaylist(): List<MediaItem> {
        var result = listOf<MediaItem>()

        try {
            val res = playlistService.getPlaylist(body)
            if (res.isSuccessful) {
                val resBody = res.body()
                if (resBody != null && resBody.playlists != null) {
                    result = resBody.playlists!!
                    saveItemsToFile(result)
                }
            }
        } catch (e: Exception) {
            Log.e("yee", e.toString())
        }

        return result
    }

    fun startDownloadMedia() {
        if (broadcastList.isNullOrEmpty()) return

        broadcastList.forEach {
            downloadAMediaItem(it)
        }
    }

    fun downloadAMediaItem(it: MediaItem) {
        val url = it.path
        if (url.isEmpty() || !url.startsWith("http")) return

        val fileName = getFileNameFromUrl(url)

        if (isFileExisted(mediaStoragePath, fileName)) {
            it.path = "$mediaStoragePath/$fileName"
            coroutineScope.launch {
                saveItemsToFile(broadcastList)
            }
            return
        }


        downloadHelper.addToQueue(DownloadItem().apply {
            itDownloadUrl = url
            itStoragePath = mediaStoragePath
            itFileName = fileName
            priority = if(it.getMediaType() == MediaType.IMAGE) 0 else 1
            downloadListener = object : OnDownloadListener {
                override fun onDownloadComplete() {
                    it.pathBackup = it.path
                    it.path = "$mediaStoragePath/$fileName"

                    coroutineScope.launch {
                        saveItemsToFile(broadcastList)
                    }

                    Log.d("downloadcomplete", it.path)

                    if (it.getMediaType() == MediaType.IMAGE) {
                        imageResize.addToQueue(it.path)
                    }
                }

                override fun onError(error: Error?) {
                    Log.d("downloaderror", it.path)
                }
            }
        })
    }
}