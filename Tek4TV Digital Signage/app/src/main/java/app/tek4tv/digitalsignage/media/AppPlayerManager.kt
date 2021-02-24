package app.tek4tv.digitalsignage.media

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import app.tek4tv.digitalsignage.Timer
import app.tek4tv.digitalsignage.model.MediaItem
import app.tek4tv.digitalsignage.repo.MediaRepo
import app.tek4tv.digitalsignage.viewmodels.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

abstract class AppPlayerManager(
    private val applicationContext: Context,
    private var lifecycleScope: CoroutineScope,
    private var viewModel: MainViewModel,
    private var rotationMode: Int,
    protected val parentView: ViewGroup
) {
    private var checkScheduledMediaJobId: Long = -1L

    private var timer: Timer = Timer(lifecycleScope)
    private var checkScheduledListTimer: Timer = Timer(lifecycleScope)
    private var checkScheduledMediaListId = -1L


    private val playlistRepo: MediaRepo
        get() = viewModel.mediaRepo

    var currentPlaylist = listOf<MediaItem>()

    //-1 for random unscheduled media
    //0 for init
    //HH:mm:ss-HH:mm:ss for scheduled media
    private var currentPlaylistKey = "0"

    var audioList = mutableListOf<Uri>()

    init {
        timer.start()
        checkScheduledListTimer.delay = 3000
        checkScheduledListTimer.start()
        audioList = viewModel.audioRepo.audioFileUri
    }

    abstract fun attachLayout()
    abstract fun playVideo()
    abstract fun playMutedVideo()
    abstract fun playMedia(mediaItem: MediaItem)
    abstract fun playMediaByIndex(index: Int)

    private fun checkScheduledMediaList() {
        //cancelPlaying()

        lifecycleScope.launch {
            loopCheckList(Calendar.getInstance().timeInMillis)
        }

        checkScheduledListTimer.removeTimeListener(checkScheduledMediaListId)

        checkScheduledMediaListId =
            checkScheduledListTimer.addTimeListener(Dispatchers.Default) { now ->
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

    fun checkScheduledMedia() {
        timer.removeTimeListener(checkScheduledMediaJobId)

        val playlist = playlistRepo.broadcastList.filter {
            it.path != "start" && it.path != "end"
        }
        val scheduledItems: MutableList<MediaItem> = mutableListOf()
        scheduledItems.addAll(
            playlist.filter { it.fixTime.isNotEmpty() && it.fixTime != "00:00:00" })

        checkScheduledMediaJobId = timer.addTimeListener(Dispatchers.Default) {
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
        }
    }
}