package app.tek4tv.digitalsignage.utils

import android.util.Log
import app.tek4tv.digitalsignage.repo.AudioRepo
import app.tek4tv.digitalsignage.repo.MediaRepo
import app.tek4tv.digitalsignage.utils.*
import kotlinx.coroutines.*
import java.io.File

class MediaWatchDog(
    private val scope: CoroutineScope,
    private val mediaRepo: MediaRepo,
    private val audioRepo: AudioRepo
) {
    private var checkPlaylistJob: Job? = null
    private var checkAudioJob: Job? = null

    fun checkPlaylist() {
        checkPlaylistJob?.cancel()

        checkPlaylistJob = scope.launch(Dispatchers.Default) {
            while (true) {
                if (mediaRepo.broadcastList.isEmpty()) {
                    delay(30000)
                    continue
                }

                //if (playlist.isNotEmpty()) {
                var foundBrokenPath = false
                mediaRepo.broadcastList.forEach {
                    if (it.path == "start" || it.path == "end") return@forEach

                    val mediaPath = it.path
                    if (mediaPath.isNotEmpty() && !File(mediaPath).exists()) {
                        if (it.pathBackup.startsWith("http")) it.path = it.pathBackup
                        foundBrokenPath = true
                    }
                }

                if (foundBrokenPath) mediaRepo.saveItemsToFile(mediaRepo.broadcastList)//savePlaylist(broadcastList, appContext.filesDir.path)

                val needDownload = mediaRepo.broadcastList.filter {
                    it.path.startsWith("http")
                }

                if (needDownload.isNotEmpty()) {
                    needDownload.forEach {
                        mediaRepo.downloadAMediaItem(it)
                    }
                } //downloadMedias(appContext)

                Log.d("checkplaylist", (needDownload.isNotEmpty()).toString())
                //}

                delay(30000)
            }
        }
    }

    fun checkAudio() {
        val delayDuration = 180000L

        checkAudioJob?.cancel()
        checkAudioJob = scope.launch(Dispatchers.Default) {
            while (true) {
                if (audioRepo.isDownloadingAudio) {
                    delay(delayDuration)
                    continue
                }

                audioRepo.audioUrls.forEach { url ->
                    val fileName = getFileNameFromUrl(url)

                    if (isFileExisted(audioRepo.audioFolderPath, fileName)) return@forEach

                    audioRepo.downloadAnAudio(url)
                }

                delay(delayDuration)
            }
        }
    }
}