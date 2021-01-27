package app.tek4tv.digitalsignage.viewmodels


import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tek4tv.digitalsignage.media.MediaDownloadManager
import app.tek4tv.digitalsignage.model.MediaItem
import app.tek4tv.digitalsignage.repo.PlaylistRepo
import app.tek4tv.digitalsignage.utils.AUDIO_FOLDER_NAME
import app.tek4tv.digitalsignage.utils.getAllFileNameInFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File


class MainViewModel @ViewModelInject constructor(
    val playlistRepo: PlaylistRepo
) : ViewModel() {

    var isPlaying: Boolean = false
    val broadcastList = MutableLiveData<List<MediaItem>>()


    private var getPlaylistJob: Job? = null

    private var mediaDownloadManager: MediaDownloadManager =
        MediaDownloadManager(viewModelScope, playlistRepo)

    var playlistIndex = 0

    var currentMediaItem: MediaItem? = null


    fun getPlaylist(context: Context, needUpdate: Boolean) {
        if (getPlaylistJob != null) return

        getPlaylistJob = viewModelScope.launch {
            val res = playlistRepo.getBroadcastList(context.filesDir.path, needUpdate)

            mediaDownloadManager.broadcastList = res

            broadcastList.value = res

            getPlaylistJob = null
        }
    }

    fun checkPlaylist(appContext: Context) {
        mediaDownloadManager.checkPlaylist(appContext)
    }

    fun downloadMedias(appContext: Context)
    {
        mediaDownloadManager.downloadMedias(appContext)
    }

    fun getAudioListFromNetwork(appContext: Context, url: String)
    {
        viewModelScope.launch(Dispatchers.Default) {
            val audioPaths = playlistRepo.getAudioList(url)
            if (audioPaths.isNotEmpty())
                mediaDownloadManager.downloadAudio(appContext, audioPaths)
        }
    }

    fun getAudioList(appContext: Context): List<Uri>
    {
        return try
        {
            val audioFolderPath = "${appContext.filesDir.path}${File.separator}$AUDIO_FOLDER_NAME"
            getAllFileNameInFolder(audioFolderPath).map { Uri.parse("file://$audioFolderPath${File.separator}$it") }
                .shuffled()

        } catch (e: Exception)
        {
            Log.e("MainViewmodel", "Error getting audio list")
            listOf()
        }
    }
}