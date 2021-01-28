package app.tek4tv.digitalsignage.media

import android.content.Context
import android.net.Uri
import android.util.Log
import app.tek4tv.digitalsignage.Timer
import app.tek4tv.digitalsignage.model.MediaItem
import app.tek4tv.digitalsignage.model.MediaType
import app.tek4tv.digitalsignage.repo.PlaylistRepo
import app.tek4tv.digitalsignage.ui.CustomPlayer
import app.tek4tv.digitalsignage.viewmodels.MainViewModel
import kotlinx.coroutines.*
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.util.*

class PlayerManager(
    private var applicationContext: Context,
    private var lifecycleScope: CoroutineScope,
    private var viewModel: MainViewModel,
    var mVideoLayout: VLCVideoLayout
) {
    private var checkScheduledMediaJobId: Long = -1L

    private var presentImageJob: Job? = null

    private var timer: Timer
    private var checkScheduledListTimer: Timer
    private var checkScheduledMediaListId = -1L

    private val mLibVLC: LibVLC
    private val audioPlayer: CustomPlayer
    private val visualPlayer: CustomPlayer

    private var mainPlayer: CustomPlayer? = null
        set(value) {
            field = value
            setMainPlayerEventListener()
        }


    //rotation mode
    var rotationMode = 0

    private val playlistRepo: PlaylistRepo
        get() = viewModel.playlistRepo

    var currentPlaylist = listOf<MediaItem>()

    private var audioIndex = 0
    var audioList = listOf<Uri>()

    //-1 for random unscheduled media
    //0 for init
    //HH:mm:ss-HH:mm:ss for scheduled media
    private var currentPlaylistKey = "0"

    init {
        val args = ArrayList<String>()
        args.add("-vvv")
        args.add("--aout=opensles")
        args.add("--avcodec-codec=h264")
        args.add("--network-caching=2000")
        args.add("--no-http-reconnect")

        timer = Timer(lifecycleScope)
        timer.start()

        checkScheduledListTimer = Timer(lifecycleScope)
        checkScheduledListTimer.delay = 3000
        checkScheduledListTimer.start()

        mLibVLC = LibVLC(applicationContext, args)
        visualPlayer = CustomPlayer(mLibVLC)
        audioPlayer = CustomPlayer(mLibVLC)

        audioList = viewModel.getAudioList(applicationContext)
    }

    fun attachVisualPlayerView() {
        if (rotationMode == 0) {
            visualPlayer.attachViews(mVideoLayout, null, true, false)
        } else {
            mVideoLayout.rotation = 180.0f
            visualPlayer.attachViews(mVideoLayout, null, true, true)
        }
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

    private fun playMedia(mediaItem: MediaItem) {
        try {
            viewModel.currentMediaItem = mediaItem


            Log.d("mediaplayed", mediaItem.path)

            presentImageJob?.cancel()

            mainPlayer?.eventListener = {}
            visualPlayer.eventListener = {}
            audioPlayer.eventListener = {}

            when (mediaItem.getMediaType()) {
                MediaType.VIDEO -> {
                    Log.d("mediaplayed", "video")

                    if (mediaItem.muted) {
                        mainPlayer = audioPlayer
                        playMutedVideo(mediaItem)
                    } else {
                        val media = mediaItem.getVlcMedia(mLibVLC)
                        audioPlayer.stop()
                        media.addOption(":fullscreen")
                        mainPlayer = visualPlayer
                        visualPlayer.play(media)
                    }
                }
                MediaType.IMAGE -> {
                    Log.d("mediaplayed", "audio")
                    presentImage(mediaItem)
                    mainPlayer = audioPlayer
                }
                else -> {
                }
            }


        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun playMutedVideo(videoItem: MediaItem) {
        val video = videoItem.getVlcMedia(mLibVLC)
        visualPlayer.media = video
        visualPlayer.play()


        visualPlayer.eventListener = { event ->
            when (event) {
                MediaPlayer.Event.EndReached -> {
                    mVideoLayout.post {
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

        imageList.addAll(playlistRepo.unscheduledList.filter { it.getMediaType() == MediaType.IMAGE })
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

    private fun playRandomAudio() {
        if (audioList.isEmpty()) {
            audioList = viewModel.getAudioList(applicationContext)
            audioIndex = 0
        }

        if (audioList.isNotEmpty()) {
            if (audioIndex >= audioList.size) {
                audioIndex = 0
                audioList = audioList.shuffled()
            }

            Log.d("audiox", audioIndex.toString())
            val backgroundAudio = Media(
                mLibVLC, audioList[audioIndex++])
            audioPlayer.play(backgroundAudio)
        } else Log.d("audiox", "no audio")
    }

    fun playLiveStream() {
        cancelPlaying()

    }


    private fun playNextMedia() {
        mVideoLayout.post {
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

    fun onNewBroadcastList() {
        currentPlaylistKey = "0"
        checkScheduledMediaList()
    }

    private fun checkScheduledMediaList() {
        //cancelPlaying()

        lifecycleScope.launch {
            loopCheckList(Calendar.getInstance().timeInMillis)
        }

        checkScheduledListTimer.removeTimeListener(checkScheduledMediaListId)

        checkScheduledMediaListId =
            checkScheduledListTimer.addTimeListener(Dispatchers.Default) { now ->
                Log.d("checklist", "check")
                loopCheckList(now)
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

            if (end.get(Calendar.HOUR_OF_DAY) < start.get(Calendar.HOUR_OF_DAY)) end.add(
                Calendar.DAY_OF_MONTH, 1)

            if (now in start.timeInMillis..end.timeInMillis) {

                if (currentPlaylistKey != period) {
                    Log.d("scheduledlist", period)
                    withContext(Dispatchers.Main) {
                        setPlaylistContent(
                            playlistRepo.scheduledList[period] ?: listOf(), audioList)
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

    private fun setPlaylistContent(playlist: List<MediaItem>, audios: List<Uri>) {
        this.currentPlaylist = playlist
        this.audioList = audios

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

    fun checkScheduledMedia() {
        timer.removeTimeListener(checkScheduledMediaJobId)

        val playlist = playlistRepo.broadcastList.filter {
            it.path != "start" && it.path != "end"
        }
        val scheduledItems: MutableList<MediaItem> = mutableListOf()
        scheduledItems.addAll(playlist.filter { it.fixTime.isNotEmpty() && it.fixTime != "00:00:00" })

        checkScheduledMediaJobId = timer.addTimeListener(Dispatchers.Default) {
            if (scheduledItems.isEmpty()) {
                scheduledItems.addAll(playlist.filter { it.fixTime.isNotEmpty() && it.fixTime != "00:00:00" })
            }


            var index = -1

            scheduledItems.forEachIndexed { i, mediaItem ->
                try {
                    val now = Calendar.getInstance()
                    val scheduledTime = toCalendar(mediaItem.fixTime)
                    val mediaDuration = getDurationInSecond(mediaItem.duration ?: "00:00:00")
                    if (scheduledTime.timeInMillis <= now.timeInMillis && now.timeInMillis <= scheduledTime.timeInMillis + mediaDuration * 1000) {
                        Log.d(
                            "hengio",
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
        }
    }

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
        timer.stop()
        visualPlayer.detachViews()
    }

    fun onActivityDestroy() {
        presentImageJob?.cancel()

        mainPlayer?.release()
        if (!audioPlayer.isReleased) audioPlayer.release()

        if (!visualPlayer.isReleased) visualPlayer.release()

        mLibVLC.release()
    }
}