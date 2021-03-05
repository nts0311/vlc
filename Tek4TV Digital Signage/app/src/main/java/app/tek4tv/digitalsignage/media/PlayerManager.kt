package app.tek4tv.digitalsignage.media

import android.content.Context
import android.net.Uri
import android.util.Log
import app.tek4tv.digitalsignage.model.MediaItem
import app.tek4tv.digitalsignage.model.MediaType
import app.tek4tv.digitalsignage.repo.MediaRepo
import app.tek4tv.digitalsignage.ui.CustomPlayer
import app.tek4tv.digitalsignage.utils.getDurationInSecond
import app.tek4tv.digitalsignage.utils.toCalendar
import app.tek4tv.digitalsignage.viewmodels.MainViewModel
import kotlinx.coroutines.*
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.util.*
import kotlin.coroutines.resume

class PlayerManager(
    private val applicationContext: Context,
    private var lifecycleScope: CoroutineScope,
    private var viewModel: MainViewModel,
    var vlcVideoLayout: VLCVideoLayout
) {
    private var checkScheduledMediaJob: Job? = null
    private var checkScheduledListJob: Job? = null

    private var presentImageJob: Job? = null

    private val mLibVLC: LibVLC
    private val audioPlayer: CustomPlayer
    private val visualPlayer: CustomPlayer

    private var mainPlayer: CustomPlayer? = null
        set(value) {
            field = value
            setMainPlayerEventListener()
        }

    private val playlistRepo: MediaRepo
        get() = viewModel.mediaRepo

    var currentPlaylist = listOf<MediaItem>()

    private var audioIndex = 0
    var audioList = mutableListOf<Uri>()

    //-1 for random unscheduled media
    //0 for init
    //HH:mm:ss-HH:mm:ss for scheduled media
    private var currentPlaylistKey = "0"

    var rotationMode = 0

    init {
        val args = ArrayList<String>()
        args.add("-vvv")
        args.add("--codec=avcodec")
        args.add("--file-caching=3000")
        args.add("--no-http-reconnect")
        args.add("--avcodec-hurry-up")
        args.add("--avcodec-fast")
        args.add("--avcodec-skip-frame=4")
        args.add("--avcodec-skip-idct=4")
        args.add("--avcodec-skiploopfilter=4")

        mLibVLC = LibVLC(applicationContext, args)
        visualPlayer = CustomPlayer(mLibVLC)
        audioPlayer = CustomPlayer(mLibVLC)

        audioList = viewModel.audioRepo.audioFileUri

        instance = this
    }

    fun attachVisualPlayerView() {
        visualPlayer.attachViews(vlcVideoLayout, null, false, false)
    }

    fun playMediaByIndex(index: Int) {
        try {
            val playlist = currentPlaylist

            if (playlist.isEmpty()) return
            val mediaItem = playlist[index]

            playMedia(mediaItem)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playScheduledMediaByIndex(mediaIndex: Int) {
        val scheduledItems = viewModel.mediaRepo.scheduledMediaItems
        if (mediaIndex >= scheduledItems.size) return

        playMedia(scheduledItems[mediaIndex])
    }

    private fun playMedia(mediaItem: MediaItem) {
        try {
            viewModel.currentMediaItem = mediaItem

            presentImageJob?.cancel()

            mainPlayer?.eventListener = {}
            visualPlayer.eventListener = {}
            audioPlayer.eventListener = {}

            when (mediaItem.getMediaType()) {
                MediaType.VIDEO -> {
                    if (mediaItem.muted) {
                        mainPlayer = audioPlayer
                        //playMutedVideo(mediaItem)
                        playMutedMedia(mediaItem)
                    } else {
                        val media = mediaItem.getVlcMedia(mLibVLC)
                        audioPlayer.stop()
                        mainPlayer = visualPlayer
                        visualPlayer.play(media)
                    }
                }
                MediaType.IMAGE -> {
                    //presentImage(mediaItem)
                    playMutedMedia(mediaItem)
                    mainPlayer = audioPlayer
                }
                MediaType.AUDIO -> {
                    mainPlayer = audioPlayer
                    val media = mediaItem.getVlcMedia(mLibVLC)
                    audioPlayer.play(media)
                }
                else -> {
                }
            }


        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun playMutedVideo(videoItem: MediaItem) {
        val video: Media = videoItem.getVlcMedia(mLibVLC)
        video.addOption(":no-audio")
        //video.addOption(":video-filter=transform{type=hflip}")
        visualPlayer.media = video
        visualPlayer.play()

        visualPlayer.eventListener = { event ->
            when (event) {
                MediaPlayer.Event.EndReached -> {
                    vlcVideoLayout.post {
                        visualPlayer.media = video
                        visualPlayer.play()
                    }
                }

                MediaPlayer.Event.EncounteredError -> {
                    presentImageJob?.cancel()
                    presentImageJob = null

                    visualPlayer.eventListener = {}

                    playNextMedia()
                }
            }
        }

        playRandomAudio()
    }


    private fun presentImage(mediaItem: MediaItem) {

        val imageList = mutableListOf(mediaItem)

        imageList.addAll(
            playlistRepo.unscheduledList.filter { it.getMediaType() == MediaType.IMAGE })
        playRandomAudio()

        presentImageJob = lifecycleScope.launch {
            var playedMainImage = false
            while (true) {
                val media = if (!playedMainImage) {
                    playedMainImage = true
                    mediaItem
                } else {
                    imageList.random()
                }
                val delayDuration = getMediaDuration(media)
                visualPlayer.play(media.getVlcMedia(mLibVLC))
                Log.d("delay", delayDuration.toString())
                delay(delayDuration)
            }
        }
    }

    private fun getMediaDuration(mediaItem: MediaItem): Long {
        if (mediaItem.duration != null) {
            val mediaDuration = getDurationInSecond(mediaItem.duration!!)
            if (mediaDuration > 0) return mediaDuration * 1000
        }

        return 15000L
    }

    //picture and muted video
    private fun playMutedMedia(firstItem: MediaItem) {
        val mutedMedia = mutableListOf(firstItem)

        mutedMedia.addAll(currentPlaylist.filter {
            it.getMediaType() == MediaType.IMAGE || (it.getMediaType() == MediaType.VIDEO && it.muted)
        }.shuffled())

        playRandomAudio()

        presentImageJob = lifecycleScope.launch {
            var index = 0

            while (true) {
                if (index >= mutedMedia.size) index = 0

                val mediaItem = mutedMedia[index]

                when (mediaItem.getMediaType()) {
                    MediaType.IMAGE -> {
                        visualPlayer.play(mediaItem.getVlcMedia(mLibVLC))
                        delay(getMediaDuration(mediaItem))
                    }

                    MediaType.VIDEO -> {
                        playMutedVideoItem(mediaItem)
                        visualPlayer.eventListener = { }
                    }

                    else -> {
                    }
                }
                index++
            }
        }
    }

    private suspend fun playMutedVideoItem(video: MediaItem) = suspendCancellableCoroutine<Int> {
        val vlcVideo = video.getVlcMedia(mLibVLC)
        vlcVideo.addOption(":no-audio")
        visualPlayer.play(vlcVideo)
        visualPlayer.eventListener = { event ->
            when (event) {
                MediaPlayer.Event.EndReached -> it.resume(0)
                MediaPlayer.Event.EncounteredError -> it.resume(-1)
            }
        }
        it.invokeOnCancellation { visualPlayer.eventListener = { } }
    }

    private fun playRandomAudio() {
        if (audioList.isEmpty()) {
            audioList = viewModel.audioRepo.audioFileUri
            audioIndex = 0
        }

        if (audioList.isNotEmpty()) {
            if (audioIndex >= audioList.size) {
                audioIndex = 0
                audioList = audioList.shuffled() as MutableList<Uri>
            }

            val audioItem = audioList[audioIndex++]

            val backgroundAudio = Media(mLibVLC, audioItem)


            val audioName = audioItem.toString()
            viewModel.currentAudioName = audioName.substring(audioName.lastIndexOf('/') + 1)

            Log.d("audiox", audioItem.toString())
            audioPlayer.play(backgroundAudio)
        } else Log.d("audiox", "no audio")
    }


    private fun playNextMedia() {
        vlcVideoLayout.post {
            val playlist = currentPlaylist
            viewModel.playlistIndex++
            if (viewModel.playlistIndex >= playlist.size) viewModel.playlistIndex = 0

            playMediaByIndex(viewModel.playlistIndex)
        }
    }

    private fun setMainPlayerEventListener() {
        if (mainPlayer != null) {
            mainPlayer!!.eventListener = { event ->
                when (event) {
                    MediaPlayer.Event.EndReached -> {
                        playNextMedia()
                        mainPlayer!!.eventListener = {}
                    }

                    MediaPlayer.Event.EncounteredError -> {
                        presentImageJob?.cancel()
                        presentImageJob = null

                        playNextMedia()
                        mainPlayer!!.eventListener = {}
                    }
                    MediaPlayer.Event.Stopped -> viewModel.isPlaying = false
                    MediaPlayer.Event.Playing -> viewModel.isPlaying = true
                }
            }
        }
    }

    /*fun onNewBroadcastList() {
        currentPlaylistKey = "0"
        checkScheduledMediaList()
    }*/

    fun setPlaylist(playlistKey: String) {
        cancelPlaying()
        setPlaylistContent(playlistRepo.scheduledList[playlistKey] ?: listOf(), audioList)
    }

    private fun setPlaylistContent(playlist: List<MediaItem>, audios: List<Uri>) {
        this.currentPlaylist = playlist
        //this.audioList = audios

        viewModel.playlistIndex = 0

        if (currentPlaylist.isNotEmpty()) {

            //if the first item is scheduled, skip over and play the second media
            val firstItem = playlist[0]
            if (firstItem.fixTime != "00:00:00") {
                val now = Calendar.getInstance()
                val scheduledTime = toCalendar(firstItem.fixTime)

                if (now.timeInMillis < scheduledTime.timeInMillis && currentPlaylist.size > 2) {
                    playMediaByIndex(1)
                    viewModel.playlistIndex = 1
                    return
                }
            }


            playMediaByIndex(0)
        }
    }

    /*private fun checkScheduledMediaList() {
        //cancelPlaying()

        lifecycleScope.launch {
            loopCheckList(Calendar.getInstance().timeInMillis)
        }

        *//*checkScheduledListTimer.removeTimeListener(checkScheduledMediaListId)

        checkScheduledMediaListId =
            checkScheduledListTimer.addTimeListener(Dispatchers.Default) { now ->
                loopCheckList(now)
            }*//*

        checkScheduledListJob?.cancel()
        checkScheduledListJob = lifecycleScope.launch(Dispatchers.Default) {
            while (true) {
                loopCheckList(Calendar.getInstance().timeInMillis)
                Log.d("checklist", "checked")
                delay(3000)
            }
        }
    }

    private suspend fun loopCheckList(now: Long) {
        var foundPeriod = false
        val scheduledList = playlistRepo.scheduledList
        for (i in scheduledList.keys.indices) {
            val period = scheduledList.keys.elementAt(i)

            val t = period.split("-")

            val start = toCalendar(t[0])
            val end = toCalendar(t[1])

            val startHour = start.get(Calendar.HOUR_OF_DAY)
            val endHour = end.get(Calendar.HOUR_OF_DAY)

            if (endHour < startHour) {
                val nowHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                if (nowHour >= startHour) end.add(Calendar.DAY_OF_MONTH, 1)
                else start.add(Calendar.DAY_OF_MONTH, -1)
            }

            if (now in start.timeInMillis..end.timeInMillis) {

                if (currentPlaylistKey != period) {
                    Log.d("scheduledlist", period)
                    withContext(Dispatchers.Main) {
                        setPlaylistContent(playlistRepo.scheduledList[period] ?: listOf(),
                            audioList)
                        currentPlaylistKey = period
                    }
                }

                foundPeriod = true
                break
            }
        }

        if (!foundPeriod && currentPlaylistKey != "-1") {
            Log.d("scheduledlist", "random")
            withContext(Dispatchers.Main) {
                setPlaylistContent(playlistRepo.unscheduledList, audioList)
                currentPlaylistKey = "-1"
            }
        }
    }



    fun checkScheduledMedia() {
        //timer.removeTimeListener(checkScheduledMediaJobId)

        val playlist = playlistRepo.broadcastList.filter {
            it.path != "start" && it.path != "end"
        }
        val scheduledItems: MutableList<MediaItem> = mutableListOf()
        scheduledItems.addAll(
            playlist.filter { it.fixTime.isNotEmpty() && it.fixTime != "00:00:00" })

        checkScheduledMediaJob?.cancel()

        checkScheduledMediaJob = lifecycleScope.launch(Dispatchers.Default) {
            while (true) {
                aaa(playlist, scheduledItems)

                delay(1000)
            }
        }

        *//* checkScheduledMediaJobId = timer.addTimeListener(Dispatchers.Default) {

         }*//*
    }

    suspend fun aaa(playlist: List<MediaItem>, scheduledItems: MutableList<MediaItem>) {
        if (scheduledItems.isEmpty()) {
            scheduledItems.addAll(
                playlist.filter { it.fixTime.isNotEmpty() && it.fixTime != "00:00:00" })
        }


        var index = -1

        scheduledItems.forEachIndexed { i, mediaItem ->
            try {
                val now = Calendar.getInstance()
                val scheduledTime = toCalendar(mediaItem.fixTime)
                val mediaDuration = getDurationInSecond(mediaItem.duration ?: "00:00:00")
                if (scheduledTime.timeInMillis <= now.timeInMillis && now.timeInMillis <= scheduledTime.timeInMillis + mediaDuration * 1000) {
                    Log.d("hengio",
                        "${now.timeInMillis} - ${scheduledTime.timeInMillis} - ${scheduledTime.timeInMillis + mediaDuration * 1000}")

                    if (scheduledItems[i] != viewModel.currentMediaItem) index = i
                }

            } catch (e: Exception) {
                Log.e("checkScheduledMedia", "error checking media fixtime")
            }
        }

        if (index != -1 && index < scheduledItems.size) {
            withContext(Dispatchers.Main) {
                val mediaToPlay = scheduledItems[index]
                playMedia(mediaToPlay)
                Log.d("hengio", mediaToPlay.name ?: "")
                scheduledItems.removeAt(index)
            }
        }
    }*/

    fun cancelPlaying() {
        visualPlayer.eventListener = {}
        audioPlayer.eventListener = {}

        presentImageJob?.cancel()

        visualPlayer.stop()
        audioPlayer.stop()
    }

    fun onActivityStop() {
        mainPlayer?.stop()
        audioPlayer.stop()
        visualPlayer.stop()

        visualPlayer.detachViews()
    }

    fun onActivityDestroy() {
        presentImageJob?.cancel()

        mainPlayer?.release()
        if (!audioPlayer.isReleased) audioPlayer.release()

        if (!visualPlayer.isReleased) visualPlayer.release()

        mLibVLC.release()
    }


    companion object {
        var instance: PlayerManager? = null
    }
}