package app.tek4tv.digitalsignage.viewmodels


import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tek4tv.digitalsignage.model.MediaItem
import app.tek4tv.digitalsignage.repo.PlaylistRepo
import app.tek4tv.digitalsignage.utils.AUDIO_FOLDER_NAME
import app.tek4tv.digitalsignage.utils.MediaDownloadUtils
import app.tek4tv.digitalsignage.utils.getAllFileNameInFolder
import app.tek4tv.digitalsignage.utils.isFileExisted
import com.downloader.Error
import com.downloader.OnDownloadListener
import com.downloader.PRDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File


class MainViewmodel @ViewModelInject constructor(
        private val playlistRepo: PlaylistRepo
) : ViewModel()
{

    var isPlaying: Boolean = false
    val playlist = MutableLiveData<List<MediaItem>>()

    private var saveFileJob: Job? = null
    private var getPlaylistJob: Job? = null
    private var checkPlaylistJob: Job? = null
    private var downloadMediaJob: Job? = null

    var playlistIndex = 0

    var currentOrientation = -1

    fun getPlaylist(context: Context, needUpdate: Boolean)
    {
        if (getPlaylistJob != null) return

        getPlaylistJob = viewModelScope.launch {
            val res = playlistRepo.getPlaylist(context.filesDir.path, needUpdate)
            playlist.value = res
            getPlaylistJob = null
        }
    }

    fun checkPlaylist(appContext: Context)
    {
        if (checkPlaylistJob != null) return

        checkPlaylistJob = viewModelScope.launch {
            while (true)
            {
                val curPlaylist = playlist.value
                if (curPlaylist != null && curPlaylist.isNotEmpty())
                {

                    var foundBrokenPath = false
                    curPlaylist.forEach {
                        val mediaPath = it.path
                        if (mediaPath.isNotEmpty() && !File(mediaPath).exists())
                        {
                            if (it.pathBackup.startsWith("http"))
                                it.path = it.pathBackup
                            foundBrokenPath = true
                        }
                    }

                    if (foundBrokenPath) savePlaylist(curPlaylist, appContext.filesDir.path)

                    val needDownload = playlist.value!!.any {
                        it.path.startsWith("http") || it.path.isNullOrEmpty()
                    }

                    if (needDownload)
                        downloadMedias(appContext)

                    Log.d("checkplaylist", needDownload.toString())
                }

                delay(30000)
            }
        }
    }

    @Synchronized
    fun downloadMedias(appContext: Context)
    {
        viewModelScope.launch {
            downloadMediaJob?.join()
            downloadMediaJob = launch {
                startDownloadMedia(appContext)
            }
        }
    }

    private fun startDownloadMedia(appContext: Context)
    {
        val storagePath = appContext.filesDir.path
        if (!playlist.value.isNullOrEmpty())
        {
            val listVideo = playlist.value!!
            listVideo.forEach {
                val url = it.path

                if (!url.isNullOrEmpty() && url.startsWith("http"))
                {
                    var fileName = File(Uri.parse(url).path).name

                    val slash = fileName.lastIndexOf("\\")

                    if (slash != -1)
                    {
                        fileName = fileName.substring(slash + 1)
                    }

                    if (!isFileExisted(storagePath, fileName))
                    {
                        PRDownloader.download(url, storagePath, fileName).build()
                                .start(object : OnDownloadListener
                                {
                                    override fun onDownloadComplete()
                                    {
                                        it.pathBackup = it.path
                                        it.path = "$storagePath/$fileName"
                                        savePlaylist(listVideo, storagePath)
                                        Log.d("downloadcomplete", it.path)
                                    }

                                    override fun onError(error: Error?)
                                    {
                                        Log.d("downloaderror", it.path)
                                    }
                                })
                    } else
                    {
                        it.path = "$storagePath/$fileName"
                        savePlaylist(listVideo, storagePath)
                    }
                }
            }
        }
    }

    fun savePlaylist(listVideo: List<MediaItem>, storagePath: String)
    {
        viewModelScope.launch {
            saveFileJob?.join()
            saveFileJob = launch {
                playlistRepo.savePlaylistToFile(
                        listVideo,
                        storagePath
                )
            }
        }
    }

    fun getAudioListFromNetwork(appContext: Context, url: String)
    {
        viewModelScope.launch(Dispatchers.Default) {
            val audioPaths = playlistRepo.getAudioList(url)
            if (audioPaths.isNotEmpty())
                MediaDownloadUtils.downloadAudio(appContext, audioPaths)
        }
    }

    fun getAudioList(appContext: Context): List<Uri>
    {
        return try
        {
            val audioFolderPath = "${appContext.filesDir.path}${File.separator}$AUDIO_FOLDER_NAME"
            getAllFileNameInFolder(audioFolderPath)
                    .map { Uri.parse("file://$audioFolderPath${File.separator}$it") }

        } catch (e: Exception)
        {
            Log.e("MainViewmodel", "Error getting audio list")
            listOf()
        }
    }


}