package app.tek4tv.digitalsignage.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import app.tek4tv.digitalsignage.media.PlayerManager


class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val playerManager = PlayerManager.instance ?: return


        when (intent.action) {
            ACTION_PLAY_MEDIA_LIST -> {
                val listKey = intent.getStringExtra(SCHEDULED_LIST_KEY)
                Toast.makeText(context, listKey, Toast.LENGTH_LONG).show()
                Log.d("alarmx", listKey)

                playerManager.setPlaylist(listKey)
            }

            ACTION_END_PLAYLIST -> {
                val listKey = intent.getStringExtra(SCHEDULED_LIST_KEY)
                Toast.makeText(context, listKey, Toast.LENGTH_LONG).show()
                Log.d("alarmx", listKey)


            }

            ACTION_PLAY_MEDIA -> {
                val mediaIndex = intent.getIntExtra(SCHEDULED_MEDIA_INDEX, 0)
                Toast.makeText(context, mediaIndex.toString(), Toast.LENGTH_LONG).show()
                Log.d("alarmx", mediaIndex.toString())

                playerManager.playScheduledMediaByIndex(mediaIndex)
            }
        }
    }

    companion object {
        const val ACTION_PLAY_MEDIA_LIST = "xaction_play_media_list"
        const val ACTION_PLAY_MEDIA = "xaction_play_media"
        const val ACTION_END_PLAYLIST = "xaction_end_playlist"
        const val SCHEDULED_LIST_KEY = "scheduled_list_key"
        const val SCHEDULED_MEDIA_INDEX = "scheduled_media_uri"
    }
}