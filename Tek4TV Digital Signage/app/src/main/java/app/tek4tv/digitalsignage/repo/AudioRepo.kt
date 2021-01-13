package app.tek4tv.digitalsignage.repo

import android.content.Context
import app.tek4tv.digitalsignage.network.PlaylistService
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioRepo @Inject constructor(
    private val playlistService: PlaylistService,
    private val moshi: Moshi,
    @ApplicationContext private val appContext: Context
) {
}