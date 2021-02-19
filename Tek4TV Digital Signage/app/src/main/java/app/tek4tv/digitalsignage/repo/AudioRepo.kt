package app.tek4tv.digitalsignage.repo

import android.content.Context
import android.net.Uri
import android.util.Log
import app.tek4tv.digitalsignage.network.PlaylistService
import app.tek4tv.digitalsignage.utils.*
import com.downloader.Error
import com.downloader.OnDownloadListener
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioRepo @Inject constructor(
    private val playlistService: PlaylistService,
    moshi: Moshi,
    @ApplicationContext private val appContext: Context
) : BaseRepo<String>(fileStoragePath = "${appContext.filesDir.path}/$AUDIO_LIST_FILE_NAME") {
    private val downloadHelper: DownloadHelper

    override val jsonAdapter: JsonAdapter<List<String>> =
        moshi.adapter(Types.newParameterizedType(List::class.java, String::class.java))

    var audioUrls = mutableListOf<String>()
    var audioFileUri = mutableListOf<Uri>()

    val audioFolderPath = "${appContext.filesDir.path}/$AUDIO_FOLDER_NAME"

    private var downloadAudioJob: Job? = null

    var isDownloadingAudio = false

    init {
        audioFileUri = getAllFileNameInFolder(audioFolderPath).map {
            filenameToUri(it)
        }.shuffled() as MutableList<Uri>
        downloadHelper = DownloadHelper.getInstance(coroutineScope)
    }

    suspend fun getAudioUrls(url: String, needUpdate: Boolean): List<String> {
        audioUrls = if (needUpdate) {
            audioUrls.clear()
            audioUrls.addAll(fetchAudioUrls(url))
            saveItemsToFile(audioUrls)
            downloadAudio()
            audioUrls
        } else {
            readItemsFromFile() as MutableList<String>
        }

        return audioUrls
    }

    private suspend fun fetchAudioUrls(url: String): List<String> {
        var result = listOf<String>()
        try {
            val rep = playlistService.getAudioList(url)
            if (rep.isSuccessful && !rep.body().isNullOrEmpty()) result = rep.body()!!
        } catch (e: Exception) {
            Log.e(LOG_TAG, e.message)
            e.printStackTrace()
        }

        return result
    }

    private fun filenameToUri(filename: String) = Uri.parse("file://$audioFolderPath/$filename")

    fun downloadAudio() {

        downloadAudioJob = coroutineScope.launch(Dispatchers.Default) {
            isDownloadingAudio = true
            if (!isFolderExisted(audioFolderPath)) createFolder(audioFolderPath)

            if (audioUrls.isEmpty()) return@launch

            audioUrls.forEach { url ->
                downloadAnAudio(url)
            }

            isDownloadingAudio = false
        }
    }

    fun downloadAnAudio(url: String) {
        if (url.isEmpty() || !url.startsWith("http")) return

        //if (url.isNotEmpty() && url.startsWith("http")) {
        val fileName = getFileNameFromUrl(url)
        if (isFileExisted(audioFolderPath, fileName)) return

        downloadHelper.addToQueue(DownloadItem().apply {
            itDownloadUrl = url
            itStoragePath = audioFolderPath
            itFileName = fileName
            downloadListener = object : OnDownloadListener {
                override fun onDownloadComplete() {
                    audioFileUri.add(filenameToUri(fileName))
                    Log.d("downloadcomplete", url)
                }

                override fun onError(error: Error?) {
                    Log.d("downloaderror", url)
                }
            }
        })
    }

    companion object {
        const val LOG_TAG = "AudioRepo"
    }
}