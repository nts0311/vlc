package app.tek4tv.digitalsignage.viewmodels


import android.content.Context
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tek4tv.digitalsignage.media.MediaWatchDog
import app.tek4tv.digitalsignage.model.MediaItem
import app.tek4tv.digitalsignage.repo.AudioRepo
import app.tek4tv.digitalsignage.repo.MediaRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch


class MainViewModel @ViewModelInject constructor(
    internal val mediaRepo: MediaRepo, val audioRepo: AudioRepo
) : ViewModel() {

    var isPlaying: Boolean = false
    val broadcastList = MutableLiveData<List<MediaItem>>()


    private var getPlaylistJob: Job? = null

    private var mediaDownloadManager = MediaWatchDog(viewModelScope, mediaRepo, audioRepo)

    var playlistIndex = 0

    var currentMediaItem: MediaItem? = null

    var currentAudioName = ""


    fun getPlaylist(context: Context, needUpdate: Boolean) {
        if (getPlaylistJob != null) return

        getPlaylistJob = viewModelScope.launch {
            val res = mediaRepo.getBroadcastList(needUpdate)
            audioRepo.getAudioUrls("", false)

            //mediaDownloadManager.broadcastList = res

            broadcastList.value = res

            getPlaylistJob = null
        }
    }

    fun checkPlaylist(appContext: Context) {
        mediaDownloadManager.checkPlaylist()
        mediaDownloadManager.checkAudio()
    }

    fun downloadMedias(appContext: Context) {
        // mediaDownloadManager.downloadMedias(appContext)
        mediaRepo.startDownloadMedia()
    }

    fun getAudioListFromNetwork(appContext: Context, url: String) {
        viewModelScope.launch(Dispatchers.Default) {
            /*val audioPaths = playlistRepo.getAudioList(url)
            if (audioPaths.isNotEmpty())
                mediaDownloadManager.downloadAudio(appContext, audioPaths)*/

            audioRepo.getAudioUrls(url, true)
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioRepo.cancelAllJob()
    }
}