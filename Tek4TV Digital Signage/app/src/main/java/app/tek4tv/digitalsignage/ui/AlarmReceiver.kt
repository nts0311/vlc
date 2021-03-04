package app.tek4tv.digitalsignage.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.tek4tv.digitalsignage.media.PlayerManagerHolder


class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val playerManager = PlayerManagerHolder.getPlayerManager() ?: return


    }

    companion object {
        const val SCHEDULE_TYPE = "schedule_type"
        const val ACTION_PLAY_MEDIA_LIST = "xaction_play_media_list"
        const val ACTION_PLAY_MEDIA = "xaction_play_media"
        const val ACTION_END_PLAYLIST = "xaction_end_playlist"
        const val SCHEDULED_LIST_KEY = "scheduled_list_key"
        const val SCHEDULED_MEDIA_URL = "scheduled_media_uri"
    }
}