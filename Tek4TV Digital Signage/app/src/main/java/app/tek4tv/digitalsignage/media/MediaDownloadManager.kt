package app.tek4tv.digitalsignage.media

import android.content.Context
import android.net.Uri
import android.util.Log
import app.tek4tv.digitalsignage.model.MediaItem
import app.tek4tv.digitalsignage.model.MediaType
import app.tek4tv.digitalsignage.repo.PlaylistRepo
import app.tek4tv.digitalsignage.utils.*
import com.downloader.Error
import com.downloader.OnDownloadListener
import kotlinx.coroutines.*
import java.io.File

class MediaDownloadManager(
    private val scope: CoroutineScope,
    private val playlistRepo: PlaylistRepo,
) {

    //private val downloadManager = DownloadManager(scope)
    private val imageResize = ImageResize(scope)

    private val downloadHelper = DownloadHelper.getInstance(scope)

    var broadcastList: List<MediaItem> = listOf()
        set(value) {
            field = value

            playlist = broadcastList.filter {
                val res = it.path != "start" && it.path != "end"
                res
            }
        }
    private var checkPlaylistJob: Job? = null
    private var downloadMediaJob: Job? = null
    private var saveFileJob: Job? = null
    private var playlist: List<MediaItem> = listOf()

    fun checkPlaylist(appContext: Context) {
        checkPlaylistJob?.cancel()

        checkPlaylistJob = scope.launch(Dispatchers.Default) {
            while (true) {
                if (playlist.isNotEmpty()) {
                    var foundBrokenPath = false
                    playlist.forEach {
                        val mediaPath = it.path
                        if (mediaPath.isNotEmpty() && !File(mediaPath).exists()) {
                            if (it.pathBackup.startsWith("http")) it.path = it.pathBackup
                            foundBrokenPath = true
                        }
                    }

                    if (foundBrokenPath) savePlaylist(broadcastList, appContext.filesDir.path)

                    val needDownload = playlist.any {
                        it.path.startsWith("http") || it.path.isEmpty()
                    }

                    if (needDownload) downloadMedias(appContext)

                    Log.d("checkplaylist", needDownload.toString())
                }

                delay(30000)
            }
        }
    }

    @Synchronized
    fun downloadMedias(appContext: Context) {
        scope.launch {
            downloadMediaJob?.join()
            downloadMediaJob = launch(Dispatchers.Default) {
                startDownloadMedia(appContext)
            }
        }
    }

    private suspend fun startDownloadMedia(appContext: Context) {
        if (playlist.isNullOrEmpty()) return
        val storagePath = appContext.filesDir.path

        playlist.forEach {
            val url = it.path
            if (!url.isEmpty() && url.startsWith("http")) {
                val fileName = getFileName(url)
                if (!isFileExisted(storagePath, fileName)) {
                    downloadHelper.addToQueue(DownloadItem().apply {
                        itDownloadUrl = url
                        itStoragePath = storagePath ?: appContext.filesDir.path!!
                        itFileName = fileName
                        downloadListener = object : OnDownloadListener {
                            override fun onDownloadComplete() {
                                it.pathBackup = it.path
                                it.path = "$storagePath/$fileName"
                                savePlaylist(broadcastList, storagePath)
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
                } else {
                    it.path = "$storagePath/$fileName"
                    savePlaylist(broadcastList, storagePath)
                }
            }
        }
    }

    fun savePlaylist(broadcastList: List<MediaItem>, storagePath: String) {
        scope.launch {
            saveFileJob?.join()
            saveFileJob = launch {
                playlistRepo.savePlaylistToFile(
                    broadcastList, storagePath)
            }
        }
    }


    fun downloadAudio(appContext: Context, paths: List<String>) {
        val storagePath = "${appContext.filesDir.path}${File.separator}$AUDIO_FOLDER_NAME"
        if (!isFolderExisted(storagePath)) createFolder(storagePath)

        if (paths.isEmpty()) return

        paths.forEach { url ->
            if (url.isNotEmpty() && url.startsWith("http")) {
                val fileName = getFileName(url)

                if (!isFileExisted(storagePath, fileName)) {

                    scope.launch {
                        downloadHelper.addToQueue(DownloadItem().apply {
                            itDownloadUrl = url
                            itStoragePath = storagePath
                            itFileName = fileName
                            downloadListener = object : OnDownloadListener {
                                override fun onDownloadComplete() {
                                    Log.d("downloadcomplete", url)
                                }

                                override fun onError(error: Error?) {
                                    Log.d("downloaderror", url)
                                }
                            }
                        })
                    }
                }
            }
        }

    }

    private fun getFileName(url: String): String {
        var fileName = File(Uri.parse(url).path).name

        val slash = fileName.lastIndexOf("\\")

        if (slash != -1) {
            fileName = fileName.substring(slash + 1)
        }

        return fileName
    }
}