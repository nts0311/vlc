package app.tek4tv.digitalsignage.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

class Playlist(private val activityScope: CoroutineScope) {
    var mediaItems: List<MediaItem> = listOf()

    var checkScheduledMediaJob: Job? = null
    var currentPlayingMedia: MediaItem? = null
    var playMediaByIndex: (Int) -> Unit = {}


}