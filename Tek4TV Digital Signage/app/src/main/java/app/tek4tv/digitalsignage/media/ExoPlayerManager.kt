package app.tek4tv.digitalsignage.media

import android.content.Context
import android.view.ViewGroup
import app.tek4tv.digitalsignage.model.MediaItem
import app.tek4tv.digitalsignage.viewmodels.MainViewModel
import kotlinx.coroutines.CoroutineScope

class ExoPlayerManager(
    applicationContext: Context,
    lifecycleScope: CoroutineScope,
    viewModel: MainViewModel,
    rotationMode: Int,
    parentView: ViewGroup,
) : AppPlayerManager(applicationContext, lifecycleScope, viewModel, rotationMode, parentView) {
    override fun attachLayout() {

    }

    override fun playVideo() {

    }

    override fun playMutedVideo() {

    }

    override fun playMedia(mediaItem: MediaItem) {

    }

    override fun playMediaByIndex(index: Int) {

    }

}