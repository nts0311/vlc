package app.tek4tv.digitalsignage.media

import android.content.Context
import android.net.Uri
import android.util.Log
import app.tek4tv.digitalsignage.model.MediaItem
import app.tek4tv.digitalsignage.model.MediaType
import app.tek4tv.digitalsignage.model.getDurationInSecond
import app.tek4tv.digitalsignage.ui.CustomPlayer
import app.tek4tv.digitalsignage.viewmodels.MainViewmodel
import kotlinx.coroutines.*
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.util.*

class PlayerManager(
        private var applicationContext: Context,
        private var lifecycleScope: CoroutineScope,
        private var viewModel: MainViewmodel,

        var mVideoLayout: VLCVideoLayout?,

        )
{
    private val USE_TEXTURE_VIEW = false
    private val ENABLE_SUBTITLES = true
    private var checkScheduledMediaJob: Job? = null
    private var presentImageJob: Job? = null

    val mLibVLC: LibVLC
    val audioPlayer: CustomPlayer
    val visualPlayer: CustomPlayer
    var mainPlayer: CustomPlayer? = null

    var audioList = listOf<Uri>()

    init
    {
        val args = ArrayList<String>()
        args.add("-vvv")
        args.add("--aout=opensles")
        args.add("--avcodec-codec=h264")
        args.add("--network-caching=2000")
        args.add("--no-http-reconnect")
        args.add("--file-logging")
        args.add("--logfile=vlc-log.txt")


        mLibVLC = LibVLC(applicationContext, args)
        visualPlayer = CustomPlayer(mLibVLC)
        audioPlayer = CustomPlayer(mLibVLC)

        audioList = viewModel.getAudioList(applicationContext)
    }

    fun attachVisualPlayerView()
    {
        visualPlayer.attachViews(mVideoLayout!!, null, ENABLE_SUBTITLES, USE_TEXTURE_VIEW)
    }

    fun playMediaByIndex(index: Int)
    {
        try
        {
            val playlist = viewModel.playlist.value

            if (playlist == null || playlist.isEmpty()) return
            val mediaItem = viewModel.playlist.value!![index]
            val media = mediaItem.getVlcMedia(mLibVLC)
            presentImageJob?.cancel()

            mainPlayer?.eventListener = {}

            when (mediaItem.getMediaType())
            {
                MediaType.VIDEO ->
                {
                    Log.d("mediaplayed", "video")
                    audioPlayer.stop()
                    media.addOption(":fullscreen")
                    mainPlayer = visualPlayer
                    visualPlayer.play(media)

                    mainPlayer!!.type = "video"
                }
                MediaType.IMAGE ->
                {
                    Log.d("mediaplayed", "audio")
                    presentImage(mediaItem)
                    mainPlayer = audioPlayer

                    mainPlayer!!.type = "audio"
                }
                else ->
                {
                }
            }

            if (mainPlayer != null)
            {
                mainPlayer!!.eventListener = { event ->
                    when (event)
                    {
                        MediaPlayer.Event.EndReached ->
                        {
                            playNextMedia()
                            //remove callback
                            mainPlayer!!.eventListener = {}
                        }

                        MediaPlayer.Event.EncounteredError ->
                        {
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


        } catch (e: Exception)
        {
            Log.e("Main", e.message)
            e.printStackTrace()
        }
    }


    fun presentImage(mediaItem: MediaItem)
    {

        val imageList = mutableListOf(mediaItem)

        if (!viewModel.playlist.value.isNullOrEmpty())
            imageList.addAll(viewModel.playlist.value!!.filter { it.getMediaType() == MediaType.IMAGE })

        val delayDuration = 30000L//getDurationInSecond(duration) * 1000 / imageList.size

        if (audioList.isEmpty())
            audioList = viewModel.getAudioList(applicationContext)

        if (audioList.isNotEmpty())
        {
            val backgroundAudio = Media(
                    mLibVLC,
                    audioList.random()
            )
            audioPlayer.play(backgroundAudio)
        }


        presentImageJob = lifecycleScope.launch {
            var i = 0
            while (i < imageList.size && isActive)
            {
                val media = imageList[i++]
                visualPlayer.play(media.getVlcMedia(mLibVLC))
                delay(delayDuration)

                if (i >= imageList.size)
                    i = 0
            }
        }
    }


    private fun playNextMedia()
    {
        mVideoLayout!!.post {
            val playlist = viewModel.playlist.value!!
            viewModel.playlistIndex++
            if (viewModel.playlistIndex >= playlist.size)
                viewModel.playlistIndex = 0

            playMediaByIndex(viewModel.playlistIndex)
        }
    }

    fun checkScheduledMedia()
    {
        checkScheduledMediaJob?.cancel()
        checkScheduledMediaJob = lifecycleScope.launch(Dispatchers.Default) {

            val playlist = viewModel.playlist.value ?: listOf()
            val scheduledItems: MutableList<MediaItem> = mutableListOf()
            scheduledItems.addAll(playlist.filter { it.fixTime.isNotEmpty() && it.fixTime != "00:00:00" })

            while (true)
            {
                if (scheduledItems.isEmpty())
                {
                    scheduledItems.addAll(playlist.filter { it.fixTime.isNotEmpty() && it.fixTime != "00:00:00" })
                }


                var index = -1

                scheduledItems.forEachIndexed { i, mediaItem ->
                    try
                    {
                        val time = mediaItem.fixTime.split(":").map { it.toInt() }

                        val now = Calendar.getInstance()

                        val scheduledTime = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, time[0])
                            set(Calendar.MINUTE, time[1])
                            set(Calendar.SECOND, time[2])
                        }

                        val mediaDuration = getDurationInSecond(mediaItem.duration ?: "00:00:00")

                        if (scheduledTime.timeInMillis <= now.timeInMillis
                                && now.timeInMillis <= scheduledTime.timeInMillis + mediaDuration * 1000
                        )
                        {
                            Log.d(
                                    "hengio",
                                    "${now.timeInMillis} - ${scheduledTime.timeInMillis} - ${scheduledTime.timeInMillis + mediaDuration * 1000}"
                            )
                            index = i
                        }

                    } catch (e: NumberFormatException)
                    {
                        Log.e("checkScheduledMedia", "error parsing media fixtime")
                    } catch (e: Exception)
                    {
                        Log.e("checkScheduledMedia", "error checking media fixtime")
                    }
                }

                if (index != -1 && index < scheduledItems.size)
                {
                    withContext(Dispatchers.Main)
                    {
                        val indexToPlay = playlist.indexOf(scheduledItems[index])

                        playMediaByIndex(indexToPlay)
                        Log.d("Scheduled", "play scheduled $indexToPlay")
                        viewModel.playlistIndex = indexToPlay
                        scheduledItems.removeAt(index)
                    }
                }

                delay(1000)
            }
        }
    }

    fun onActivityStop()
    {
        mainPlayer?.stop()
        audioPlayer.stop()
        visualPlayer.stop()
        visualPlayer.detachViews()
    }

    fun onActivityDestroy()
    {
        checkScheduledMediaJob?.cancel()
        presentImageJob?.cancel()

        mainPlayer?.release()
        if (!audioPlayer.isReleased)
            audioPlayer.release()

        if (!visualPlayer.isReleased)
            visualPlayer.release()

        mLibVLC.release()
    }
}