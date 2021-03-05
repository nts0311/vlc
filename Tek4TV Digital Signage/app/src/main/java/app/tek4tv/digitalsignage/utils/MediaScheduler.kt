package app.tek4tv.digitalsignage.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import app.tek4tv.digitalsignage.repo.MediaRepo
import app.tek4tv.digitalsignage.ui.AlarmReceiver
import java.util.*

class MediaScheduler(private val appContext: Context, private val mediaRepo: MediaRepo) {

    private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun scheduleListAlarms() {
        val scheduledList = mediaRepo.scheduledList

        var maxEndKey = ""
        var maxEnd = 0L

        var isNewDayStarted = false

        for (i in scheduledList.keys.indices) {
            val period = scheduledList.keys.elementAt(i)

            val requestCode = mediaRepo.timeDividers[period]!![0].id

            val t = period.split("-")
            val start = toCalendar(t[0])
            val end = toCalendar(t[1])

            if (start.get(Calendar.HOUR_OF_DAY) > end.get(Calendar.HOUR_OF_DAY)
            ) isNewDayStarted = true

            if (isNewDayStarted) {
                if (start.get(Calendar.HOUR_OF_DAY) > end.get(Calendar.HOUR_OF_DAY)) end.add(
                        Calendar.DAY_OF_MONTH, 1)
                else {
                    start.add(Calendar.DAY_OF_MONTH, 1)
                    end.add(Calendar.DAY_OF_MONTH, 1)
                }
            }

            val now = Calendar.getInstance().timeInMillis

            if (now > end.timeInMillis) continue

            if (end.timeInMillis > maxEnd) {
                maxEnd = end.timeInMillis
                maxEndKey = period
            }

            setAlarmForList(period, requestCode, start)
        }

        //setting an alarm when all playlist is played, play nothing
        if (maxEndKey != "") {
            val requestCodeEnd = mediaRepo.timeDividers[maxEndKey]!![1].id
            setAlarmForList(maxEndKey, requestCodeEnd,
                    Calendar.getInstance().apply { timeInMillis = maxEnd }, true)
        }
    }

    private fun setAlarmForList(
        listKey: String, requestCode: Int, time: Calendar, shouldEndPlaying: Boolean = false
    ) {
        val isAlarmWorking = getScheduleListIntent(listKey, requestCode,
                PendingIntent.FLAG_NO_CREATE, shouldEndPlaying)

        if (isAlarmWorking != null) return

        Log.d("alarmx", "Set $listKey - $requestCode - $time")
        val alarmIntent = getScheduleListIntent(listKey, requestCode, 0, shouldEndPlaying)
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time.timeInMillis,
                alarmIntent)
    }

    private fun cancelAllListAlarm() {
        val scheduledList = mediaRepo.scheduledList
        var maxEndKey = ""
        var maxEnd = 0L

        for (i in scheduledList.keys.indices) {
            val period = scheduledList.keys.elementAt(i)

            val requestCode = mediaRepo.timeDividers[period]!![0].id

            val t = period.split("-")
            val end = toCalendar(t[1])

            if (end.timeInMillis > maxEnd) {
                maxEndKey = period
            }

            cancelListAlarm(period, requestCode)
        }

        if (maxEndKey != "") {
            val requestCodeEnd = mediaRepo.timeDividers[maxEndKey]!![1].id
            cancelListAlarm(maxEndKey, requestCodeEnd, true)
        }
    }

    private fun cancelListAlarm(
        listKey: String, requestCode: Int, shouldEndPlaying: Boolean = false
    ) {
        val isAlarmWorking = getScheduleListIntent(listKey, requestCode,
                PendingIntent.FLAG_NO_CREATE, shouldEndPlaying) != null

        if (!isAlarmWorking) return

        Log.d("alarmx", "Cancel $listKey - $requestCode")
        val alarmIntent = getScheduleListIntent(listKey, requestCode, 0, shouldEndPlaying)
        alarmManager.cancel(alarmIntent)
        alarmIntent?.cancel()
    }

    private fun getScheduleListIntent(
        listKey: String, requestCode: Int, flag: Int, shouldEndPlaying: Boolean = false
    ): PendingIntent? {

        val alarmAction = if (shouldEndPlaying) AlarmReceiver.ACTION_END_PLAYLIST
        else AlarmReceiver.ACTION_PLAY_MEDIA_LIST

        val intent = Intent(appContext, AlarmReceiver::class.java).apply {
            action = alarmAction
            putExtra(AlarmReceiver.SCHEDULED_LIST_KEY, listKey)
        }

        return PendingIntent.getBroadcast(appContext, requestCode, intent, flag)
    }

    private fun getScheduleMediaIntent(
        mediaIndex: Int, requestCode: Int, flag: Int
    ): PendingIntent? {
        val intent = Intent(appContext, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_PLAY_MEDIA
            putExtra(AlarmReceiver.SCHEDULED_MEDIA_INDEX, mediaIndex)
        }

        return PendingIntent.getBroadcast(appContext, requestCode, intent, flag)
    }

    private fun scheduleMediaAlarms() {
        mediaRepo.scheduledMediaItems.forEachIndexed { index, mediaItem ->
            val now = Calendar.getInstance()
            val scheduleTime = toCalendar(mediaItem.fixTime)

            if (now > scheduleTime) return@forEachIndexed

            val isAlarmWorking = getScheduleMediaIntent(index, mediaItem.id,
                    PendingIntent.FLAG_NO_CREATE) != null

            if (!isAlarmWorking) {
                Log.d("alarmx", "set media ${mediaItem.path}")

                val alarmIntent = getScheduleMediaIntent(index, mediaItem.id, 0)
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                        scheduleTime.timeInMillis, alarmIntent)
            }
        }
    }

    private fun cancelMediaAlarms() {
        mediaRepo.scheduledMediaItems.forEachIndexed { index, mediaItem ->
            val now = Calendar.getInstance()
            val scheduleTime = toCalendar(mediaItem.fixTime)

            if (now > scheduleTime) return@forEachIndexed

            val isAlarmWorking = getScheduleMediaIntent(index, mediaItem.id,
                    PendingIntent.FLAG_NO_CREATE) != null

            if (isAlarmWorking) {
                Log.d("alarmx", "cancel media ${mediaItem.path}")

                val alarmIntent = getScheduleMediaIntent(index, mediaItem.id, 0)

                alarmManager.cancel(alarmIntent)
                alarmIntent?.cancel()
            }
        }
    }

    fun scheduleAllAlarms() {
        scheduleListAlarms()
        scheduleMediaAlarms()
    }

    fun cancelAllAlarm() {
        cancelAllListAlarm()
        cancelMediaAlarms()
    }
}